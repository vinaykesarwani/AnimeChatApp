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

    private boolean isSelfOrAdmin(User target, String requester) {
        User req = userRepo.findByUsername(requester).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        return req.getRole() == Role.ADMIN || target.getUsername().equals(requester);
    }
}
