package com.speeddesk.api.controller;

import com.speeddesk.api.entity.User;
import com.speeddesk.api.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
