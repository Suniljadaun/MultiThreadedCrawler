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
    void updateChangesOnlyGivenFields() {
        User existing = new User("Sunil", "sunil@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.saveAndFlush(existing)).thenReturn(existing);

        UserResponse result = userService.update(1L, new UpdateUserRequest("Sunil J", null));

        assertThat(result.name()).isEqualTo("Sunil J");
        assertThat(result.email()).isEqualTo("sunil@example.com");
        // Email did not change, so no uniqueness lookup is needed
        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    void updateRejectsEmailTakenByAnotherUser() {
        User existing = new User("Sunil", "sunil@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.update(1L, new UpdateUserRequest(null, "Taken@Example.com")))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateThrowsWhenUserMissing() {
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(7L, new UpdateUserRequest("X", null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getThrowsWhenMissing() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.get(42L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("user 42 not found");
    }
}
