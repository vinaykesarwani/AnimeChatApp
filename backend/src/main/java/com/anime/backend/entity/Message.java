package com.anime.backend.entity;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Data;

@Entity 
@Table(name = "messages")
@Data
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2000)
    private String content;

    @ManyToOne
    @JoinColumn(name = "sender_id")
    private User sender;

    @ManyToOne
    @JoinColumn(name = "discussion_room_id")
    private DiscussionRoom discussionRoom;

    @ManyToOne
    @JoinColumn(name = "reply_to_message_id")
    private Message replyTo;

    private boolean deleted = false;

    private LocalDateTime createdAt = LocalDateTime.now();
}
