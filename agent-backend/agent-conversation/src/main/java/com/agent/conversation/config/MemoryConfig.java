package com.agent.conversation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class MemoryConfig {

    public static final int MAX_ROUNDS = 20;
    public static final int MAX_MESSAGES = MAX_ROUNDS * 2;
    public static final int TOKEN_BUDGET = 4000;
    public static final double TOPIC_SIMILARITY_THRESHOLD = 0.6;
    public static final long REDIS_TTL_SECONDS = 86400;

    public static final String HISTORY_KEY = "chat:history:";
    public static final String SUMMARY_KEY = "chat:summary:";
    public static final String TOPIC_KEY = "chat:topic_embedding:";
    public static final String KEYWORDS_KEY = "chat:keywords:";
    public static final String TOKEN_COUNT_KEY = "chat:token_count:";

    public static String historyKey(Long convId) { return HISTORY_KEY + convId; }
    public static String summaryKey(Long convId) { return SUMMARY_KEY + convId; }
    public static String topicEmbeddingKey(Long convId) { return TOPIC_KEY + convId; }
    public static String keywordsKey(Long convId) { return KEYWORDS_KEY + convId; }
    public static String tokenCountKey(Long convId) { return TOKEN_COUNT_KEY + convId; }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(mapper, Object.class);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }
}
