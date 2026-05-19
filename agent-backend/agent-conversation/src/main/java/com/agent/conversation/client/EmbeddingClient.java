package com.agent.conversation.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class EmbeddingClient {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Value("${ai.engine.url:http://localhost:8000}")
    private String engineUrl;

    @SuppressWarnings("unchecked")
    public List<Double> getEmbedding(String text) {
        try {
            URI uri = URI.create(engineUrl + "/rag/embed-single");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(30000);

            String body = mapper.writeValueAsString(Map.of("text", text));
            try (var os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() != 200) {
                log.warn("embed-single returned status {}", conn.getResponseCode());
                return null;
            }

            var response = mapper.readValue(conn.getInputStream(), Map.class);
            return (List<Double>) response.get("embedding");
        } catch (IOException e) {
            log.warn("Failed to get embedding: {}", e.getMessage());
            return null;
        }
    }
}
