package com.agent.conversation.service;

import com.agent.conversation.mapper.MessageMapper;
import com.agent.conversation.service.impl.MessageInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.agent.conversation.config.MemoryConfig;

@Service
public class MessageCacheService {
    private static final Logger log = LoggerFactory.getLogger(MessageCacheService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final RedisTemplate<String, Object> redisTemplate;
    private final MessageMapper messageMapper;
    private final com.agent.conversation.mapper.ConversationMapper conversationMapper;

    public MessageCacheService(RedisTemplate<String, Object> redisTemplate,
                               MessageMapper messageMapper,
                               com.agent.conversation.mapper.ConversationMapper conversationMapper) {
        this.redisTemplate = redisTemplate;
        this.messageMapper = messageMapper;
        this.conversationMapper = conversationMapper;
    }

    public void addMessage(Long convId, String role, String content) {
        String key = MemoryConfig.historyKey(convId);
        try {
            MessageInfo msg = new MessageInfo(role, content);
            String json = mapper.writeValueAsString(msg);
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.opsForList().trim(key, -MemoryConfig.MAX_MESSAGES, -1);
            redisTemplate.expire(key, Duration.ofSeconds(MemoryConfig.REDIS_TTL_SECONDS));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message for conv {}", convId, e);
        }
    }

    public List<MessageInfo> getHistory(Long convId) {
        String key = MemoryConfig.historyKey(convId);
        List<Object> rawList = redisTemplate.opsForList().range(key, 0, -1);
        if (rawList != null && !rawList.isEmpty()) {
            List<MessageInfo> result = new ArrayList<>();
            for (Object obj : rawList) {
                try {
                    String json = (String) obj;
                    result.add(mapper.readValue(json, MessageInfo.class));
                } catch (Exception e) {
                    log.warn("Failed to deserialize message: {}", obj, e);
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        // Cache miss: load from PostgreSQL
        log.info("Redis miss for conv {}, loading from DB", convId);
        List<com.agent.conversation.entity.Message> dbMessages =
            messageMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.agent.conversation.entity.Message>()
                    .eq(com.agent.conversation.entity.Message::getConversationId, convId)
                    .orderByAsc(com.agent.conversation.entity.Message::getCreatedAt)
                    .last("LIMIT " + MemoryConfig.MAX_MESSAGES));
        if (dbMessages.isEmpty()) {
            return Collections.emptyList();
        }
        List<MessageInfo> result = new ArrayList<>();
        for (var msg : dbMessages) {
            result.add(new MessageInfo(msg.getRole(), msg.getContent()));
            try {
                redisTemplate.opsForList().rightPush(key,
                    mapper.writeValueAsString(new MessageInfo(msg.getRole(), msg.getContent())));
            } catch (JsonProcessingException e) {
                log.warn("Failed to cache message", e);
            }
        }
        redisTemplate.expire(key, Duration.ofSeconds(MemoryConfig.REDIS_TTL_SECONDS));
        return result;
    }

    public void addKeywords(Long convId, List<String> keywords) {
        String key = MemoryConfig.keywordsKey(convId);
        for (String kw : keywords) {
            redisTemplate.opsForSet().add(key, kw);
        }
        redisTemplate.expire(key, Duration.ofSeconds(MemoryConfig.REDIS_TTL_SECONDS));
    }

    public List<String> getKeywords(Long convId) {
        String key = MemoryConfig.keywordsKey(convId);
        var members = redisTemplate.opsForSet().members(key);
        if (members == null || members.isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (Object m : members) {
            result.add(m.toString());
        }
        return result;
    }

    public void setSummary(Long convId, String summary) {
        String key = MemoryConfig.summaryKey(convId);
        redisTemplate.opsForValue().set(key, summary, Duration.ofSeconds(MemoryConfig.REDIS_TTL_SECONDS));
    }

    public String getSummary(Long convId) {
        String key = MemoryConfig.summaryKey(convId);
        Object val = redisTemplate.opsForValue().get(key);
        if (val != null) return val.toString();

        // Redis miss: fallback to PostgreSQL
        var conv = conversationMapper.selectById(convId);
        if (conv != null && conv.getSummary() != null) {
            redisTemplate.opsForValue().set(key, conv.getSummary(),
                Duration.ofSeconds(MemoryConfig.REDIS_TTL_SECONDS));
            return conv.getSummary();
        }
        return null;
    }

    public void setTopicEmbedding(Long convId, String embeddingJson) {
        String key = MemoryConfig.topicEmbeddingKey(convId);
        redisTemplate.opsForValue().set(key, embeddingJson, Duration.ofSeconds(MemoryConfig.REDIS_TTL_SECONDS));
    }

    public String getTopicEmbedding(Long convId) {
        String key = MemoryConfig.topicEmbeddingKey(convId);
        Object val = redisTemplate.opsForValue().get(key);
        return val != null ? val.toString() : null;
    }

    public void setTokenCount(Long convId, int count) {
        String key = MemoryConfig.tokenCountKey(convId);
        redisTemplate.opsForValue().set(key, String.valueOf(count), Duration.ofSeconds(MemoryConfig.REDIS_TTL_SECONDS));
    }

    public int getTokenCount(Long convId) {
        String key = MemoryConfig.tokenCountKey(convId);
        Object val = redisTemplate.opsForValue().get(key);
        return val != null ? Integer.parseInt(val.toString()) : 0;
    }

    public void refreshTtl(Long convId) {
        Duration ttl = Duration.ofSeconds(MemoryConfig.REDIS_TTL_SECONDS);
        redisTemplate.expire(MemoryConfig.historyKey(convId), ttl);
        redisTemplate.expire(MemoryConfig.keywordsKey(convId), ttl);
        redisTemplate.expire(MemoryConfig.summaryKey(convId), ttl);
        redisTemplate.expire(MemoryConfig.topicEmbeddingKey(convId), ttl);
        redisTemplate.expire(MemoryConfig.tokenCountKey(convId), ttl);
    }
}
