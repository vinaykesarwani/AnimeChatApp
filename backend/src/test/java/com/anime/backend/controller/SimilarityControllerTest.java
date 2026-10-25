package com.anime.backend.controller;

import com.anime.backend.dto.SimilarityRequestDto;
import com.anime.backend.dto.SimilarityResponseDto;
import com.anime.backend.entity.AnimeRoom;
import com.anime.backend.entity.DiscussionRoom;
import com.anime.backend.repository.AnimeRoomRepository;
import com.anime.backend.repository.DiscussionRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimilarityControllerTest {
    private SimilarityController controller;
    private RestTemplate pythonClient;
    private DiscussionRoom room;
    private SimilarityController.CheckRequest request;

    @BeforeEach
    void setUp() {
        DiscussionRoomRepository rooms = mock(DiscussionRoomRepository.class);
        AnimeRoomRepository anime = mock(AnimeRoomRepository.class);
        pythonClient = mock(RestTemplate.class);
        controller = new SimilarityController(rooms, anime);

        // The controller creates its own HTTP client. Replace it only in this test.
        ReflectionTestUtils.setField(controller, "restTemplate", pythonClient);
        ReflectionTestUtils.setField(controller, "similarityServiceUrl", "http://similarity.test");

        room = new DiscussionRoom();
        room.setId(2L);
        room.setTitle("Existing title");
        when(anime.findById(1L)).thenReturn(Optional.of(new AnimeRoom()));
        when(rooms.findByAnimeRoomId(1L)).thenReturn(List.of(room));

        request = new SimilarityController.CheckRequest();
        request.setAnimeRoomId(1L);
        request.setCandidate("New title");
    }

    @Test
    void similarityResultIncludesTheMatchingRoomAndScore() {
        SimilarityResponseDto.SimilarityResult match = new SimilarityResponseDto.SimilarityResult();
        match.setTitle("Existing title");
        match.setScore(0.91);
        SimilarityResponseDto pythonResponse = new SimilarityResponseDto();
        pythonResponse.setResults(List.of(match));
        when(pythonClient.postForObject(eq("http://similarity.test/similarity"),
                any(SimilarityRequestDto.class), eq(SimilarityResponseDto.class)))
                .thenReturn(pythonResponse);

        SimilarityController.CheckResponse response = controller.check(request);

        assertEquals(1, response.getResults().size());
        assertSame(room, response.getResults().getFirst().getRoom());
        assertEquals(0.91, response.getResults().getFirst().getScore());
        ArgumentCaptor<SimilarityRequestDto> sentRequest = ArgumentCaptor.forClass(SimilarityRequestDto.class);
        verify(pythonClient).postForObject(eq("http://similarity.test/similarity"),
                sentRequest.capture(), eq(SimilarityResponseDto.class));
        assertEquals("New title", sentRequest.getValue().getCandidate());
        assertEquals(List.of("Existing title"), sentRequest.getValue().getExistingTitles());
    }

    @Test
    void pythonFailureReturnsServiceUnavailable() {
        when(pythonClient.postForObject(eq("http://similarity.test/similarity"),
                any(SimilarityRequestDto.class), eq(SimilarityResponseDto.class)))
                .thenThrow(new RestClientException("Python service unavailable"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.check(request));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }
}
