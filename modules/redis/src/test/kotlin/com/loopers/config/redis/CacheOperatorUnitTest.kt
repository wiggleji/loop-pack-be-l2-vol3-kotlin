package com.loopers.config.redis

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

class CacheOperatorUnitTest {

    private val replicaOps: ValueOperations<String, String> = mockk()
    private val masterOps: ValueOperations<String, String> = mockk()

    private val redisTemplate: RedisTemplate<String, String> = mockk {
        every { opsForValue() } returns replicaOps
    }
    private val masterRedisTemplate: RedisTemplate<String, String> = mockk {
        every { opsForValue() } returns masterOps
    }
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    private val cacheOperator = CacheOperator(redisTemplate, masterRedisTemplate, objectMapper)

    private val stringTypeRef = object : TypeReference<String>() {}

    @DisplayName("get()")
    @Nested
    inner class Get {

        @DisplayName("should return deserialized value on cache hit")
        @Test
        fun `get() should return deserialized value on cache hit`() {
            // arrange
            every { replicaOps.get("key1") } returns "\"hello\""

            // act
            val result = cacheOperator.get("key1", stringTypeRef, CachePolicy.FALLBACK)

            // assert
            assertThat(result).isEqualTo("hello")
        }

        @DisplayName("should return null on cache miss")
        @Test
        fun `get() should return null on cache miss`() {
            // arrange
            every { replicaOps.get("key1") } returns null

            // act
            val result = cacheOperator.get("key1", stringTypeRef, CachePolicy.FALLBACK)

            // assert
            assertThat(result).isNull()
        }

        @DisplayName("should return null when Redis fails with FALLBACK policy")
        @Test
        fun `get() should return null when Redis fails with FALLBACK policy`() {
            // arrange
            every { replicaOps.get("key1") } throws RedisConnectionFailureException("Connection refused")

            // act
            val result = cacheOperator.get("key1", stringTypeRef, CachePolicy.FALLBACK)

            // assert
            assertThat(result).isNull()
        }

        @DisplayName("should throw CacheException when Redis fails with FAIL_FAST policy")
        @Test
        fun `get() should throw CacheException when Redis fails with FAIL_FAST policy`() {
            // arrange
            every { replicaOps.get("key1") } throws RedisConnectionFailureException("Connection refused")

            // act & assert
            assertThatThrownBy {
                cacheOperator.get("key1", stringTypeRef, CachePolicy.FAIL_FAST)
            }.isInstanceOf(CacheException::class.java)
                .hasMessageContaining("key=key1")
        }
    }

    @DisplayName("put()")
    @Nested
    inner class Put {

        @DisplayName("should swallow exception when Redis fails")
        @Test
        fun `put() should swallow exception when Redis fails`() {
            // arrange
            every { masterOps.set(any(), any(), any<Duration>()) } throws RedisConnectionFailureException("Connection refused")

            // act - should not throw
            cacheOperator.put("key1", "value", Duration.ofMinutes(5))
        }
    }

    @DisplayName("evict()")
    @Nested
    inner class Evict {

        @DisplayName("should swallow exception when Redis fails")
        @Test
        fun `evict() should swallow exception when Redis fails`() {
            // arrange
            every { masterRedisTemplate.delete(any<String>()) } throws RedisConnectionFailureException("Connection refused")

            // act - should not throw
            cacheOperator.evict("key1")
        }
    }

    @DisplayName("tryLock()")
    @Nested
    inner class TryLock {

        @DisplayName("should return false when Redis fails")
        @Test
        fun `tryLock() should return false when Redis fails`() {
            // arrange
            every { masterOps.setIfAbsent(any(), any(), any<Duration>()) } throws RedisConnectionFailureException("Connection refused")

            // act
            val result = cacheOperator.tryLock("lock:1", Duration.ofSeconds(3))

            // assert
            assertThat(result).isFalse()
        }

        @DisplayName("should return true when lock acquired")
        @Test
        fun `tryLock() should return true when lock acquired`() {
            // arrange
            every { masterOps.setIfAbsent("lock:1", "1", Duration.ofSeconds(3)) } returns true

            // act
            val result = cacheOperator.tryLock("lock:1", Duration.ofSeconds(3))

            // assert
            assertThat(result).isTrue()
        }
    }

    @DisplayName("unlock()")
    @Nested
    inner class Unlock {

        @DisplayName("should swallow exception when Redis fails")
        @Test
        fun `unlock() should swallow exception when Redis fails`() {
            // arrange
            every { masterRedisTemplate.delete(any<String>()) } throws RedisConnectionFailureException("Connection refused")

            // act - should not throw
            cacheOperator.unlock("lock:1")
        }
    }
}
