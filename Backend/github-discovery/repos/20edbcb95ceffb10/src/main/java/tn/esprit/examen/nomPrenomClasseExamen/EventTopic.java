package tn.esprit.examen.nomPrenomClasseExamen;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "events_topics")
@Data
@NoArgsConstructor
@AllArgsConstructor

public class EventTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String description;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime eventDate; // Peut être null si c'est un topic

    private boolean isEvent;

    private String location; // Peut être null si c'est un topic



}
