package com.anime.backend.controller;

import com.anime.backend.entity.Role;
import com.anime.backend.entity.User;
import com.anime.backend.repository.UserRepository;
import com.anime.backend.security.JwtUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OAuthPasswordLoginTest {
    @ParameterizedTest
    @NullAndEmptySource
    void oauthOnlyAccountCannotSignInWithAnArbitraryLocalPassword(String storedPassword) {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        JwtUtil tokens = mock(JwtUtil.class);
        User user = new User();
        user.setUsername("google-user");
        user.setGoogleId("google-sub");
        user.setRole(Role.USER);
        user.setPassword(storedPassword);
        when(users.findByUsername("google-user")).thenReturn(Optional.of(user));

        AuthController controller = new AuthController(users, passwords, tokens);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.login(Map.of("username", "google-user", "password", "any-password")));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(passwords, tokens);
    }
}