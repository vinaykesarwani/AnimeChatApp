package com.anime.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.anime.backend.dto.DiscussionRoomCreateDto;
import com.anime.backend.dto.DiscussionRoomUpdateDto;
import com.anime.backend.entity.AnimeRoom;
import com.anime.backend.entity.DiscussionRoom;
import com.anime.backend.repository.AnimeRoomRepository;
import com.anime.backend.repository.DiscussionRoomRepository;
import com.anime.backend.repository.UserRepository;

@Service
@Transactional
public class DiscussionRoomService {

    private final DiscussionRoomRepository repo;
    private final AnimeRoomRepository animeRepo;
    private final UserRepository userRepo;

    public DiscussionRoomService(
            DiscussionRoomRepository repo,
            AnimeRoomRepository animeRepo,
            UserRepository userRepo) {
        this.repo = repo;
        this.animeRepo = animeRepo;
        this.userRepo = userRepo;
    }

    public DiscussionRoom create(DiscussionRoomCreateDto dto, String username) {
        dto.setTitle(InputRules.text(dto.getTitle(), "Title", 1, 255));
        if (dto.getAnimeRoomId() == null || dto.getAnimeRoomId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Anime room ID is required");
        }
        AnimeRoom anime = animeRepo.findById(dto.getAnimeRoomId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Anime room not found"));

        if (repo.existsByAnimeRoomAndTitle(anime, dto.getTitle()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Discussion already exists");

        DiscussionRoom room = new DiscussionRoom();
        room.setTitle(dto.getTitle());
        room.setAnimeRoom(anime);
        room.setCreatedBy(userRepo.findByUsername(username).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found")));

        return repo.save(room);
    }

    public List<DiscussionRoom> getAll() {
        return repo.findAll();
    }

    public DiscussionRoom update(Long id, DiscussionRoomUpdateDto dto) {
        dto.setTitle(InputRules.text(dto.getTitle(), "Title", 1, 255));
        DiscussionRoom room = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Discussion room not found"));

        room.setTitle(dto.getTitle());
        return repo.save(room);
    }

    public void delete(Long id) {
        DiscussionRoom room = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Discussion room not found"));

        repo.delete(room);
    }
}
