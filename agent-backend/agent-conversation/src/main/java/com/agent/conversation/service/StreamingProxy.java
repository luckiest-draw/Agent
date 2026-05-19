package com.agent.conversation.service;

import com.agent.conversation.memory.MemoryPipeline.MemoryContext;
import com.agent.conversation.service.impl.MessageInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class StreamingProxy {
    private static final Logger log = LoggerFactory.getLogger(StreamingProxy.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Value("${ai.engine.url:http://localhost:8000}")
    private String engineUrl;

    @FunctionalInterface
    public interface ResponseCallback {
        void onComplete(String fullResponse);
        default void onError(Throwable e) {
            log.error("Callback error", e);
        }
    }

    /** 带上下文 + 回调（Controller 使用，可收集完整回复） */
    public void streamChat(String userMessage, MemoryContext context,
                           Long conversationId, String modelName,
                           SseEmitter emitter, ResponseCallback callback) {
        new Thread(() -> {
            StringBuilder fullResponse = new StringBuilder();
            try {
                URI uri = URI.create(engineUrl + "/chat/stream");
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(120000);

                List<Map<String, String>> history = new ArrayList<>();
                if (context.summary() != null && !context.summary().isEmpty()) {
                    history.add(Map.of("role", "system", "content", context.summary()));
                }
                for (MessageInfo msg : context.messages()) {
                    history.add(Map.of("role", msg.role(), "content", msg.content()));
                }

                Map<String, Object> body = new HashMap<>();
                body.put("query", userMessage);
                body.put("history", history);
                body.put("model", modelName != null ? modelName : "deepseek-chat");

                String json = mapper.writeValueAsString(body);
                log.info("StreamingProxy -> {} msgs={} tokens={}", uri, history.size(),
                    context.estimatedTokens());

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                int status = conn.getResponseCode();
                if (status != 200) {
                    log.error("Python engine returned status {}", status);
                    emitter.completeWithError(new RuntimeException("Engine error: HTTP " + status));
                    return;
                }

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                boolean doneReceiving = false;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        try {
                            var event = mapper.readValue(data, Map.class);
                            if (event.containsKey("content")) {
                                String content = (String) event.get("content");
                                fullResponse.append(content);
                            }
                            if (Boolean.TRUE.equals(event.get("done"))) {
                                doneReceiving = true;
                            }
                        } catch (Exception ignored) {}
                        emitter.send(SseEmitter.event().data(data));
                    }
                }
                reader.close();
                emitter.complete();
                if (callback != null) {
                    callback.onComplete(fullResponse.toString());
                }
                log.info("StreamingProxy completed for conv {}, response {} chars",
                    conversationId, fullResponse.length());
            } catch (Exception e) {
                log.error("StreamingProxy error for conv {}: {}", conversationId, e.getMessage());
                emitter.completeWithError(e);
                if (callback != null) callback.onError(e);
            }
        }).start();
    }

    /** 无回调版本（纯转发，用于无会话ID的流式） */
    public void streamChat(String userMessage, MemoryContext context,
                           Long conversationId, String modelName, SseEmitter emitter) {
        streamChat(userMessage, context, conversationId, modelName, emitter, null);
    }

    /** 兼容旧签名 */
    public void streamChat(String userMessage, List<Map<String, String>> history,
                           Long conversationId, String modelName, SseEmitter emitter) {
        streamChat(userMessage,
            new MemoryContext(null,
                history != null ? history.stream()
                    .map(m -> new MessageInfo(
                        m.getOrDefault("role", "user"),
                        m.getOrDefault("content", "")))
                    .toList()
                : List.of(),
                0),
            conversationId, modelName, emitter, null);
    }
}
