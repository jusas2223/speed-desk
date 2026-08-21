package com.speeddesk.api.controller;

import com.speeddesk.api.dto.OperationMessageDTO;
import com.speeddesk.api.dto.PasswordChangeRequestDTO;
import com.speeddesk.api.dto.PasswordResetConfirmRequestDTO;
import com.speeddesk.api.dto.UserProfileUpdateRequestDTO;
import com.speeddesk.api.dto.UserResponseDTO;
import com.speeddesk.api.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/profile")
    public ResponseEntity<UserResponseDTO> getProfile() {
        return ResponseEntity.ok(accountService.getProfile());
    }

    @PutMapping("/profile")
    public ResponseEntity<UserResponseDTO> updateProfile(
            @Valid @RequestBody UserProfileUpdateRequestDTO request
    ) {
        return ResponseEntity.ok(accountService.updateProfile(request));
    }

    @PostMapping("/password/change")
    public ResponseEntity<OperationMessageDTO> changePassword(
            @Valid @RequestBody PasswordChangeRequestDTO request
    ) {
        return ResponseEntity.ok(accountService.changePassword(request));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<OperationMessageDTO> resetPassword(
            @Valid @RequestBody PasswordResetConfirmRequestDTO request
    ) {
        return ResponseEntity.ok(accountService.resetPassword(request));
    }
}
