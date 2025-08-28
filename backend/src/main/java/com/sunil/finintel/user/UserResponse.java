package com.sunil.finintel.user;

import java.time.Instant;

// API view of a user. Keeps the JPA entity out of the public contract.
public record UserResponse(Long id, String name, String email, Instant createdAt, Instant updatedAt) {

    static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
