package com.anime.backend.entity;
import jakarta.persistence.*;
import lombok.Data;
@Entity
@Table(
    name = "anime_rooms",
    uniqueConstraints = @UniqueConstraint(columnNames = "name")
)
@Data
public class AnimeRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;
}
