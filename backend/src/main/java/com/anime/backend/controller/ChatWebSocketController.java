package com.anime.backend.controller;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import com.anime.backend.service.InputRules;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import com.anime.backend.dto.ChatMessageDto;
import com.anime.backend.dto.DeleteMessageRequest;
import com.anime.backend.dto.EditMessageRequest;
import com.anime.backend.dto.MessageEvent;
import com.anime.backend.entity.Message;
import com.anime.backend.entity.Role;
import com.anime.backend.entity.User;
import com.anime.backend.repository.DiscussionRoomRepository;
import com.anime.backend.repository.MessageRepository;
import com.anime.backend.repository.UserRepository;

@Controller
public class ChatWebSocketController {

    private final MessageRepository messageRepo;
    private final DiscussionRoomRepository roomRepo;
    private final UserRepository userRepo;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatWebSocketController(
            MessageRepository messageRepo,
            DiscussionRoomRepository roomRepo,
            UserRepository userRepo,
            SimpMessagingTemplate messagingTemplate) {
        this.messageRepo = messageRepo;
        this.roomRepo = roomRepo;
        this.userRepo = userRepo;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.send/{roomId}")
    public void send(
            @DestinationVariable Long roomId,
            ChatMessageDto dto,
            Principal principal) {

        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Message msg = new Message();
        msg.setContent(InputRules.text(dto.getContent(), "Message", 1, 2000));
        msg.setSender(userRepo.findByUsername(principal.getName()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found")));
        msg.setDiscussionRoom(roomRepo.findById(roomId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Discussion room not found")));

        if (dto.getReplyToMessageId() != null) {
            Message reply = messageRepo.findById(dto.getReplyToMessageId()).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Reply message not found"));
            if (!reply.getDiscussionRoom().getId().equals(roomId) || reply.isDeleted()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Reply must reference an active message in the same room");
            }
            msg.setReplyTo(reply);
        }

        Message saved = messageRepo.save(msg);
        saved.setTempId(dto.getTempId()); 
        messagingTemplate.convertAndSend(
            "/topic/chat/" + roomId,
            new MessageEvent("CREATE", saved, null, System.currentTimeMillis())
        );
    }

    @MessageMapping("/chat.edit")
    public void edit(EditMessageRequest dto, Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (dto.getMessageId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message ID is required");
        }
        User user = userRepo.findByUsername(principal.getName()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        Message msg = messageRepo.findById(dto.getMessageId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        // FIX: admin OR owner can edit
        if (user.getRole() != Role.ADMIN && !msg.getSender().getUsername().equals(principal.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the author or an administrator can change this message");
        }

        if (msg.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Deleted messages cannot be edited");
        }
        msg.setContent(InputRules.text(dto.getContent(), "Message", 1, 2000));
        Message updated = messageRepo.save(msg);

        messagingTemplate.convertAndSend(
            "/topic/chat/" + msg.getDiscussionRoom().getId(),
            new MessageEvent("EDIT", updated, null, System.currentTimeMillis())
        );
    }

    @MessageMapping("/chat.delete")
    public void delete(DeleteMessageRequest dto, Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (dto.getMessageId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message ID is required");
        }
        User user = userRepo.findByUsername(principal.getName()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        Message msg = messageRepo.findById(dto.getMessageId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        // FIX: admin OR owner can delete
        if (user.getRole() != Role.ADMIN && !msg.getSender().getUsername().equals(principal.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the author or an administrator can change this message");
        }

        Long roomId = msg.getDiscussionRoom().getId();
        if (msg.isDeleted()) return;
        msg.setDeleted(true);
        msg.setContent("[deleted]");
        messageRepo.save(msg);

        messagingTemplate.convertAndSend(
            "/topic/chat/" + roomId,
            new MessageEvent("DELETE", msg, dto.getMessageId(), System.currentTimeMillis())
        );
    }

    @MessageExceptionHandler
    @SendToUser(value = "/queue/errors", broadcast = false)
    public Map<String, String> error(Exception error) {
        String detail = error instanceof ResponseStatusException status
                ? status.getReason() : "Message operation failed. Please try again.";
        return Map.of("message", detail == null ? "Message operation failed" : detail);
    }
}
