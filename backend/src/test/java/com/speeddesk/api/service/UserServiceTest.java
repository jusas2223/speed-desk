package com.speeddesk.api.service;

import com.speeddesk.api.entity.User;
import com.speeddesk.api.exception.DuplicateEmailException;
import com.speeddesk.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldListAllUsers() {
        User user = org.mockito.Mockito.mock(User.class);
        List<User> users = List.of(user);
        when(userRepository.findAll()).thenReturn(users);

        List<User> result = userService.listAll();

        assertEquals(users, result);
        verify(userRepository).findAll();
    }

    @Test
    void shouldCreateUserWhenEmailDoesNotExist() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getEmail()).thenReturn("user@speeddesk.com");
        when(userRepository.existsByEmail("user@speeddesk.com")).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);

        User result = userService.create(user);

        assertSame(user, result);
        verify(userRepository).existsByEmail("user@speeddesk.com");
        verify(userRepository).save(user);
    }

    @Test
    void shouldRejectUserWhenEmailAlreadyExists() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getEmail()).thenReturn("existing@speeddesk.com");
        when(userRepository.existsByEmail("existing@speeddesk.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> userService.create(user));

        verify(userRepository).existsByEmail("existing@speeddesk.com");
        verify(userRepository, never()).save(user);
    }
}
