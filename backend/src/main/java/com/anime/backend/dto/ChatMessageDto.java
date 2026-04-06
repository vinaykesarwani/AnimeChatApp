package com.anime.backend.dto;

import lombok.Data;

@Data
public class ChatMessageDto {
    private String content;
    private Long discussionRoomId;
    private Long replyToMessageId;
    private String tempId;
}
