package com.agent.conversation.service;

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
import java.util.List;
import java.util.Map;

@Service
public class StreamingProxy {
    private static final Logger log = LoggerFactory.getLogger(StreamingProxy.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Value("${ai.engine.url:http://localhost:8000}")
    private String engineUrl;

    public void streamChat(String userMessage, List<Map<String, String>> history,
                           Long conversationId, String modelName, SseEmitter emitter) {
        new Thread(() -> {
            try {
                URI uri = URI.create(engineUrl + "/chat/stream");
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(120000);

                Map<String, Object> body = new java.util.HashMap<>();
                body.put("query", userMessage);
                body.put("history", history != null ? history : List.of());
                body.put("model", modelName != null ? modelName : "deepseek-chat");

                String json = mapper.writeValueAsString(body);
                log.info("StreamingProxy -> {} body: {}", uri, json);

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
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        log.debug("SSE data: {}", data);
                        emitter.send(SseEmitter.event().data(data));
                    }
                }
                reader.close();
                emitter.complete();
                log.info("StreamingProxy completed for conversation {}", conversationId);
            } catch (Exception e) {
                log.error("StreamingProxy error for conversation {}: {}", conversationId, e.getMessage());
                emitter.completeWithError(e);
            }
        }).start();
    }
}
