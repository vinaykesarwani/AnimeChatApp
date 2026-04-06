package com.anime.backend.controller;

import java.security.Principal;
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

        Message msg = new Message();
        msg.setContent(dto.getContent());
        msg.setSender(userRepo.findByUsername(principal.getName()).orElseThrow());
        msg.setDiscussionRoom(roomRepo.findById(roomId).orElseThrow());

        if (dto.getReplyToMessageId() != null) {
            msg.setReplyTo(messageRepo.findById(dto.getReplyToMessageId()).orElse(null));
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
        User user = userRepo.findByUsername(principal.getName()).orElseThrow();
        Message msg = messageRepo.findById(dto.getMessageId()).orElseThrow();

        // FIX: admin OR owner can edit
        if (user.getRole() != Role.ADMIN && !msg.getSender().getUsername().equals(principal.getName())) {
            return;
        }

        msg.setContent(dto.getContent());
        Message updated = messageRepo.save(msg);

        messagingTemplate.convertAndSend(
            "/topic/chat/" + msg.getDiscussionRoom().getId(),
            new MessageEvent("EDIT", updated, null, System.currentTimeMillis())
        );
    }

    @MessageMapping("/chat.delete")
    public void delete(DeleteMessageRequest dto, Principal principal) {
        User user = userRepo.findByUsername(principal.getName()).orElseThrow();
        Message msg = messageRepo.findById(dto.getMessageId()).orElseThrow();

        // FIX: admin OR owner can delete
        if (user.getRole() != Role.ADMIN && !msg.getSender().getUsername().equals(principal.getName())) {
            return;
        }

        Long roomId = msg.getDiscussionRoom().getId();
        messageRepo.delete(msg);

        messagingTemplate.convertAndSend(
            "/topic/chat/" + roomId,
            new MessageEvent("DELETE", null, dto.getMessageId(), System.currentTimeMillis())
        );
    }
}