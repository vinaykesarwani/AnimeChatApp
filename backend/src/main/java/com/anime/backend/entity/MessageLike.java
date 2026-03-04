package com.anime.backend.entity;
import jakarta.persistence.*;
import lombok.Data;
@Entity
@Table(
    name = "message_likes",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"message_id", "user_id"})
    }
)
@Data
public class MessageLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Message message;

    @ManyToOne
    private User user;
}
