package com.anime.backend.security;

import com.anime.backend.entity.Role;
import com.anime.backend.entity.User;
import com.anime.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OAuth2SuccessHandlerTest {
    private JwtUtil tokens;
    private UserService users;
    private OAuth2SuccessHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        tokens = mock(JwtUtil.class);
        users = mock(UserService.class);
        handler = new OAuth2SuccessHandler(tokens, users, "http://localhost:3000/");
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void googleLoginIssuesJwtForTheMappedAccountAndUsesCurrentCallback() throws Exception {
        when(users.findOrCreateGoogleUser("google-sub", "person@example.com", "Person")).thenReturn(account());
        when(tokens.generateToken("stored-user")).thenReturn("signed-token");

        handler.onAuthenticationSuccess(request, response, authentication("google",
                Map.of("sub", "google-sub", "email", "person@example.com", "name", "Person"), "sub"));

        assertEquals("http://localhost:3000/oauth2/callback?token=signed-token", response.getRedirectedUrl());
        verify(users).findOrCreateGoogleUser("google-sub", "person@example.com", "Person");
        verify(tokens).generateToken("stored-user");
    }

    @Test
    void githubUsesRegistrationIdAndNumericGithubIdRatherThanSubAttribute() throws Exception {
        when(users.findOrCreateGithubUser("42", "octocat")).thenReturn(account());
        when(tokens.generateToken("stored-user")).thenReturn("signed-token");

        handler.onAuthenticationSuccess(request, response, authentication("github",
                Map.of("id", 42L, "login", "octocat", "sub", "not-a-google-login"), "id"));

        assertEquals("http://localhost:3000/oauth2/callback?token=signed-token", response.getRedirectedUrl());
        verify(users).findOrCreateGithubUser("42", "octocat");
        verify(users, never()).findOrCreateGoogleUser(any(), any(), any());
    }

    @Test
    void ambiguousLegacyAccountRedirectsWithFixedErrorCodeAndNoJwt() throws Exception {
        when(users.findOrCreateGoogleUser("google-sub", "person@example.com", "Person"))
                .thenThrow(new OAuth2AuthenticationException(new OAuth2Error("account_link_required")));

        handler.onAuthenticationSuccess(request, response, authentication("google",
                Map.of("sub", "google-sub", "email", "person@example.com", "name", "Person"), "sub"));

        assertEquals("http://localhost:3000/oauth2/callback?error=account_link_required", response.getRedirectedUrl());
        verifyNoInteractions(tokens);
    }

    @Test
    void unsupportedProviderCannotCreateAccountOrToken() throws Exception {
        handler.onAuthenticationSuccess(request, response, authentication("other", Map.of("id", "42"), "id"));

        assertEquals("http://localhost:3000/oauth2/callback?error=oauth_login_failed", response.getRedirectedUrl());
        verifyNoInteractions(users, tokens);
    }

    private OAuth2AuthenticationToken authentication(String provider, Map<String, Object> attributes,
                                                      String nameAttribute) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var principal = new DefaultOAuth2User(authorities, attributes, nameAttribute);
        return new OAuth2AuthenticationToken(principal, authorities, provider);
    }

    private User account() {
        User user = new User();
        user.setId(7L);
        user.setUsername("stored-user");
        user.setRole(Role.USER);
        return user;
    }
}