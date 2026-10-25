package com.anime.backend.controller;

import com.anime.backend.entity.Role;
import com.anime.backend.entity.User;
import com.anime.backend.repository.UserRepository;
import com.anime.backend.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {
    private UserRepository users;
    private PasswordEncoder passwords;
    private JwtUtil tokens;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        passwords = mock(PasswordEncoder.class);
        tokens = mock(JwtUtil.class);
        controller = new AuthController(users, passwords, tokens);
    }

    @Test
    void validCredentialsReturnATokenForTheStoredAccount() {
        User user = account("stored-hash");
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwords.matches("password", "stored-hash")).thenReturn(true);
        when(tokens.generateToken("alice")).thenReturn("signed-token");

        Map<String, Object> response = controller.login(Map.of("username", "  alice  ", "password", "password"));

        assertEquals("signed-token", response.get("token"));
        assertSame(user, response.get("user"));
        verify(passwords).matches("password", "stored-hash");
        verify(tokens).generateToken("alice");
    }

    @Test
    void wrongPasswordCannotProduceAToken() {
        when(users.findByUsername("alice")).thenReturn(Optional.of(account("stored-hash")));
        when(passwords.matches("wrong-password", "stored-hash")).thenReturn(false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.login(Map.of("username", "alice", "password", "wrong-password")));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(tokens);
    }

    private User account(String hash) {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword(hash);
        user.setRole(Role.USER);
        return user;
    }
}
