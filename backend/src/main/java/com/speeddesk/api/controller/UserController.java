package com.speeddesk.api.controller;

import com.speeddesk.api.dto.LoginRequest;
import com.speeddesk.api.dto.LoginResponse;
import com.speeddesk.api.dto.UserCreateRequestDTO;
import com.speeddesk.api.dto.UserResponseDTO;
import com.speeddesk.api.service.AuthenticationService;
import com.speeddesk.api.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthenticationService authenticationService;

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

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authenticationService.login(request));
    }
}
