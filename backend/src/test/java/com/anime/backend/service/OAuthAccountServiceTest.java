package com.anime.backend.service;

import com.anime.backend.entity.Role;
import com.anime.backend.entity.User;
import com.anime.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OAuthAccountServiceTest {
    private UserRepository users;
    private PasswordEncoder passwords;
    private UserService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        passwords = mock(PasswordEncoder.class);
        service = new UserService(users, passwords);
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void returningGoogleUserKeepsExistingAccountAndRole() {
        User existing = account(7L, "original-name", Role.ADMIN);
        existing.setGoogleId("google-sub");
        existing.setEmail("original@example.com");
        when(users.findByGoogleId("google-sub")).thenReturn(Optional.of(existing));

        User result = service.findOrCreateGoogleUser("google-sub", "changed@example.com", "Changed Name");

        assertSame(existing, result);
        assertEquals(7L, result.getId());
        assertEquals("original-name", result.getUsername());
        assertEquals(Role.ADMIN, result.getRole());
        assertEquals("original@example.com", result.getEmail());
        assertNull(result.getPassword());
        verify(users, never()).save(any());
        verifyNoInteractions(passwords);
    }

    @Test
    void firstGoogleSignInCreatesSeparateAccountEvenWhenEmailMatchesLocalUser() {
        User local = account(1L, "alice", Role.USER);
        local.setEmail("alice@example.com");
        local.setPassword("stored-password-hash");
        when(users.findByUsername("alice")).thenReturn(Optional.of(local));

        User result = service.findOrCreateGoogleUser("new-google-sub", "alice@example.com", "Alice");

        assertNotSame(local, result);
        assertEquals("alice_2", result.getUsername());
        assertEquals("alice@example.com", result.getEmail());
        assertEquals("new-google-sub", result.getGoogleId());
        assertEquals("google:new-google-sub", result.getOauthId());
        assertEquals(Role.USER, result.getRole());
        assertEquals("", result.getPassword());
        assertEquals("stored-password-hash", local.getPassword());
        verifyNoInteractions(passwords);
    }

    @Test
    void sameNumericIdFromGoogleAndGithubSelectsDifferentAccounts() {
        User google = account(10L, "google-user", Role.USER);
        google.setGoogleId("42");
        User github = account(11L, "github-user", Role.USER);
        github.setOauthId("github:42");
        when(users.findByGoogleId("42")).thenReturn(Optional.of(google));
        when(users.findByOauthId("github:42")).thenReturn(Optional.of(github));

        assertSame(google, service.findOrCreateGoogleUser("42", "person@example.com", "Person"));
        assertSame(github, service.findOrCreateGithubUser("42", "person"));
        verify(users, never()).save(any());
    }

    @Test
    void firstGithubSignInStoresProviderScopedIdAndNoPassword() {
        User result = service.findOrCreateGithubUser("42", "octo-cat");

        assertEquals("github:42", result.getOauthId());
        assertEquals("octo_cat", result.getUsername());
        assertEquals(Role.USER, result.getRole());
        assertNull(result.getGoogleId());
        assertEquals("", result.getPassword());
        verifyNoInteractions(passwords);
    }

    @Test
    void rawLegacyIdIsBackfilledOnlyWhenGoogleIdConfirmsItsProvider() {
        User existing = account(12L, "kept-name", Role.ADMIN);
        existing.setGoogleId("42");
        existing.setOauthId("42");
        when(users.findByGoogleId("42")).thenReturn(Optional.of(existing));

        User result = service.findOrCreateGoogleUser("42", "person@example.com", "Person");

        assertSame(existing, result);
        assertEquals(12L, result.getId());
        assertEquals("kept-name", result.getUsername());
        assertEquals(Role.ADMIN, result.getRole());
        assertEquals("google:42", result.getOauthId());
        verify(users).save(existing);
    }

    @ParameterizedTest
    @ValueSource(strings = {"google", "github"})
    void rawLegacyIdWithoutProviderProofCannotLinkAnAccount(String provider) {
        User legacy = account(9L, "legacy-user", Role.USER);
        legacy.setOauthId("42");
        when(users.findByOauthId("42")).thenReturn(Optional.of(legacy));

        OAuth2AuthenticationException error = assertThrows(OAuth2AuthenticationException.class,
                () -> {
                    if (provider.equals("google")) {
                        service.findOrCreateGoogleUser("42", "person@example.com", "Person");
                    } else {
                        service.findOrCreateGithubUser("42", "person");
                    }
                });

        assertEquals("account_link_required", error.getError().getErrorCode());
        assertEquals("42", legacy.getOauthId());
        verify(users, never()).save(any());
        verifyNoInteractions(passwords);
    }

    @Test
    void scopedGoogleAccountCanBackfillMissingGoogleIdAndEmail() {
        User existing = account(15L, "existing-name", Role.USER);
        existing.setOauthId("google:42");
        when(users.findByOauthId("google:42")).thenReturn(Optional.of(existing));

        User result = service.findOrCreateGoogleUser("42", "person@example.com", "Person");

        assertSame(existing, result);
        assertEquals("42", result.getGoogleId());
        assertEquals("person@example.com", result.getEmail());
        verify(users).save(existing);
    }

    @Test
    void conflictingScopedAccountDoesNotOverwriteAnotherGoogleIdentity() {
        User existing = account(15L, "existing-name", Role.USER);
        existing.setOauthId("google:42");
        existing.setGoogleId("different-sub");
        when(users.findByOauthId("google:42")).thenReturn(Optional.of(existing));

        OAuth2AuthenticationException error = assertThrows(OAuth2AuthenticationException.class,
                () -> service.findOrCreateGoogleUser("42", "person@example.com", "Person"));

        assertEquals("account_link_required", error.getError().getErrorCode());
        assertEquals("different-sub", existing.getGoogleId());
        verify(users, never()).save(any());
    }

    @Test
    void missingProviderIdCannotCreateAnAccount() {
        OAuth2AuthenticationException error = assertThrows(OAuth2AuthenticationException.class,
                () -> service.findOrCreateGoogleUser(null, "person@example.com", "Person"));

        assertEquals("invalid_user_info", error.getError().getErrorCode());
        verify(users, never()).findByGoogleId(any());
        verify(users, never()).save(any());
    }

    private User account(Long id, String username, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        return user;
    }
}