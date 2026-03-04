package com.anime.backend.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.anime.backend.entity.Message;
import com.anime.backend.entity.Role;
import com.anime.backend.entity.User;
import com.anime.backend.repository.MessageRepository;
import com.anime.backend.repository.UserRepository;
import com.anime.backend.service.MessageService;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;
    private final MessageRepository messageRepo;
    private final UserRepository userRepo;


    public MessageController(MessageService messageService, MessageRepository messageRepo, UserRepository userRepo) {
        this.messageService = messageService;
        this.messageRepo = messageRepo;
        this.userRepo=userRepo;
    }

    @GetMapping("/discussion/{roomId}")
    public List<Message> getMessages(@PathVariable Long roomId) {
        return messageRepo.findByDiscussionRoomIdOrderByCreatedAtAsc(roomId);
    }

    @PutMapping("/{id}")
    public Message updateMessage(
            @PathVariable Long id,
            @RequestBody String newContent,
            Principal principal
    ) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        // Fetch full User entity from DB
        User user = userRepo.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        // Fetch the message
        Message msg = messageRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        // Only Admin or Owner can edit
        if (user.getRole() != Role.ADMIN && !msg.getSender().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }

        // Update content
        msg.setContent(newContent);
        return messageRepo.save(msg);
    }


    @DeleteMapping("/{id}")
    public void deleteMessage(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        User user = userRepo.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        Message msg = messageRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        // Admin OR owner
        if (user.getRole() != Role.ADMIN && !msg.getSender().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }

        messageRepo.delete(msg);
    }


    // Like message
    @PostMapping("/{id}/like")
    public ResponseEntity<?> likeMessage(
            @PathVariable Long id,
            Principal principal) {

        messageService.likeMessage(id, principal.getName());
        return ResponseEntity.ok().build();
    }

    // Unlike
    @DeleteMapping("/{id}/like")
    public ResponseEntity<?> unlikeMessage(
            @PathVariable Long id,
            Principal principal) {

        messageService.unlikeMessage(id, principal.getName());
        return ResponseEntity.ok().build();
    }
}
