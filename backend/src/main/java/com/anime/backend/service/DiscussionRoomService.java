package com.anime.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        AnimeRoom anime = animeRepo.findById(dto.getAnimeRoomId())
                .orElseThrow(() -> new RuntimeException("Anime room not found"));

        if (repo.existsByAnimeRoomAndTitle(anime, dto.getTitle()))
            throw new RuntimeException("Discussion already exists");

        DiscussionRoom room = new DiscussionRoom();
        room.setTitle(dto.getTitle());
        room.setAnimeRoom(anime);
        room.setCreatedBy(userRepo.findByUsername(username).orElseThrow());

        return repo.save(room);
    }

    public List<DiscussionRoom> getAll() {
        return repo.findAll();
    }

    public DiscussionRoom update(Long id, DiscussionRoomUpdateDto dto) {
        DiscussionRoom room = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Discussion room not found"));

        room.setTitle(dto.getTitle());
        return repo.save(room);
    }

    public void delete(Long id) {
        DiscussionRoom room = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Discussion room not found"));

        repo.delete(room);
    }
}
