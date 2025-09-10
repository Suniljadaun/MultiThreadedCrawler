package com.sunil.finintel.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Request body for PATCH /api/v1/users/{id}. Null fields are left unchanged.
public record UpdateUserRequest(
        @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
        @Email @Size(max = 255) String email) {
}
