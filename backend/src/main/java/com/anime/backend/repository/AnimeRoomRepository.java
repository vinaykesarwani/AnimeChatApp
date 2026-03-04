package com.anime.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.anime.backend.entity.AnimeRoom;

public interface AnimeRoomRepository extends JpaRepository<AnimeRoom, Long> {
    boolean existsByName(String name);
}
