package com.agent.conversation.controller;

import com.agent.common.Result;
import com.agent.conversation.entity.Conversation;
import com.agent.conversation.entity.Message;
import com.agent.conversation.mapper.ConversationMapper;
import com.agent.conversation.mapper.MessageMapper;
import com.agent.conversation.memory.MemoryPipeline;
import com.agent.conversation.memory.MemoryPipeline.MemoryContext;
import com.agent.conversation.memory.PiiMasker;
import com.agent.conversation.memory.TopicDetector;
import com.agent.conversation.memory.TopicDetector.TopicDetectionResult;
import com.agent.conversation.service.ChatService;
import com.agent.conversation.service.ImageUploadService;
import com.agent.conversation.service.StreamingProxy;
import org.springframework.web.multipart.MultipartFile;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private ChatService chatService;

    @Autowired
    private StreamingProxy streamingProxy;

    @Autowired
    private MemoryPipeline memoryPipeline;

    @Autowired
    private PiiMasker piiMasker;

    @Autowired
    private TopicDetector topicDetector;

    @Autowired
    private ImageUploadService imageUploadService;

    @Value("${memory.topic-detection.enabled:true}")
    private boolean topicDetectionEnabled;

    @PostMapping
    public Result<Conversation> create(@RequestBody Conversation conv) {
        conversationMapper.insert(conv);
        return Result.ok(conv);
    }

    @GetMapping
    public Result<List<Conversation>> list() {
        return Result.ok(conversationMapper.selectList(null));
    }

    @GetMapping("/{id}/messages")
    public Result<List<Message>> messages(@PathVariable Long id) {
        return Result.ok(messageMapper.selectList(
            new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, id)
                .orderByAsc(Message::getCreatedAt)));
    }

    @PostMapping("/upload-image")
    public Result<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file) {
        ImageUploadService.ImageUploadResult result = imageUploadService.upload(file);
        return Result.ok(Map.of(
            "imageUrl", result.imageUrl(),
            "fileName", result.fileName(),
            "size", result.size()
        ));
    }

    /** 无会话ID的流式对话（前端 Chat 页新建对话） */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody Map<String, Object> body) {
        SseEmitter emitter = new SseEmitter(300000L);
        String query = (String) body.get("query");
        String model = (String) body.getOrDefault("model", "deepseek-chat");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) body.getOrDefault("history", List.of());

        String imageUrl = (String) body.getOrDefault("imageUrl", null);

        String maskedQuery = piiMasker.mask(query);

        Conversation conv = new Conversation();
        conv.setTitle(maskedQuery.length() > 30 ? maskedQuery.substring(0, 30) + "..." : maskedQuery);
        conv.setMessageCount(0);
        conversationMapper.insert(conv);

        chatService.saveUserMessage(conv.getId(), maskedQuery, imageUrl);
        MemoryContext context = memoryPipeline.buildContext(conv.getId(), maskedQuery);

        streamingProxy.streamChat(maskedQuery, context, conv.getId(), model, imageUrl, emitter,
            (fullResponse) -> {
                String maskedResponse = piiMasker.mask(fullResponse);
                chatService.saveAssistantMessage(conv.getId(), maskedResponse);
                memoryPipeline.afterResponse(conv.getId(), maskedQuery, maskedResponse);
            });
        return emitter;
    }

    /** 已有会话的流式对话（带话题检测 + 滑动窗口） */
    @PostMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long id, @RequestBody Map<String, String> body) {
        SseEmitter emitter = new SseEmitter(300000L);
        String rawMessage = body.get("message");
        String modelName = body.getOrDefault("modelName", null);
        String imageUrl = body.getOrDefault("imageUrl", null);

        // 1. PII 脱敏
        String maskedMessage = piiMasker.mask(rawMessage);

        // 2. 话题检测（可配置开关）
        Long activeConvId = id;
        if (topicDetectionEnabled) {
            TopicDetectionResult topicResult = topicDetector.detect(maskedMessage, id);
            if (topicResult.changed()) {
                Conversation sub = new Conversation();
                sub.setTitle(maskedMessage.length() > 30
                    ? maskedMessage.substring(0, 30) + "..." : maskedMessage);
                sub.setParentId(id);
                conversationMapper.insert(sub);
                activeConvId = sub.getId();
            }
        }

        // 3. 保存用户消息
        chatService.saveUserMessage(activeConvId, maskedMessage, imageUrl);

        // 4. 构建上下文（Redis 滑动窗口 + Token 压缩）
        MemoryContext context = memoryPipeline.buildContext(activeConvId, maskedMessage);

        // 5. 发送流式请求，在回调中收集完整回复
        final Long finalConvId = activeConvId;
        streamingProxy.streamChat(maskedMessage, context, activeConvId, modelName, imageUrl, emitter,
            (fullResponse) -> {
                String maskedResponse = piiMasker.mask(fullResponse);
                chatService.saveAssistantMessage(finalConvId, maskedResponse);
                memoryPipeline.afterResponse(finalConvId, maskedMessage, maskedResponse);
            });
        return emitter;
    }
}
