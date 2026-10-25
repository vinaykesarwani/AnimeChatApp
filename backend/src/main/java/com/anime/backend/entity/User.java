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

    // OAuth-only accounts do not have a local password.
    @JsonIgnore
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(unique = true)
    @JsonIgnore
    private String googleId;

    @JsonIgnore
    private String email;

    @Column(name = "oauth_id", unique = true)
    @JsonIgnore
    private String oauthId;
}