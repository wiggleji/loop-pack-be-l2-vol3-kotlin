package com.loopers.config.redis

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class CacheOperator(
    private val redisTemplate: RedisTemplate<String, String>,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val masterRedisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ── Read ──

    fun <T> get(key: String, typeRef: TypeReference<T>, policy: CachePolicy): T? {
        return try {
            val json = redisTemplate.opsForValue().get(key) ?: return null
            objectMapper.readValue(json, typeRef)
        } catch (e: Exception) {
            when (policy) {
                CachePolicy.FALLBACK -> {
                    log.warn("Cache FALLBACK - key={}, error={}", key, e.message)
                    null
                }
                CachePolicy.FAIL_FAST -> {
                    log.error("Cache FAIL_FAST - key={}, error={}", key, e.message)
                    throw CacheException("Redis read failed for key=$key", e)
                }
            }
        }
    }

    // ── Write (항상 best-effort: 예외 삼킴 + error 로그) ──

    fun put(key: String, value: Any, ttl: Duration) {
        try {
            val json = objectMapper.writeValueAsString(value)
            masterRedisTemplate.opsForValue().set(key, json, ttl)
        } catch (e: Exception) {
            log.error("Cache put failed - key={}, error={}", key, e.message)
        }
    }

    fun evict(key: String) {
        try {
            masterRedisTemplate.delete(key)
        } catch (e: Exception) {
            log.error("Cache evict failed - key={}, error={}", key, e.message)
        }
    }

    fun evictByPattern(pattern: String) {
        try {
            val keys = masterRedisTemplate.keys(pattern)
            if (!keys.isNullOrEmpty()) {
                masterRedisTemplate.delete(keys)
            }
        } catch (e: Exception) {
            log.error("Cache evictByPattern failed - pattern={}, error={}", pattern, e.message)
        }
    }

    // ── Lock (항상 best-effort: 장애 시 false/무시) ──

    fun tryLock(key: String, ttl: Duration): Boolean {
        return try {
            masterRedisTemplate.opsForValue().setIfAbsent(key, "1", ttl) ?: false
        } catch (e: Exception) {
            log.error("Cache tryLock failed - key={}, error={}", key, e.message)
            false
        }
    }

    fun unlock(key: String) {
        try {
            masterRedisTemplate.delete(key)
        } catch (e: Exception) {
            log.error("Cache unlock failed - key={}, error={}", key, e.message)
        }
    }
}
