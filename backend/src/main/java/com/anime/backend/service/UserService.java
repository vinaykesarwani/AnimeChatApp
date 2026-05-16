package com.anime.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.anime.backend.dto.UserCreateDto;
import com.anime.backend.dto.UserUpdateDto;
import com.anime.backend.entity.Role;
import com.anime.backend.entity.User;
import com.anime.backend.repository.UserRepository;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;

    public UserService(UserRepository userRepo, PasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.encoder = encoder;
    }

    // ── Existing: username/password registration ──────────────────────────────

    public User createUser(UserCreateDto dto) {
        if (userRepo.findByUsername(dto.getUsername()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(encoder.encode(dto.getPassword()));
        user.setRole(dto.getRole() != null ? dto.getRole() : Role.USER);
        return userRepo.save(user);
    }

    public User updateUser(Long id, UserUpdateDto dto, String requester) {
        User user = userRepo.findById(id).orElseThrow();

        if (!isSelfOrAdmin(user, requester))
            throw new RuntimeException("Not allowed");

        if (dto.getPassword() != null)
            user.setPassword(encoder.encode(dto.getPassword()));

        return userRepo.save(user);
    }

    public void deleteUser(Long id, String requester) {
        User user = userRepo.findById(id).orElseThrow();

        if (!isSelfOrAdmin(user, requester))
            throw new RuntimeException("Not allowed");

        userRepo.delete(user);
    }

    // ── New: Google OAuth2 ────────────────────────────────────────────────────

    /**
     * Called after a successful Google login.
     * Looks up the user by their stable Google ID.
     * Creates a new account automatically on first login.
     *
     * Username derivation:
     *   - Use the part of the email before "@" as the base (e.g. "john.doe")
     *   - If that username is already taken, append a number until unique
     */
    public User findOrCreateGoogleUser(String googleId, String email, String name) {
        // Returning user — fast path
        return userRepo.findByGoogleId(googleId).orElseGet(() -> {
            User user = new User();
            user.setGoogleId(googleId);
            user.setEmail(email);
            user.setRole(Role.USER);

            // Derive a username from their email prefix
            String base = email != null
                    ? email.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "_")
                    : (name != null ? name.replaceAll("\\s+", "_") : "user");

            user.setUsername(uniqueUsername(base));
            // No password — Google users authenticate via OAuth, not Basic auth
            return userRepo.save(user);
        });
    }

    /** Appends _2, _3, … until the username is unique. */
    private String uniqueUsername(String base) {
        if (userRepo.findByUsername(base).isEmpty()) return base;
        int i = 2;
        while (userRepo.findByUsername(base + "_" + i).isPresent()) i++;
        return base + "_" + i;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isSelfOrAdmin(User target, String requester) {
        User req = userRepo.findByUsername(requester).orElseThrow();
        return req.getRole() == Role.ADMIN || target.getUsername().equals(requester);
    }
}
