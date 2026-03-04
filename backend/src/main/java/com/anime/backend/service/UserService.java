package com.anime.backend.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(encoder.encode(dto.getPassword()));
        user.setRole(dto.getRole());
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

    private boolean isSelfOrAdmin(User target, String requester) {
        User req = userRepo.findByUsername(requester).orElseThrow();
        return req.getRole() == Role.ADMIN || target.getUsername().equals(requester);
    }
}
