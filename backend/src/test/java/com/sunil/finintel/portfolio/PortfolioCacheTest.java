package com.sunil.finintel.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.stream.LongStream;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PortfolioCacheTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> ops;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private SimpleMeterRegistry registry;
    private PortfolioCache cache;

    private final PortfolioResponse sample = new PortfolioResponse(1L,
            List.of(new PositionView("ACME", 10, new BigDecimal("100.0000"), new BigDecimal("112.5000"),
                    new BigDecimal("1125.0000"), new BigDecimal("125.0000"))),
            new BigDecimal("1000.0000"), new BigDecimal("1125.0000"), new BigDecimal("125.0000"));

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        cache = new PortfolioCache(redis, jsonMapper, registry, true, Duration.ofSeconds(60));
    }

    private double count(String result) {
        return registry.counter("portfolio.cache", "result", result).count();
    }

    @Test
    void missReturnsEmptyAndCountsMiss() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("portfolio:v1:1")).thenReturn(null);

        assertThat(cache.get(1L)).isEmpty();
        assertThat(count("miss")).isEqualTo(1.0);
    }

    @Test
    void hitReturnsCachedPortfolio() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("portfolio:v1:1")).thenReturn(jsonMapper.writeValueAsString(sample));

        Optional<PortfolioResponse> cached = cache.get(1L);

        assertThat(cached).isPresent();
        assertThat(cached.get().positions().get(0).quantity()).isEqualTo(10);
        assertThat(cached.get().totalUnrealizedPnl()).isEqualByComparingTo("125");
        assertThat(count("hit")).isEqualTo(1.0);
    }

    @Test
    void redisDownIsTreatedAsMiss() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThat(cache.get(1L)).isEmpty();
        assertThat(count("error")).isEqualTo(1.0);
    }

    @Test
    void putStoresJsonWithTtl() {
        when(redis.opsForValue()).thenReturn(ops);

        cache.put(1L, sample);

        verify(ops).set(eq("portfolio:v1:1"), anyString(), eq(Duration.ofSeconds(60)));
    }

    @Test
    void putFailureIsSwallowed() {
        when(redis.opsForValue()).thenReturn(ops);
        doThrow(new RedisConnectionFailureException("down")).when(ops).set(anyString(), anyString(), any(Duration.class));

        cache.put(1L, sample);

        assertThat(count("error")).isEqualTo(1.0);
    }

    @Test
    void evictOutsideTransactionDeletesImmediately() {
        cache.evictAfterCommit(1L);

        verify(redis).delete("portfolio:v1:1");
    }

    @Test
    void evictAllDeletesKeysInChunks() {
        List<Long> ids = LongStream.rangeClosed(1, 2500).boxed().toList();

        cache.evictAllAfterCommit(ids);

        verify(redis, times(3)).delete(anyCollection());
        // last chunk holds the remaining 500 keys
        verify(redis).delete(LongStream.rangeClosed(2001, 2500).mapToObj(i -> "portfolio:v1:" + i).toList());
    }

    @Test
    void evictAllFailureIsCountedPerChunk() {
        when(redis.delete(anyCollection())).thenThrow(new RedisConnectionFailureException("down"));

        cache.evictAll(LongStream.rangeClosed(1, 1500).boxed().toList());

        assertThat(count("error")).isEqualTo(2.0);
    }

    @Test
    void evictAllWithNoHoldersDoesNothing() {
        cache.evictAllAfterCommit(List.of());

        verifyNoInteractions(redis);
    }

    @Test
    void disabledCacheNeverTouchesRedis() {
        PortfolioCache disabled = new PortfolioCache(redis, jsonMapper, registry, false, Duration.ofSeconds(60));

        assertThat(disabled.get(1L)).isEmpty();
        disabled.put(1L, sample);
        disabled.evictAfterCommit(1L);
        disabled.evictAllAfterCommit(List.of(1L, 2L));

        verifyNoInteractions(redis);
    }
}
