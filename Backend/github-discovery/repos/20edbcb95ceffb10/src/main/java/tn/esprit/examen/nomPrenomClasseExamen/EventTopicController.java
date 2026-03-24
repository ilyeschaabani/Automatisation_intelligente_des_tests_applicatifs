package tn.esprit.examen.nomPrenomClasseExamen;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.examen.nomPrenomClasseExamen.dto.StatisticsDTO;

import java.io.ByteArrayInputStream;
import java.util.List;

@RestController
@RequestMapping("/api/events-topics")
@RequiredArgsConstructor
public class EventTopicController {

    private final EventTopicService service;

    // Récupérer tous les événements et topics
    @GetMapping
    public ResponseEntity<List<EventTopic>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    // Récupérer un événement par ID
    @GetMapping("/{id}")
    public ResponseEntity<EventTopic> getById(@PathVariable Long id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Créer un nouvel événement et envoyer une notification immédiatement après
    @PostMapping
    public ResponseEntity<EventTopic> create(@RequestBody EventTopic eventTopic) {
        EventTopic savedEvent = service.create(eventTopic);
        // Appel à la fonction de notification après l'ajout d'un événement
        service.checkAndSendUpcomingEventNotifications(); // Appel ici
        return ResponseEntity.ok(savedEvent);
    }

    // Mettre à jour un événement existant
    @PutMapping("/{id}")
    public ResponseEntity<EventTopic> update(@PathVariable Long id, @RequestBody EventTopic eventTopic) {
        return ResponseEntity.ok(service.update(id, eventTopic));
    }

    // Supprimer un événement
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // Récupérer les événements à venir
    @GetMapping("/upcoming")
    public ResponseEntity<List<EventTopic>> getUpcomingEvents() {
        return ResponseEntity.ok(service.getUpcomingEvents());
    }

    // Notifier les événements à venir (système de notification)
    @GetMapping("/notify-upcoming")
    public ResponseEntity<Void> notifyUpcomingEvents() {
        service.checkAndSendUpcomingEventNotifications();
        return ResponseEntity.ok().build();
    }
    @GetMapping("/statistics")
    public ResponseEntity<StatisticsDTO> getStatistics() {
        return ResponseEntity.ok(service.getStatistics());
    }
    @GetMapping("/statistics/pdf")
    public ResponseEntity<InputStreamResource> downloadStatisticsPdf() {
        ByteArrayInputStream bis = service.generateStatisticsPdf();

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "inline; filename=statistics.pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(bis));
    }
    }



