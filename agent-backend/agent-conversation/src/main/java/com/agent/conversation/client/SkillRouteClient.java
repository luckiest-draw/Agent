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
import java.util.List;
import java.util.Map;

@Component
public class SkillRouteClient {
    private static final Logger log = LoggerFactory.getLogger(SkillRouteClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Value("${ai.engine.url:http://localhost:8000}")
    private String engineUrl;

    @SuppressWarnings("unchecked")
    public RouteResult route(String query, List<Map<String, String>> skills) {
        try {
            URI uri = URI.create(engineUrl + "/skills/route");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(30000);

            Map<String, Object> body = Map.of(
                "query", query,
                "skills", skills,
                "threshold", 0.6
            );
            String json = mapper.writeValueAsString(body);
            try (var os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() != 200) {
                log.warn("Skill route returned {}", conn.getResponseCode());
                return new RouteResult(null, 0.0, false);
            }

            var resp = mapper.readValue(conn.getInputStream(), Map.class);
            Boolean matched = (Boolean) resp.getOrDefault("matched", false);
            String skillName = (String) resp.get("skill");
            Double score = ((Number) resp.getOrDefault("score", 0.0)).doubleValue();
            return new RouteResult(skillName, score, Boolean.TRUE.equals(matched));

        } catch (IOException e) {
            log.warn("Skill route failed: {}", e.getMessage());
            return new RouteResult(null, 0.0, false);
        }
    }

    public record RouteResult(String skillName, double score, boolean matched) {}
}
