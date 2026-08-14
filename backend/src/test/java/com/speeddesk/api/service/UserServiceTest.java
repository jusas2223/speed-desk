package com.speeddesk.api.service;

import com.speeddesk.api.entity.User;
import com.speeddesk.api.exception.DuplicateEmailException;
import com.speeddesk.api.exception.InvalidCredentialsException;
import com.speeddesk.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

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

    @Test
    void shouldLoginWhenCredentialsAreValid() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getPassword()).thenReturn("secret");
        when(userRepository.findByEmail("user@speeddesk.com")).thenReturn(Optional.of(user));

        User result = userService.login(" user@speeddesk.com ", "secret");

        assertSame(user, result);
        verify(userRepository).findByEmail("user@speeddesk.com");
    }

    @Test
    void shouldRejectLoginWhenEmailDoesNotExist() {
        when(userRepository.findByEmail("missing@speeddesk.com")).thenReturn(Optional.empty());

        assertThrows(
                InvalidCredentialsException.class,
                () -> userService.login("missing@speeddesk.com", "secret")
        );
    }

    @Test
    void shouldRejectLoginWhenPasswordIsInvalid() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getPassword()).thenReturn("correct-password");
        when(userRepository.findByEmail("user@speeddesk.com")).thenReturn(Optional.of(user));

        assertThrows(
                InvalidCredentialsException.class,
                () -> userService.login("user@speeddesk.com", "wrong-password")
        );
    }
}
