package com.speeddesk.api.controller;

import com.speeddesk.api.dto.LoginRequest;
import com.speeddesk.api.dto.LoginResponse;
import com.speeddesk.api.entity.User;
import com.speeddesk.api.entity.UserRole;
import com.speeddesk.api.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @Test
    void shouldReturnAllUsers() {
        User user = org.mockito.Mockito.mock(User.class);
        List<User> users = List.of(user);
        when(userService.listAll()).thenReturn(users);

        ResponseEntity<List<User>> response = userController.listAll();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(users, response.getBody());
        verify(userService).listAll();
    }

    @Test
    void shouldCreateUser() {
        User user = org.mockito.Mockito.mock(User.class);
        when(userService.create(user)).thenReturn(user);

        ResponseEntity<User> response = userController.create(user);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(user, response.getBody());
        verify(userService).create(user);
    }

    @Test
    void shouldLoginUser() {
        UUID userId = UUID.randomUUID();
        LoginRequest request = new LoginRequest("user@speeddesk.com", "secret");
        User user = User.builder()
                .id(userId)
                .name("Speed Desk User")
                .email(request.email())
                .password(request.password())
                .role(UserRole.CLIENTE)
                .build();
        when(userService.login(request.email(), request.password())).thenReturn(user);

        ResponseEntity<LoginResponse> response = userController.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(
                new LoginResponse(userId, "Speed Desk User", request.email(), UserRole.CLIENTE),
                response.getBody()
        );
        verify(userService).login(request.email(), request.password());
    }

    @Test
    void shouldMapPostLoginEndpoint() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .name("Speed Desk User")
                .email("user@speeddesk.com")
                .password("secret")
                .role(UserRole.CLIENTE)
                .build();
        when(userService.login("user@speeddesk.com", "secret")).thenReturn(user);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(userController).build();

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@speeddesk.com","password":"secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("user@speeddesk.com"))
                .andExpect(jsonPath("$.role").value("CLIENTE"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }
}
