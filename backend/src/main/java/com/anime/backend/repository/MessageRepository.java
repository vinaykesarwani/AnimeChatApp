package com.anime.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.anime.backend.entity.Message;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByDiscussionRoomId(Long discussionRoomId);
    List<Message> findByDiscussionRoomIdOrderByCreatedAtAsc(Long discussionRoomId);
}
