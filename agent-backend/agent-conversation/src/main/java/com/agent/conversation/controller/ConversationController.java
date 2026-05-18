package com.agent.conversation.controller;

import com.agent.common.Result;
import com.agent.conversation.entity.Conversation;
import com.agent.conversation.entity.Message;
import com.agent.conversation.mapper.ConversationMapper;
import com.agent.conversation.mapper.MessageMapper;
import com.agent.conversation.service.ChatService;
import com.agent.conversation.service.StreamingProxy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
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

    @PostMapping("/{id}/send")
    public Result<Message> send(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String userMessage = body.get("message");
        Message msg = chatService.saveUserMessage(id, userMessage);
        return Result.ok(msg);
    }

    // 无会话ID的流式对话（前端Chat页面使用）
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody Map<String, Object> body) {
        SseEmitter emitter = new SseEmitter(300000L);
        String query = (String) body.get("query");
        String model = (String) body.getOrDefault("model", "deepseek-chat");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) body.getOrDefault("history", List.of());
        // 创建临时会话保存消息
        Conversation conv = new Conversation();
        conv.setTitle(query != null && query.length() > 30 ? query.substring(0, 30) + "..." : query);
        conv.setMessageCount(0);
        conversationMapper.insert(conv);
        chatService.saveUserMessage(conv.getId(), query);
        streamingProxy.streamChat(query, history, conv.getId(), model, emitter);
        return emitter;
    }

    @PostMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long id, @RequestBody Map<String, String> body) {
        SseEmitter emitter = new SseEmitter(300000L);
        String userMessage = body.get("message");
        String modelName = body.getOrDefault("modelName", null);
        chatService.saveUserMessage(id, userMessage);
        streamingProxy.streamChat(userMessage, List.of(), id, modelName, emitter);
        return emitter;
    }
}
