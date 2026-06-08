package com.anime.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.anime.backend.entity.AnimeRoom;
import com.anime.backend.entity.DiscussionRoom;

public interface DiscussionRoomRepository extends JpaRepository<DiscussionRoom, Long> {
    boolean existsByAnimeRoomAndTitle(AnimeRoom animeRoom, String title);
}
