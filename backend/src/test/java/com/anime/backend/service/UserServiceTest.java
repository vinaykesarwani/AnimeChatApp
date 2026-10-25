package com.anime.backend.service;

import com.anime.backend.dto.UserCreateDto;
import com.anime.backend.dto.UserUpdateDto;
import com.anime.backend.entity.Role;
import com.anime.backend.entity.User;
import com.anime.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private UserRepository userRepo;
    private PasswordEncoder encoder;
    private UserService service;

    @BeforeEach
    void setUp() {
        userRepo = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        service = new UserService(userRepo, encoder);
    }

    @Test
    void publicRegistrationHashesPasswordAndIgnoresRequestedAdminRole() {
        UserCreateDto request = new UserCreateDto();
        request.setUsername("  new-user  ");
        request.setPassword("test-password");
        request.setRole(Role.ADMIN);

        when(userRepo.findByUsername("new-user")).thenReturn(Optional.empty());
        when(encoder.encode("test-password")).thenReturn("encoded-password");

        service.createUser(request);

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(savedUser.capture());
        assertEquals(Role.USER, savedUser.getValue().getRole());
        assertEquals("new-user", savedUser.getValue().getUsername());
        assertEquals("encoded-password", savedUser.getValue().getPassword());
        verify(encoder).encode("test-password");
    }

    @Test
    void duplicateUsernameReturnsConflictWithoutSavingOrEncoding() {
        UserCreateDto request = new UserCreateDto();
        request.setUsername("existing-user");
        request.setPassword("test-password");
        when(userRepo.findByUsername("existing-user")).thenReturn(Optional.of(new User()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.createUser(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(userRepo, never()).save(any(User.class));
        verifyNoInteractions(encoder);
    }

    @Test
    void anotherUserCannotChangeThePasswordOrCauseAWrite() {
        User target = account(1L, "alice", Role.USER);
        when(userRepo.findById(1L)).thenReturn(Optional.of(target));
        when(userRepo.findByUsername("bob")).thenReturn(Optional.of(account(2L, "bob", Role.USER)));
        UserUpdateDto request = new UserUpdateDto();
        request.setPassword("new-password");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updateUser(1L, request, "bob"));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals("stored-hash", target.getPassword());
        verify(userRepo, never()).save(any(User.class));
        verifyNoInteractions(encoder);
    }

    private User account(Long id, String username, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setPassword("stored-hash");
        return user;
    }
}
