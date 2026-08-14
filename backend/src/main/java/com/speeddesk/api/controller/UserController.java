package com.speeddesk.api.controller;

import com.speeddesk.api.dto.LoginRequest;
import com.speeddesk.api.dto.LoginResponse;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.service.UserService;
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

    @GetMapping
    public ResponseEntity<List<User>> listAll() {
        return ResponseEntity.ok(userService.listAll());
    }

    @PostMapping
    public ResponseEntity<User> create(@RequestBody User user) {
        User savedUser = userService.create(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        User authenticatedUser = userService.login(request.email(), request.password());
        return ResponseEntity.ok(LoginResponse.from(authenticatedUser));
    }
}
