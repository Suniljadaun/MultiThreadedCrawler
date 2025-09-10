package com.sunil.finintel.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sunil.finintel.common.ConflictException;
import com.sunil.finintel.common.NotFoundException;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase();

        // Fast check for a friendly error. Not enough on its own:
        // two requests can pass it at the same time.
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("email already registered");
        }

        try {
            // saveAndFlush sends the INSERT now, so the unique constraint
            // on users.email catches the race inside this method.
            User saved = userRepository.saveAndFlush(new User(request.name().trim(), email));
            return UserResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("email already registered");
        }
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("user " + id + " not found"));

        String name = request.name() != null ? request.name().trim() : user.getName();
        String email = request.email() != null ? request.email().trim().toLowerCase() : user.getEmail();

        // Only check uniqueness if the email is actually changing
        if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new ConflictException("email already registered");
        }

        user.update(name, email);
        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("email already registered");
        }
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("user " + id + " not found"));
    }
}
