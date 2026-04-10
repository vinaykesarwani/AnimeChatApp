package com.anime.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class SimilarityRequestDto {
    private String candidate;
    private List<String> existingTitles;
}
