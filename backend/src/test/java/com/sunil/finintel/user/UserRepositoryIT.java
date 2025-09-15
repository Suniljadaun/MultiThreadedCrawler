package com.sunil.finintel.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// Runs against a real PostgreSQL in Docker (same image as docker-compose).
// Checks that the Flyway migration works and the DB constraints are real.
@SpringBootTest
@Testcontainers
class UserRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    void savesUserWithGeneratedIdAndTimestamps() {
        User saved = userRepository.saveAndFlush(new User("Sunil", "sunil@example.com"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(userRepository.existsByEmail("sunil@example.com")).isTrue();
    }

    @Test
    void uniqueEmailConstraintIsEnforcedByDatabase() {
        userRepository.saveAndFlush(new User("Sunil", "sunil@example.com"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(new User("Other", "sunil@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
