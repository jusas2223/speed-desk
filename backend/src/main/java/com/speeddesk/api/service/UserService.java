package com.speeddesk.api.service;

import com.speeddesk.api.entity.User;
import com.speeddesk.api.exception.DuplicateEmailException;
import com.speeddesk.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public List<User> listAll() {
        return userRepository.findAll();
    }

    @Transactional
    public User create(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new DuplicateEmailException();
        }

        return userRepository.save(user);
    }
}
