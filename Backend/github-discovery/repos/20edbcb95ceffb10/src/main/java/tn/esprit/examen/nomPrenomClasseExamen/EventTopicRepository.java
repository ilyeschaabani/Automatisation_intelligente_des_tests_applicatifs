package tn.esprit.examen.nomPrenomClasseExamen;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;



public interface EventTopicRepository extends JpaRepository<EventTopic, Long> {

    // Pour récupérer tous les events ou topics
    List<EventTopic> findByIsEvent(boolean isEvent);

    // Pour récupérer uniquement les events à venir
    List<EventTopic> findByIsEventTrueAndEventDateAfter(LocalDateTime date);
}
