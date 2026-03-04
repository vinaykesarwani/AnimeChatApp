package com.anime.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.anime.backend.entity.Message;
import com.anime.backend.entity.MessageLike;
import com.anime.backend.entity.User;

public interface MessageLikeRepository extends JpaRepository<MessageLike, Long> {
    boolean existsByMessageAndUser(Message message, User user);
    Optional<MessageLike> findByMessageAndUser(Message message, User user);
}
