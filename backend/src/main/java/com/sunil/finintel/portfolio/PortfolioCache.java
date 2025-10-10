package com.sunil.finintel.portfolio;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.json.JsonMapper;

// Cache-aside for GET /portfolio/{userId} (ADR-004, docs/caching.md).
// Redis is never the source of truth: every failure is logged, counted and treated as a miss.
@Component
public class PortfolioCache {

    private static final Logger log = LoggerFactory.getLogger(PortfolioCache.class);
    private static final String KEY_PREFIX = "portfolio:v1:";

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final boolean enabled;
    private final Duration ttl;
    private final Counter hits;
    private final Counter misses;
    private final Counter errors;

    public PortfolioCache(StringRedisTemplate redis, JsonMapper jsonMapper, MeterRegistry meterRegistry,
                          @Value("${app.cache.portfolio.enabled:true}") boolean enabled,
                          @Value("${app.cache.portfolio.ttl:60s}") Duration ttl) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.enabled = enabled;
        this.ttl = ttl;
        this.hits = counter(meterRegistry, "hit");
        this.misses = counter(meterRegistry, "miss");
        this.errors = counter(meterRegistry, "error");
    }

    private static Counter counter(MeterRegistry registry, String result) {
        return Counter.builder("portfolio.cache")
                .description("Portfolio cache lookups and failures")
                .tag("result", result)
                .register(registry);
    }

    static String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    public Optional<PortfolioResponse> get(Long userId) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            String json = redis.opsForValue().get(key(userId));
            if (json == null) {
                misses.increment();
                return Optional.empty();
            }
            hits.increment();
            return Optional.of(jsonMapper.readValue(json, PortfolioResponse.class));
        } catch (RuntimeException e) {
            errors.increment();
            log.warn("Portfolio cache read failed for user {}, using database: {}", userId, e.toString());
            return Optional.empty();
        }
    }

    public void put(Long userId, PortfolioResponse portfolio) {
        if (!enabled) {
            return;
        }
        try {
            redis.opsForValue().set(key(userId), jsonMapper.writeValueAsString(portfolio), ttl);
        } catch (RuntimeException e) {
            errors.increment();
            log.warn("Portfolio cache write failed for user {}: {}", userId, e.toString());
        }
    }

    public void evict(Long userId) {
        if (!enabled) {
            return;
        }
        try {
            redis.delete(key(userId));
        } catch (RuntimeException e) {
            // Entry expires by TTL, so staleness is bounded
            errors.increment();
            log.warn("Portfolio cache evict failed for user {}, entry expires in {}: {}", userId, ttl, e.toString());
        }
    }

    // Evict only after the DB change is committed. Evicting earlier lets a concurrent
    // reader load the old data and put it straight back into the cache.
    public void evictAfterCommit(Long userId) {
        if (!enabled) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(userId);
                }
            });
        } else {
            evict(userId);
        }
    }
}
