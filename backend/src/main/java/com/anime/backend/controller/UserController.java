package com.anime.backend.controller;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.anime.backend.config.JwtUtil;
import com.anime.backend.dto.UserCreateDto;
import com.anime.backend.dto.UserUpdateDto;
import com.anime.backend.entity.Role;
import com.anime.backend.entity.User;
import com.anime.backend.repository.UserRepository;
import com.anime.backend.service.UserService;

@RestController
public class UserController {

    private final UserService userService;
    private final UserRepository userRepo;
    private final JwtUtil jwtUtil;

    public UserController(UserService userService, UserRepository userRepo, JwtUtil jwtUtil) {
        this.userService = userService;
        this.userRepo = userRepo;
        this.jwtUtil = jwtUtil;
    }

    // ── Existing user endpoints ───────────────────────────────────────────────

    @PostMapping("/api/users")
    @ResponseStatus(HttpStatus.CREATED)
    public User create(@RequestBody UserCreateDto dto) {
        return userService.createUser(dto);
    }

    @PutMapping("/api/users/{id}")
    public User update(@PathVariable Long id,
                       @RequestBody UserUpdateDto dto,
                       Principal principal) {
        return userService.updateUser(id, dto, principal.getName());
    }

    @DeleteMapping("/api/users/{id}")
    public void delete(@PathVariable Long id, Principal principal) {
        userService.deleteUser(id, principal.getName());
    }

    @GetMapping("/api/users/{id}")
    public User getUserById(@PathVariable Long id, Principal principal) {
        User loggedIn = userRepo.findByUsername(principal.getName()).orElseThrow();
        if (!loggedIn.getId().equals(id) && loggedIn.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return userRepo.findById(id).orElseThrow();
    }

    @GetMapping("/api/users/self")
    public User getSelf(Principal principal) {
        return userRepo.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // ── New: JWT token exchange ───────────────────────────────────────────────

    /**
     * POST /api/auth/token
     *
     * Called by the frontend immediately after a successful username/password login.
     * The request must include a valid Basic Authorization header (Spring Security
     * enforces this via httpBasic() — by the time this method runs, the user is
     * already authenticated).
     *
     * Returns a JWT that the frontend can use for all subsequent requests,
     * including WebSocket STOMP connections, instead of sending the raw password.
     *
     * Example response: { "token": "eyJhbGci..." }
     */
    @PostMapping("/api/auth/token")
    public Map<String, String> issueToken(Principal principal) {
        String token = jwtUtil.generateToken(principal.getName());
        return Map.of("token", token);
    }
}
