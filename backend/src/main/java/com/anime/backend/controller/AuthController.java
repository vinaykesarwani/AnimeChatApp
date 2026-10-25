package com.anime.backend.controller;

import com.anime.backend.entity.User;
import com.anime.backend.repository.UserRepository;
import com.anime.backend.security.JwtUtil;
import com.anime.backend.service.InputRules;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepo,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /** POST /api/auth/login  →  { token, user } */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String username = InputRules.text(body.get("username"), "Username", 3, 255);
        String password = body.get("password");
        InputRules.password(password);

        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (user.getPassword() == null || user.getPassword().isEmpty() ||
                !passwordEncoder.matches(password, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = jwtUtil.generateToken(user.getUsername());
        return Map.of("token", token, "user", user);
    }
}