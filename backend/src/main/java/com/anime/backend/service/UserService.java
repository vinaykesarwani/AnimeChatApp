package com.anime.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
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

    public User createUser(UserCreateDto dto) {
        dto.setUsername(InputRules.text(dto.getUsername(), "Username", 3, 255));
        InputRules.password(dto.getPassword());
        if (userRepo.findByUsername(dto.getUsername()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(encoder.encode(dto.getPassword()));
        // Public registration must never grant a role supplied by the client.
        user.setRole(Role.USER);
        return userRepo.save(user);
    }

    public User updateUser(Long id, UserUpdateDto dto, String requester) {
        User user = userRepo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!isSelfOrAdmin(user, requester))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");

        if (dto.getPassword() != null) {
            InputRules.password(dto.getPassword());
            user.setPassword(encoder.encode(dto.getPassword()));
        }

        return userRepo.save(user);
    }

    public void deleteUser(Long id, String requester) {
        User user = userRepo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!isSelfOrAdmin(user, requester))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");

        userRepo.delete(user);
    }

    public User findOrCreateGoogleUser(String googleId, String email, String name) {
        String key = oauthKey("google", googleId);
        User existing = userRepo.findByGoogleId(googleId).orElse(null);
        if (existing != null) {
            // A matching googleId proves which provider owns this old, unscoped ID.
            if (googleId.equals(existing.getOauthId())) {
                ensureKeyAvailable(key, existing);
                existing.setOauthId(key);
                return userRepo.save(existing);
            }
            return existing;
        }

        User scoped = userRepo.findByOauthId(key).orElse(null);
        if (scoped != null) {
            if (scoped.getGoogleId() != null && !googleId.equals(scoped.getGoogleId())) {
                throw accountLinkRequired();
            }
            scoped.setGoogleId(googleId);
            if (scoped.getEmail() == null) scoped.setEmail(email);
            return userRepo.save(scoped);
        }

        rejectUnscopedAccount(googleId);
        User user = oauthUser(key, email != null ? email.split("@", 2)[0] : name);
        user.setGoogleId(googleId);
        user.setEmail(email);
        return userRepo.save(user);
    }

    public User findOrCreateGithubUser(String githubId, String login) {
        String key = oauthKey("github", githubId);
        User existing = userRepo.findByOauthId(key).orElse(null);
        if (existing != null) return existing;

        rejectUnscopedAccount(githubId);
        return userRepo.save(oauthUser(key, login));
    }

    private String oauthKey(String provider, String id) {
        if (id == null || id.isBlank() || id.length() > 240) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info"));
        }
        return provider + ":" + id;
    }

    private User oauthUser(String key, String usernameSeed) {
        String base = usernameSeed == null ? "user" : usernameSeed.replaceAll("[^a-zA-Z0-9_]", "_");
        if (base.isBlank()) base = "user";
        if (base.length() > 200) base = base.substring(0, 200);

        String username = base;
        int suffix = 2;
        while (userRepo.findByUsername(username).isPresent()) {
            username = base + "_" + suffix++;
        }

        User user = new User();
        user.setUsername(username);
        user.setRole(Role.USER);
        user.setOauthId(key);
        // Older schemas require a non-null password; empty values cannot authenticate.
        user.setPassword("");
        return user;
    }

    private void rejectUnscopedAccount(String id) {
        // Older oauth_id values had no provider. Do not guess and link the wrong account.
        if (userRepo.findByOauthId(id).isPresent()) throw accountLinkRequired();
    }

    private void ensureKeyAvailable(String key, User user) {
        userRepo.findByOauthId(key).ifPresent(other -> {
            if (!other.getId().equals(user.getId())) throw accountLinkRequired();
        });
    }

    private OAuth2AuthenticationException accountLinkRequired() {
        return new OAuth2AuthenticationException(new OAuth2Error("account_link_required"));
    }

    private boolean isSelfOrAdmin(User target, String requester) {
        User req = userRepo.findByUsername(requester).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        return req.getRole() == Role.ADMIN || target.getUsername().equals(requester);
    }
}
