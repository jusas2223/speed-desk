package com.speeddesk.api.service;

import com.speeddesk.api.entity.User;
import com.speeddesk.api.exception.DuplicateEmailException;
import com.speeddesk.api.exception.InvalidCredentialsException;
import com.speeddesk.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public List<User> listAll() {
        return userRepository.findAll();
    }

    public User login(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new InvalidCredentialsException();
        }

        User user = userRepository.findByEmail(email.trim())
                .orElseThrow(InvalidCredentialsException::new);

        if (!Objects.equals(user.getPassword(), password)) {
            throw new InvalidCredentialsException();
        }

        return user;
    }

    @Transactional
    public User create(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new DuplicateEmailException();
        }

        return userRepository.save(user);
    }
}
