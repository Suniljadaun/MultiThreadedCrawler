package com.sunil.finintel.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.sunil.finintel.common.NotFoundException;
import com.sunil.finintel.user.User;
import com.sunil.finintel.user.UserRepository;

// Cache-aside on real PostgreSQL + Redis
@SpringBootTest(properties = "app.cache.portfolio.enabled=true")
@Testcontainers
@SuppressWarnings({"rawtypes", "resource"})
class PortfolioCacheIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer redis = new GenericContainer(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private MarketPriceService marketPriceService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcClient jdbcClient;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(new User("Sunil", "sunil@example.com")).getId();
        buy(10, "100");
    }

    @AfterEach
    void cleanUp() {
        marketPriceService.update("ACME", new BigDecimal("100"));
        jdbcClient.sql("DELETE FROM positions").update();
        jdbcClient.sql("DELETE FROM users").update();
        redisTemplate.delete(PortfolioCache.key(userId));
    }

    private void buy(long qty, String price) {
        transactionTemplate.executeWithoutResult(s -> portfolioService.buy(userId, "ACME", qty, new BigDecimal(price)));
    }

    @Test
    void secondReadIsServedFromCache() {
        assertThat(portfolioService.getPortfolio(userId).positions().get(0).quantity()).isEqualTo(10);
        assertThat(redisTemplate.hasKey(PortfolioCache.key(userId))).isTrue();

        // Change the DB behind the cache's back: a cached read must not see it
        jdbcClient.sql("UPDATE positions SET quantity = 999 WHERE user_id = :u").param("u", userId).update();

        assertThat(portfolioService.getPortfolio(userId).positions().get(0).quantity()).isEqualTo(10);
    }

    @Test
    void entryHasTtl() {
        portfolioService.getPortfolio(userId);

        Long ttlSeconds = redisTemplate.getExpire(PortfolioCache.key(userId));
        assertThat(ttlSeconds).isBetween(1L, 60L);
    }

    @Test
    void fillEvictsCacheAfterCommit() {
        portfolioService.getPortfolio(userId);
        assertThat(redisTemplate.hasKey(PortfolioCache.key(userId))).isTrue();

        buy(5, "100");

        assertThat(redisTemplate.hasKey(PortfolioCache.key(userId))).isFalse();
        assertThat(portfolioService.getPortfolio(userId).positions().get(0).quantity()).isEqualTo(15);
    }

    @Test
    void rolledBackFillDoesNotEvict() {
        portfolioService.getPortfolio(userId);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(s -> {
            portfolioService.buy(userId, "ACME", 5, new BigDecimal("100"));
            throw new IllegalStateException("simulated failure after the fill");
        })).isInstanceOf(IllegalStateException.class);

        // Nothing was committed, so the cached value is still correct and still there
        assertThat(redisTemplate.hasKey(PortfolioCache.key(userId))).isTrue();
        assertThat(portfolioService.getPortfolio(userId).positions().get(0).quantity()).isEqualTo(10);
    }

    @Test
    void priceChangeEvictsHolders() {
        assertThat(portfolioService.getPortfolio(userId).positions().get(0).marketPrice()).isEqualByComparingTo("100");

        marketPriceService.update("ACME", new BigDecimal("120"));

        assertThat(redisTemplate.hasKey(PortfolioCache.key(userId))).isFalse();
        PortfolioResponse repriced = portfolioService.getPortfolio(userId);
        assertThat(repriced.positions().get(0).marketPrice()).isEqualByComparingTo("120");
        assertThat(repriced.totalUnrealizedPnl()).isEqualByComparingTo("200");
    }

    @Test
    void unknownUserIsNotCached() {
        assertThatThrownBy(() -> portfolioService.getPortfolio(987654L)).isInstanceOf(NotFoundException.class);
        assertThat(redisTemplate.hasKey(PortfolioCache.key(987654L))).isFalse();
    }
}
