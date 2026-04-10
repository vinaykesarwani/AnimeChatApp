package com.anime.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.anime.backend.entity.AnimeRoom;
import com.anime.backend.entity.DiscussionRoom;

import java.util.List;

public interface DiscussionRoomRepository extends JpaRepository<DiscussionRoom, Long> {
    boolean existsByAnimeRoomAndTitle(AnimeRoom animeRoom, String title);

    @Query("SELECT d FROM DiscussionRoom d JOIN FETCH d.animeRoom ar JOIN FETCH d.createdBy WHERE ar.id = :animeRoomId")
    List<DiscussionRoom> findByAnimeRoomId(@Param("animeRoomId") Long animeRoomId);
}
