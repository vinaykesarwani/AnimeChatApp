package com.anime.backend.dto;

import com.anime.backend.entity.Role;

import lombok.Data;

@Data
public class UserCreateDto {
    private String username;
    private String password;
    private Role role;
}
