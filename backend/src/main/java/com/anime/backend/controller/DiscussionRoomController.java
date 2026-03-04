package com.anime.backend.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.anime.backend.dto.DiscussionRoomCreateDto;
import com.anime.backend.dto.DiscussionRoomUpdateDto;
import com.anime.backend.entity.DiscussionRoom;
import com.anime.backend.service.DiscussionRoomService;

@RestController
@RequestMapping("/api/discussion-rooms")
public class DiscussionRoomController {

    private final DiscussionRoomService service;

    public DiscussionRoomController(DiscussionRoomService service) {
        this.service = service;
    }

    //Create discussion room
    @PostMapping
    public DiscussionRoom create(@RequestBody DiscussionRoomCreateDto dto, Principal principal) {
        return service.create(dto, principal.getName());
    }

    //Get all discussion rooms
    @GetMapping
    public List<DiscussionRoom> getAll() {
        return service.getAll();
    }

    //Modify discussion room (Admin only)
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DiscussionRoom update(@PathVariable Long id, @RequestBody DiscussionRoomUpdateDto dto) {
        return service.update(id, dto);
    }

    //Delete discussion room (Admin only)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}

