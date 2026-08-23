package com.speeddesk.api.controller;

import com.speeddesk.api.dto.OperationMessageDTO;
import com.speeddesk.api.dto.AccountProfileResponseDTO;
import com.speeddesk.api.dto.PasswordChangeRequestDTO;
import com.speeddesk.api.dto.PasswordResetConfirmRequestDTO;
import com.speeddesk.api.dto.UserProfileUpdateRequestDTO;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.service.AccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AccountService accountService;

    @InjectMocks
    private AccountController accountController;

    @Test
    void returnsAndUpdatesCurrentProfile() {
        AccountProfileResponseDTO profile = profile();
        UserProfileUpdateRequestDTO request = new UserProfileUpdateRequestDTO(
                "Nome atualizado",
                "updated@speeddesk.test",
                "5511999998888"
        );
        when(accountService.getProfile()).thenReturn(profile);
        when(accountService.updateProfile(request)).thenReturn(profile);

        ResponseEntity<AccountProfileResponseDTO> getResponse = accountController.getProfile();
        ResponseEntity<AccountProfileResponseDTO> updateResponse =
                accountController.updateProfile(request);

        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertSame(profile, getResponse.getBody());
        assertSame(profile, updateResponse.getBody());
    }

    @Test
    void changesAndResetsPassword() {
        PasswordChangeRequestDTO change = new PasswordChangeRequestDTO(
                "Current-123",
                "New-password-123"
        );
        PasswordResetConfirmRequestDTO reset = new PasswordResetConfirmRequestDTO(
                "one-time-token",
                "Reset-password-123"
        );
        OperationMessageDTO changed = new OperationMessageDTO("Senha alterada.");
        OperationMessageDTO resetDone = new OperationMessageDTO("Senha redefinida.");
        when(accountService.changePassword(change)).thenReturn(changed);
        when(accountService.resetPassword(reset)).thenReturn(resetDone);

        assertSame(changed, accountController.changePassword(change).getBody());
        assertSame(resetDone, accountController.resetPassword(reset).getBody());
        verify(accountService).changePassword(change);
        verify(accountService).resetPassword(reset);
    }

    private AccountProfileResponseDTO profile() {
        return new AccountProfileResponseDTO(
                UUID.randomUUID(),
                "Usuário",
                "user@speeddesk.test",
                "5511999998888",
                UserRole.CLIENTE,
                null,
                true,
                OffsetDateTime.parse("2026-08-17T12:00:00Z")
        );
    }
}
