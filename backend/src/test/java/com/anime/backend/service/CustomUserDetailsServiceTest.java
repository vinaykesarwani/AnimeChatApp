package com.anime.backend.service;

import com.anime.backend.entity.Role;
import com.anime.backend.entity.User;
import com.anime.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomUserDetailsServiceTest {
    @Test
    void googleOnlyAccountWithNullPasswordCanBeLoadedForJwtAuthentication() {
        UserRepository users = mock(UserRepository.class);
        User google = new User();
        google.setUsername("google-user");
        google.setRole(Role.USER);
        google.setGoogleId("google-sub");
        when(users.findByUsername("google-user")).thenReturn(Optional.of(google));

        UserDetails details = new CustomUserDetailsService(users).loadUserByUsername("google-user");

        assertEquals("google-user", details.getUsername());
        assertEquals("", details.getPassword());
        assertTrue(details.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_USER")));
        assertNull(google.getPassword());
    }

    @Test
    void passwordAccountKeepsItsStoredHashAndAuthorities() {
        UserRepository users = mock(UserRepository.class);
        User local = new User();
        local.setUsername("local-user");
        local.setRole(Role.ADMIN);
        local.setPassword("stored-hash");
        when(users.findByUsername("local-user")).thenReturn(Optional.of(local));

        UserDetails details = new CustomUserDetailsService(users).loadUserByUsername("local-user");

        assertEquals("stored-hash", details.getPassword());
        assertTrue(details.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_ADMIN")));
    }
}