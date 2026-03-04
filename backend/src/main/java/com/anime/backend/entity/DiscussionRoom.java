package com.anime.backend.entity;
import jakarta.persistence.*;
import lombok.Data;
@Entity
@Table(
    name = "discussion_rooms",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"anime_room_id", "title"})
    }
)
@Data
public class DiscussionRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @ManyToOne
    @JoinColumn(name = "anime_room_id", nullable = false)
    private AnimeRoom animeRoom;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;
}
