package com.anime.backend.dto;
import com.anime.backend.entity.Message;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class MessageEvent {
    private String type; // CREATE, EDIT, DELETE
    private Message message;
    private Long messageId;
    private Long sentAt; 
}