package com.anime.backend.controller;

import com.anime.backend.dto.SimilarityRequestDto;
import com.anime.backend.dto.SimilarityResponseDto;
import com.anime.backend.entity.DiscussionRoom;
import com.anime.backend.repository.AnimeRoomRepository;
import com.anime.backend.repository.DiscussionRoomRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.anime.backend.service.InputRules;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/similarity")
public class SimilarityController {

    private final DiscussionRoomRepository discussionRoomRepo;
    private final AnimeRoomRepository animeRoomRepo;
    private final RestTemplate restTemplate;

    @Value("${similarity.service.url:http://localhost:8000}")
    private String similarityServiceUrl;

    public SimilarityController(
            DiscussionRoomRepository discussionRoomRepo,
            AnimeRoomRepository animeRoomRepo) {
        this.discussionRoomRepo = discussionRoomRepo;
        this.animeRoomRepo = animeRoomRepo;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    @PostMapping("/check")
    public CheckResponse check(@RequestBody CheckRequest req) {
        req.setCandidate(InputRules.text(req.getCandidate(), "Title", 1, 255));
        if (req.getAnimeRoomId() == null || req.getAnimeRoomId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Anime room ID is required");
        }
        // Verify anime exists
        animeRoomRepo.findById(req.getAnimeRoomId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Anime room not found"));

        // Use JOIN FETCH query — avoids lazy loading issues entirely
        List<DiscussionRoom> rooms = discussionRoomRepo.findByAnimeRoomId(req.getAnimeRoomId());

        if (rooms.isEmpty()) {
            return new CheckResponse(List.of());
        }

        // title → room map for enrichment
        Map<String, DiscussionRoom> titleToRoom = rooms.stream()
                .collect(Collectors.toMap(
                        DiscussionRoom::getTitle,
                        r -> r,
                        (a, b) -> a
                ));

        // Call Python similarity service
        SimilarityRequestDto pyReq = new SimilarityRequestDto();
        pyReq.setCandidate(req.getCandidate());
        pyReq.setExistingTitles(rooms.stream().map(DiscussionRoom::getTitle).collect(Collectors.toList()));

        SimilarityResponseDto pyRes;
        try {
            pyRes = restTemplate.postForObject(
                similarityServiceUrl + "/similarity",
                pyReq,
                SimilarityResponseDto.class
            );
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Similarity suggestions are temporarily unavailable");
        }

        if (pyRes == null || pyRes.getResults() == null
                || pyRes.getResults().stream().anyMatch(result -> result == null)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Similarity service returned an invalid response");
        }

        // Enrich with full DiscussionRoom objects
        List<ScoredRoom> scored = pyRes.getResults().stream()
                .filter(r -> titleToRoom.containsKey(r.getTitle()))
                .map(r -> new ScoredRoom(titleToRoom.get(r.getTitle()), r.getScore()))
                .collect(Collectors.toList());

        return new CheckResponse(scored);
    }

    // ── Inner request/response classes ──

    public static class CheckRequest {
        private String candidate;
        private Long animeRoomId;

        public String getCandidate() { return candidate; }
        public void setCandidate(String candidate) { this.candidate = candidate; }
        public Long getAnimeRoomId() { return animeRoomId; }
        public void setAnimeRoomId(Long animeRoomId) { this.animeRoomId = animeRoomId; }
    }

    public static class ScoredRoom {
        private DiscussionRoom room;
        private double score;

        public ScoredRoom(DiscussionRoom room, double score) {
            this.room = room;
            this.score = score;
        }

        public DiscussionRoom getRoom() { return room; }
        public double getScore() { return score; }
    }

    public static class CheckResponse {
        private List<ScoredRoom> results;

        public CheckResponse(List<ScoredRoom> results) { this.results = results; }
        public List<ScoredRoom> getResults() { return results; }
    }
}
