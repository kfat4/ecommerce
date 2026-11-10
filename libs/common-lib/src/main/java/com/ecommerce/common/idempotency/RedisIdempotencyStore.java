package com.ecommerce.common.idempotency;

import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis is shared by all instances of a service, so a retry that lands on a different replica
 * (round-robin load balancing) still sees the first attempt – a prerequisite for horizontal scaling.
 */
public class RedisIdempotencyStore implements IdempotencyStore {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisIdempotencyStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<IdempotencyRecord> find(String key) {
        String json = redis.opsForValue().get(key);
        return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, IdempotencyRecord.class));
    }

    @Override
    public boolean tryAcquire(String key, IdempotencyRecord inProgress, Duration ttl) {
        Boolean acquired = redis.opsForValue().setIfAbsent(key, objectMapper.writeValueAsString(inProgress), ttl);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void complete(String key, IdempotencyRecord completed, Duration ttl) {
        redis.opsForValue().set(key, objectMapper.writeValueAsString(completed), ttl);
    }

    @Override
    public void release(String key) {
        redis.delete(key);
    }
}
