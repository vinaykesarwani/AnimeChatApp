package com.anime.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.anime.backend.entity.Role;

import com.anime.backend.entity.Message;
import com.anime.backend.entity.MessageLike;
import com.anime.backend.entity.User;
import com.anime.backend.repository.MessageLikeRepository;
import com.anime.backend.repository.MessageRepository;
import com.anime.backend.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional
public class MessageService {

    private final MessageRepository messageRepo;
    private final MessageLikeRepository likeRepo;
    private final UserRepository userRepo;

    public MessageService(
            MessageRepository messageRepo,
            MessageLikeRepository likeRepo,
            UserRepository userRepo) {
        this.messageRepo = messageRepo;
        this.likeRepo = likeRepo;
        this.userRepo = userRepo;
    }

    // 🔴 Delete message (owner or admin)
    public void deleteMessage(Long messageId, String username) {
        Message message = messageRepo.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (user.getRole() != Role.ADMIN && !message.getSender().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }

        message.setDeleted(true);
        message.setContent("[deleted]");
        messageRepo.save(message);
    }

    // ❤️ Like message
    public void likeMessage(Long messageId, String username) {
        Message message = messageRepo.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (message.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Message is deleted");
        }
        if (likeRepo.existsByMessageAndUser(message, user)) {
            return; // already liked → idempotent
        }

        MessageLike like = new MessageLike();
        like.setMessage(message);
        like.setUser(user);

        likeRepo.save(like);
    }

    // 💔 Unlike (optional but recommended)
    public void unlikeMessage(Long messageId, String username) {
        Message message = messageRepo.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        likeRepo.findByMessageAndUser(message, user)
            .ifPresent(likeRepo::delete);
    }
}
