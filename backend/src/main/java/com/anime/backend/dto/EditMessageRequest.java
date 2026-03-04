package com.anime.backend.dto;

import lombok.Data;

@Data
public class EditMessageRequest {
    private Long messageId;
    private String content;
}