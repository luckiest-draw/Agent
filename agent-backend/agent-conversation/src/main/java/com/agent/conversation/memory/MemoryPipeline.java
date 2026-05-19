package com.agent.conversation.memory;

import com.agent.conversation.config.MemoryConfig;
import com.agent.conversation.service.MessageCacheService;
import com.agent.conversation.service.impl.MessageInfo;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class MemoryPipeline {

    private final MessageCacheService cacheService;
    private final PiiMasker piiMasker;
    private final TokenCounter tokenCounter;
    private final CompressionService compressionService;

    public MemoryPipeline(MessageCacheService cacheService, PiiMasker piiMasker,
                          TokenCounter tokenCounter, CompressionService compressionService) {
        this.cacheService = cacheService;
        this.piiMasker = piiMasker;
        this.tokenCounter = tokenCounter;
        this.compressionService = compressionService;
    }

    /** 构建发送给 LLM 的完整上下文 */
    public MemoryContext buildContext(Long conversationId, String maskedUserMessage) {
        List<MessageInfo> history = cacheService.getHistory(conversationId);

        // 滑动窗口：已在 MessageCacheService 写入时用 LTRIM 控制，此处再用 maxMessages 兜底
        if (history.size() > MemoryConfig.MAX_MESSAGES) {
            history = history.subList(history.size() - MemoryConfig.MAX_MESSAGES, history.size());
        }

        int totalTokens = tokenCounter.estimateTokens(history)
            + tokenCounter.estimateTokens(maskedUserMessage);

        String summary = cacheService.getSummary(conversationId);
        if (summary != null && !summary.isEmpty()) {
            totalTokens += tokenCounter.estimateTokens(summary);
        }

        List<MessageInfo> contextMessages;
        if (totalTokens > MemoryConfig.TOKEN_BUDGET) {
            int recentRounds = Math.max(5, MemoryConfig.MAX_ROUNDS / 4);  // 保留最近 5 轮
            var result = compressionService.buildCompressedContext(history, recentRounds, conversationId);
            summary = result.summary();
            contextMessages = result.recentMessages();
        } else {
            contextMessages = history;
        }

        return new MemoryContext(summary, contextMessages,
            tokenCounter.estimateTokens(contextMessages));
    }

    /** 收到 LLM 响应后：双写 + 更新统计 */
    public void afterResponse(Long conversationId, String maskedUserMessage,
                               String maskedAssistantMessage) {
        cacheService.addMessage(conversationId, "user", maskedUserMessage);
        cacheService.addMessage(conversationId, "assistant", maskedAssistantMessage);

        List<MessageInfo> all = cacheService.getHistory(conversationId);
        int tokens = tokenCounter.estimateTokens(all);
        cacheService.setTokenCount(conversationId, tokens);
        cacheService.refreshTtl(conversationId);
    }

    /** 仅保存用户消息（不等待 AI 响应时使用） */
    public void saveUserMessage(Long conversationId, String maskedMessage) {
        cacheService.addMessage(conversationId, "user", maskedMessage);
        cacheService.refreshTtl(conversationId);
    }

    public record MemoryContext(String summary, List<MessageInfo> messages, int estimatedTokens) {}
}
