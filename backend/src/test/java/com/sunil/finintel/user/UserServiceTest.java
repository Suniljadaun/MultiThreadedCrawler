package com.sunil.finintel.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.sunil.finintel.common.ConflictException;
import com.sunil.finintel.common.NotFoundException;

// Plain unit test: no Spring context, repository is mocked
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void createNormalizesEmailAndSaves() {
        when(userRepository.existsByEmail("sunil@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = userService.create(new CreateUserRequest("  Sunil ", " Sunil@Example.COM "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("sunil@example.com");
        assertThat(saved.getValue().getName()).isEqualTo("Sunil");
        assertThat(result.email()).isEqualTo("sunil@example.com");
    }

    @Test
    void createRejectsExistingEmail() {
        when(userRepository.existsByEmail("sunil@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(new CreateUserRequest("Sunil", "sunil@example.com")))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void createHandlesRaceOnUniqueConstraint() {
        // Both requests passed existsByEmail; the DB unique constraint rejects the second insert
        when(userRepository.existsByEmail("sunil@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("uq_users_email"));

        assertThatThrownBy(() -> userService.create(new CreateUserRequest("Sunil", "sunil@example.com")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void getThrowsWhenMissing() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.get(42L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("user 42 not found");
    }
}
