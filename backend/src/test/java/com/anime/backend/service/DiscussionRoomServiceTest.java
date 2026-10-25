package com.anime.backend.service;

import com.anime.backend.dto.DiscussionRoomCreateDto;
import com.anime.backend.entity.AnimeRoom;
import com.anime.backend.entity.DiscussionRoom;
import com.anime.backend.entity.User;
import com.anime.backend.repository.AnimeRoomRepository;
import com.anime.backend.repository.DiscussionRoomRepository;
import com.anime.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiscussionRoomServiceTest {
    private DiscussionRoomRepository rooms;
    private AnimeRoomRepository anime;
    private UserRepository users;
    private DiscussionRoomService service;
    private AnimeRoom parent;

    @BeforeEach
    void setUp() {
        rooms = mock(DiscussionRoomRepository.class);
        anime = mock(AnimeRoomRepository.class);
        users = mock(UserRepository.class);
        service = new DiscussionRoomService(rooms, anime, users);
        parent = new AnimeRoom();
        parent.setId(1L);
    }

    @Test
    void createsRoomWithTrimmedTitleParentAndCreator() {
        // Arrange: external dependencies return the parent and signed-in user.
        User creator = new User();
        creator.setUsername("alice");
        when(anime.findById(1L)).thenReturn(Optional.of(parent));
        when(users.findByUsername("alice")).thenReturn(Optional.of(creator));

        // Act: exercise the real service method.
        service.create(request("  Episode theories  "), "alice");

        // Assert: check what would actually be stored.
        ArgumentCaptor<DiscussionRoom> savedRoom = ArgumentCaptor.forClass(DiscussionRoom.class);
        verify(rooms).save(savedRoom.capture());
        assertEquals("Episode theories", savedRoom.getValue().getTitle());
        assertSame(parent, savedRoom.getValue().getAnimeRoom());
        assertSame(creator, savedRoom.getValue().getCreatedBy());
    }

    @Test
    void duplicateTitleInTheSameAnimeReturnsConflictWithoutSaving() {
        when(anime.findById(1L)).thenReturn(Optional.of(parent));
        when(rooms.existsByAnimeRoomAndTitle(parent, "Episode theories")).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create(request("  Episode theories  "), "alice"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(rooms, never()).save(any(DiscussionRoom.class));
        verifyNoInteractions(users);
    }

    private DiscussionRoomCreateDto request(String title) {
        DiscussionRoomCreateDto request = new DiscussionRoomCreateDto();
        request.setAnimeRoomId(1L);
        request.setTitle(title);
        return request;
    }
}
