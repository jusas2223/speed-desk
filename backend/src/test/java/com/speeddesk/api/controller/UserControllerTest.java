package com.speeddesk.api.controller;

import com.speeddesk.api.dto.LoginRequest;
import com.speeddesk.api.dto.LoginResponse;
import com.speeddesk.api.dto.UserCreateRequestDTO;
import com.speeddesk.api.dto.UserResponseDTO;
import com.speeddesk.api.dto.UserStatusUpdateRequestDTO;
import com.speeddesk.api.dto.UserUpdateRequestDTO;
import com.speeddesk.api.dto.PasswordResetIssueResponseDTO;
import com.speeddesk.api.service.AccountService;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.service.AuthenticationService;
import com.speeddesk.api.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private AccountService accountService;

    @InjectMocks
    private UserController userController;

    @Test
    void returnsPublicUserDtos() {
        List<UserResponseDTO> users = List.of(userResponse(UserRole.CLIENTE));
        when(userService.listAll()).thenReturn(users);

        ResponseEntity<List<UserResponseDTO>> response = userController.listAll();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(users, response.getBody());
    }

    @Test
    void createsUserFromDedicatedRequestDto() {
        UserCreateRequestDTO request = new UserCreateRequestDTO(
                "Gerente",
                "manager@speeddesk.test",
                "Password-123",
                UserRole.GERENTE
        );
        UserResponseDTO saved = userResponse(UserRole.GERENTE);
        when(userService.create(request)).thenReturn(saved);

        ResponseEntity<UserResponseDTO> response = userController.create(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(saved, response.getBody());
        verify(userService).create(request);
    }

    @Test
    void loginReturnsBearerTokenDto() {
        LoginRequest request = new LoginRequest(
                "user@speeddesk.test",
                "Password-123"
        );
        LoginResponse loginResponse = new LoginResponse(
                UUID.randomUUID(),
                "User",
                request.email(),
                UserRole.CLIENTE,
                "jwt-token",
                "Bearer",
                3600
        );
        when(authenticationService.login(request)).thenReturn(loginResponse);

        ResponseEntity<LoginResponse> response = userController.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(loginResponse, response.getBody());
        verify(authenticationService).login(request);
    }

    @Test
    void updatesUserFromDedicatedRequestDto() {
        UUID userId = UUID.randomUUID();
        UserUpdateRequestDTO request = new UserUpdateRequestDTO(
                "Usuário atualizado",
                "updated@speeddesk.test",
                UserRole.TECNICO,
                null
        );
        UserResponseDTO updated = userResponse(UserRole.TECNICO);
        when(userService.update(userId, request)).thenReturn(updated);

        ResponseEntity<UserResponseDTO> response = userController.update(userId, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(updated, response.getBody());
        verify(userService).update(userId, request);
    }

    @Test
    void updatesUserStatus() {
        UUID userId = UUID.randomUUID();
        UserStatusUpdateRequestDTO request = new UserStatusUpdateRequestDTO(false);
        UserResponseDTO updated = userResponse(UserRole.CLIENTE);
        when(userService.updateStatus(userId, request)).thenReturn(updated);

        ResponseEntity<UserResponseDTO> response = userController.updateStatus(
                userId,
                request
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(updated, response.getBody());
        verify(userService).updateStatus(userId, request);
    }

    @Test
    void issuesManualPasswordResetToken() {
        UUID userId = UUID.randomUUID();
        PasswordResetIssueResponseDTO issued = new PasswordResetIssueResponseDTO(
                userId,
                "Usuário",
                "one-time-token",
                OffsetDateTime.parse("2026-08-17T12:30:00Z")
        );
        when(accountService.issuePasswordReset(userId)).thenReturn(issued);

        ResponseEntity<PasswordResetIssueResponseDTO> response =
                userController.issuePasswordReset(userId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(issued, response.getBody());
        verify(accountService).issuePasswordReset(userId);
    }

    private UserResponseDTO userResponse(UserRole role) {
        return new UserResponseDTO(
                UUID.randomUUID(),
                "User",
                "user@speeddesk.test",
                role,
                OffsetDateTime.parse("2026-08-17T12:00:00Z")
        );
    }
}
