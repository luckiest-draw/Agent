package com.agent.conversation.memory;

import com.agent.conversation.client.EmbeddingClient;
import com.agent.conversation.config.MemoryConfig;
import com.agent.conversation.service.MessageCacheService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class TopicDetector {
    private static final Logger log = LoggerFactory.getLogger(TopicDetector.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final EmbeddingClient embeddingClient;
    private final MessageCacheService cacheService;

    public TopicDetector(EmbeddingClient embeddingClient, MessageCacheService cacheService) {
        this.embeddingClient = embeddingClient;
        this.cacheService = cacheService;
    }

    public TopicDetectionResult detect(String currentMessage, Long conversationId) {
        // 第1层: 关键词规则粗筛
        if (keywordFilter(currentMessage, conversationId)) {
            log.debug("Topic detection: keyword filter passed, same topic");
            return new TopicDetectionResult(false, null);
        }

        // 第2层: 简易启发式
        if (heuristicFilter(currentMessage)) {
            log.debug("Topic detection: heuristic filter passed, skip embedding");
            return new TopicDetectionResult(false, null);
        }

        // 第3层: Embedding 精确判断
        log.info("Topic detection: calling embedding API for conv {}", conversationId);
        List<Double> embedding = embeddingClient.getEmbedding(currentMessage);
        if (embedding == null) {
            log.warn("Embedding API unavailable, assuming same topic");
            return new TopicDetectionResult(false, null);
        }

        List<Double> existingTopic = getStoredTopicEmbedding(conversationId);
        if (existingTopic == null) {
            storeTopicEmbedding(conversationId, embedding);
            return new TopicDetectionResult(false, embedding);
        }

        double similarity = cosineSimilarity(embedding, existingTopic);
        log.info("Topic similarity for conv {}: {:.3f}", conversationId, similarity);

        if (similarity < MemoryConfig.TOPIC_SIMILARITY_THRESHOLD) {
            storeTopicEmbedding(conversationId, embedding);
            return new TopicDetectionResult(true, embedding);
        }

        // 更新运行中话题向量
        List<Double> updated = updateRunningAverage(existingTopic, embedding);
        storeTopicEmbedding(conversationId, updated);
        return new TopicDetectionResult(false, updated);
    }

    /** 第1层：关键词交集判断 */
    private boolean keywordFilter(String message, Long convId) {
        List<String> currentKw = extractKeywords(message);
        if (currentKw.isEmpty()) return false;

        List<String> cachedKw = cacheService.getKeywords(convId);
        if (cachedKw.isEmpty()) {
            cacheService.addKeywords(convId, currentKw);
            return false;
        }

        // 交集占比 > 50% → 同话题
        long overlap = currentKw.stream().filter(cachedKw::contains).count();
        double ratio = (double) overlap / currentKw.size();
        if (ratio > 0.5) {
            return true;
        }
        cacheService.addKeywords(convId, currentKw);
        return false;
    }

    /** 第2层：短句/闲聊跳过 */
    private boolean heuristicFilter(String message) {
        String trimmed = message.trim();
        // 短句（< 10 字）→ 闲聊，跳过
        if (trimmed.length() < 10) {
            return true;
        }
        // 纯标点/表情等
        if (trimmed.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", "").length() < 5) {
            return true;
        }
        return false;
    }

    /** 提取关键词（简易：按标点/空格分词，取长度 >= 2 的词） */
    private List<String> extractKeywords(String text) {
        String cleaned = text.replaceAll("[\\p{Punct}\\s，。！？、；：\"\"''（）【】《》\\-+]+", " ")
                             .trim();
        String[] words = cleaned.split("\\s+");
        List<String> result = new ArrayList<>();
        for (String w : words) {
            if (w.length() >= 2) {
                result.add(w.toLowerCase());
            }
        }
        return result;
    }

    private List<Double> getStoredTopicEmbedding(Long convId) {
        String json = cacheService.getTopicEmbedding(convId);
        if (json == null) return null;
        try {
            return mapper.readValue(json, new TypeReference<List<Double>>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private void storeTopicEmbedding(Long convId, List<Double> embedding) {
        try {
            cacheService.setTopicEmbedding(convId, mapper.writeValueAsString(embedding));
        } catch (JsonProcessingException e) {
            log.warn("Failed to store topic embedding", e);
        }
    }

    /** 余弦相似度 */
    public double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a.size() != b.size()) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        if (normA == 0 && normB == 0) return 1.0;
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /** 运行中平均向量更新 */
    private List<Double> updateRunningAverage(List<Double> oldAvg, List<Double> newVec) {
        // 简单等差更新：(old * 0.7 + new * 0.3)，给予旧话题更多权重
        List<Double> result = new ArrayList<>(oldAvg.size());
        for (int i = 0; i < oldAvg.size(); i++) {
            result.add(oldAvg.get(i) * 0.7 + newVec.get(i) * 0.3);
        }
        return result;
    }

    public record TopicDetectionResult(boolean changed, List<Double> updatedEmbedding) {}
}
