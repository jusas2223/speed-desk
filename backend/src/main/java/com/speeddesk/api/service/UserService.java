package com.speeddesk.api.service;

import com.speeddesk.api.dto.UserCreateRequestDTO;
import com.speeddesk.api.dto.UserResponseDTO;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.exception.DuplicateEmailException;
import com.speeddesk.api.exception.InvalidRequestException;
import com.speeddesk.api.repository.UserRepository;
import com.speeddesk.api.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UserResponseDTO> listAll() {
        return userRepository.findAll().stream()
                .map(UserResponseDTO::from)
                .toList();
    }

    @Transactional
    public UserResponseDTO create(UserCreateRequestDTO request) {
        String normalizedEmail = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DuplicateEmailException();
        }

        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidRequestException(
                    "A senha deve possuir no máximo 72 bytes em UTF-8."
            );
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();

        return UserResponseDTO.from(userRepository.save(user));
    }
}
