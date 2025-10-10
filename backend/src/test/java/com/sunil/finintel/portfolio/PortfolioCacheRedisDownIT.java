package com.sunil.finintel.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.sunil.finintel.user.User;
import com.sunil.finintel.user.UserRepository;

import io.micrometer.core.instrument.MeterRegistry;

// Cache enabled, but Redis points at a port where nothing listens:
// the portfolio must still be correct, served from PostgreSQL.
@SpringBootTest(properties = {
        "app.cache.portfolio.enabled=true",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=1"
})
@Testcontainers
class PortfolioCacheRedisDownIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void portfolioStillWorksWithoutRedis() {
        Long userId = userRepository.save(new User("Sunil", "sunil@example.com")).getId();
        // The fill also tries to evict from Redis after commit; that failure must not break it
        transactionTemplate.executeWithoutResult(s ->
                portfolioService.buy(userId, "ACME", 10, new BigDecimal("100")));

        PortfolioResponse first = portfolioService.getPortfolio(userId);
        PortfolioResponse second = portfolioService.getPortfolio(userId);

        assertThat(first.positions().get(0).quantity()).isEqualTo(10);
        assertThat(second.positions().get(0).quantity()).isEqualTo(10);
        assertThat(meterRegistry.counter("portfolio.cache", "result", "error").count()).isGreaterThanOrEqualTo(2.0);
        assertThat(meterRegistry.counter("portfolio.cache", "result", "hit").count()).isZero();
    }
}
