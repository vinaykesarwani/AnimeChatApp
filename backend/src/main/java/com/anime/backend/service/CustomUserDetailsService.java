package com.anime.backend.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.anime.backend.entity.User;
import com.anime.backend.repository.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository repo;

    public CustomUserDetailsService(UserRepository repo) {
        this.repo = repo;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = repo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Google-only users have no password. We set a blank placeholder so
        // Spring's UserDetails builder doesn't throw — they can never log in
        // via Basic auth anyway because no raw password will ever match "".
        String password = user.getPassword() != null ? user.getPassword() : "";

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(password)
                .roles(user.getRole().name())
                .build();
    }
}