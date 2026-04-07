package com.anime.backend.controller;

import java.security.Principal;

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

import com.anime.backend.dto.UserCreateDto;
import com.anime.backend.dto.UserUpdateDto;
import com.anime.backend.entity.Role;
import com.anime.backend.entity.User;
import com.anime.backend.repository.UserRepository;
import com.anime.backend.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepo;

    public UserController(UserService userService, UserRepository userRepo) {
        this.userService = userService;
        this.userRepo=userRepo;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) 
    public User create(@RequestBody UserCreateDto dto) {
        return userService.createUser(dto);
    }

    //SELF or ADMIN: update user
    @PutMapping("/{id}")
    public User update(
            @PathVariable Long id,
            @RequestBody UserUpdateDto dto,
            Principal principal) {
        return userService.updateUser(id, dto, principal.getName());
    }

    //SELF or ADMIN: delete user
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, Principal principal) {
        userService.deleteUser(id, principal.getName());
    }

    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id, Principal principal) {

        User loggedIn = userRepo.findByUsername(principal.getName())
            .orElseThrow();

        // self OR admin
        if (!loggedIn.getId().equals(id) &&
            loggedIn.getRole() != Role.ADMIN) {   // ✅ enum comparison
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        return userRepo.findById(id).orElseThrow();
    }

    @GetMapping("/self")
    public User getSelf(Principal principal) {
        return userRepo.findByUsername(principal.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
