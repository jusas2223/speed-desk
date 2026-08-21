package com.speeddesk.api.controller;

import com.speeddesk.api.dto.LoginRequest;
import com.speeddesk.api.dto.LoginResponse;
import com.speeddesk.api.dto.UserCreateRequestDTO;
import com.speeddesk.api.dto.UserResponseDTO;
import com.speeddesk.api.dto.UserStatusUpdateRequestDTO;
import com.speeddesk.api.dto.UserUpdateRequestDTO;
import com.speeddesk.api.dto.PasswordResetIssueResponseDTO;
import com.speeddesk.api.service.AccountService;
import com.speeddesk.api.service.AuthenticationService;
import com.speeddesk.api.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthenticationService authenticationService;
    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<List<UserResponseDTO>> listAll() {
        return ResponseEntity.ok(userService.listAll());
    }

    @PostMapping
    public ResponseEntity<UserResponseDTO> create(
            @Valid @RequestBody UserCreateRequestDTO request
    ) {
        UserResponseDTO savedUser = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<UserResponseDTO> update(
            @PathVariable UUID userId,
            @Valid @RequestBody UserUpdateRequestDTO request
    ) {
        return ResponseEntity.ok(userService.update(userId, request));
    }

    @PatchMapping("/{userId}/status")
    public ResponseEntity<UserResponseDTO> updateStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody UserStatusUpdateRequestDTO request
    ) {
        return ResponseEntity.ok(userService.updateStatus(userId, request));
    }

    @PostMapping("/{userId}/password-reset")
    public ResponseEntity<PasswordResetIssueResponseDTO> issuePasswordReset(
            @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(accountService.issuePasswordReset(userId));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authenticationService.login(request));
    }
}
