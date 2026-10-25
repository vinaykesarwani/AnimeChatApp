package com.anime.backend.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.anime.backend.entity.AnimeRoom;
import com.anime.backend.entity.User;
import com.anime.backend.repository.AnimeRoomRepository;
import com.anime.backend.repository.UserRepository;
import com.anime.backend.service.InputRules;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/anime-rooms")
public class AnimeRoomController {

    private final AnimeRoomRepository repo;
    private final UserRepository userRepo;

    public AnimeRoomController(AnimeRoomRepository repo, UserRepository userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    @GetMapping
    public List<AnimeRoom> listRooms() {
        return repo.findAll();
    }

    @PostMapping
    public AnimeRoom createRoom(@RequestParam String name, Principal principal) {
        name = InputRules.text(name, "Anime name", 1, 255);
        if (repo.existsByName(name))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Anime already exists");

        User user = userRepo.findByUsername(principal.getName()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        AnimeRoom room = new AnimeRoom();
        room.setName(name);
        room.setCreatedBy(user);
        return repo.save(room);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AnimeRoom updateRoom(@PathVariable Long id, @RequestParam String name) {
        name = InputRules.text(name, "Anime name", 1, 255);
        AnimeRoom room = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        room.setName(name);
        return repo.save(room);
    }

    // 🔹 Delete anime room (ADMIN only)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteRoom(@PathVariable Long id) {
        AnimeRoom room = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        repo.delete(room);
    }
}
