package com.anime.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    // Nullable: Google-only users have no password
    @JsonIgnore
    @Column(nullable = true)
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    // --- OAuth2 fields (null for username/password users) ---

    /** Google's unique "sub" claim — stable even if the user changes their email. */
    @Column(unique = true, nullable = true)
    private String googleId;

    /** Stored for display and lookup convenience; not used for auth. */
    @Column(nullable = true)
    private String email;
}
