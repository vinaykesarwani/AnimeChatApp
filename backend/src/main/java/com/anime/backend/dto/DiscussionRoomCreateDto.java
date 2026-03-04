package com.anime.backend.dto;

import lombok.Data;

@Data
public class DiscussionRoomCreateDto {
    private Long animeRoomId;
    private String title;
}
