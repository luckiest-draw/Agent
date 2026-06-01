package com.agent.conversation.memory;

import com.agent.conversation.service.MessageCacheService;
import com.agent.conversation.service.impl.MessageInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class CompressionService {
    private static final Logger log = LoggerFactory.getLogger(CompressionService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Value("${ai.engine.url:http://localhost:8000}")
    private String engineUrl;

    private final MessageCacheService cacheService;
    private final com.agent.conversation.mapper.ConversationMapper conversationMapper;

    public CompressionService(MessageCacheService cacheService,
                              com.agent.conversation.mapper.ConversationMapper conversationMapper) {
        this.cacheService = cacheService;
        this.conversationMapper = conversationMapper;
    }

    /**
     * 压缩旧消息为摘要。保留最近 recentCount 轮不变，更早的合并进摘要。
     * @return { summary, recentMessages }
     */
    public CompressionResult buildCompressedContext(List<MessageInfo> allMessages, int recentRounds,
                                                     Long conversationId) {
        int recentCount = recentRounds * 2;
        if (allMessages.size() <= recentCount) {
            return new CompressionResult(null, allMessages);
        }

        List<MessageInfo> older = allMessages.subList(0, allMessages.size() - recentCount);
        List<MessageInfo> recent = allMessages.subList(allMessages.size() - recentCount, allMessages.size());

        String existingSummary = cacheService.getSummary(conversationId);
        String newSummary = compress(older, existingSummary);

        cacheService.setSummary(conversationId, newSummary);
        // 持久化到 PostgreSQL，Redis TTL 过期后可从 DB 恢复
        var conv = conversationMapper.selectById(conversationId);
        if (conv != null) {
            conv.setSummary(newSummary);
            conversationMapper.updateById(conv);
        }
        log.info("Compressed {} older messages into summary ({} chars) for conv {}",
            older.size(), newSummary.length(), conversationId);

        return new CompressionResult(newSummary, recent);
    }

    public String compress(List<MessageInfo> olderMessages, String existingSummary) {
        try {
            URI uri = URI.create(engineUrl + "/memory/summarize");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(60000);

            List<Map<String, String>> msgs = new ArrayList<>();
            for (MessageInfo m : olderMessages) {
                msgs.add(Map.of("role", m.role(), "content", m.content()));
            }

            Map<String, Object> body = new HashMap<>();
            body.put("messages", msgs);

            String json = mapper.writeValueAsString(body);
            try (var os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() != 200) {
                log.warn("Summarize API returned {}", conn.getResponseCode());
                return fallbackSummary(olderMessages, existingSummary);
            }

            var response = mapper.readValue(conn.getInputStream(), Map.class);
            String summary = (String) response.get("summary");
            if (existingSummary != null && !existingSummary.isEmpty()) {
                summary = existingSummary + "\n" + summary;
            }
            return summary;
        } catch (IOException e) {
            log.error("Summarize API failed: {}", e.getMessage());
            return fallbackSummary(olderMessages, existingSummary);
        }
    }

    private String fallbackSummary(List<MessageInfo> messages, String existingSummary) {
        StringBuilder sb = new StringBuilder();
        if (existingSummary != null) sb.append(existingSummary).append(" ");
        for (int i = 0; i < Math.min(3, messages.size()); i++) {
            sb.append(messages.get(i).content()).append(" ");
        }
        return sb.toString().trim();
    }

    public record CompressionResult(String summary, List<MessageInfo> recentMessages) {}
}
