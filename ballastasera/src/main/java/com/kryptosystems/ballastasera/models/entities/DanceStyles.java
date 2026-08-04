package com.kryptosystems.ballastasera.models.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "dance_styles")
@Getter
@Setter
public class DanceStyles {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @ManyToMany(mappedBy = "danceStyles")
    private Set<Events> events = new HashSet<>();
}
