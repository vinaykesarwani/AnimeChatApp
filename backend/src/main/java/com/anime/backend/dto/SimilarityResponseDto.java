package com.anime.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class SimilarityResponseDto {

    private List<SimilarityResult> results;

    @Data
    public static class SimilarityResult {
        private String title;
        private double score;
    }
}
