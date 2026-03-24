package tn.esprit.examen.nomPrenomClasseExamen;

import com.lowagie.text.*;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import tn.esprit.examen.nomPrenomClasseExamen.dto.StatisticsDTO;



import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.awt.Color;

import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;



@Service
@RequiredArgsConstructor
public class EventTopicService {
    private final EventTopicRepository repository;

    public List<EventTopic> getAll() {
        return repository.findAll();
    }

    public Optional<EventTopic> getById(Long id) {
        return repository.findById(id);
    }

    public EventTopic create(EventTopic eventTopic) throws IllegalArgumentException {
        if (eventTopic.isEvent() && (eventTopic.getLocation() == null || eventTopic.getEventDate() == null)) {
            throw new IllegalArgumentException("L'événement doit avoir un lieu et une date valides");
        }

        EventTopic savedEvent = repository.save(eventTopic);
        checkAndSendUpcomingEventNotifications();
        return savedEvent;
    }

    public EventTopic update(Long id, EventTopic updatedEventTopic) {
        return repository.findById(id)
                .map(existingEvent -> {
                    if (updatedEventTopic.getTitle() != null) {
                        existingEvent.setTitle(updatedEventTopic.getTitle());
                    }
                    if (updatedEventTopic.getDescription() != null) {
                        existingEvent.setDescription(updatedEventTopic.getDescription());
                    }
                    if (updatedEventTopic.getEventDate() != null) {
                        existingEvent.setEventDate(updatedEventTopic.getEventDate());
                    }
                    existingEvent.setEvent(updatedEventTopic.isEvent());
                    if (updatedEventTopic.getLocation() != null) {
                        existingEvent.setLocation(updatedEventTopic.getLocation());
                    }
                    return repository.save(existingEvent);
                })
                .orElseThrow(() -> new RuntimeException("EventTopic non trouvé avec l'id: " + id));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public List<EventTopic> getUpcomingEvents() {
        return repository.findByIsEventTrueAndEventDateAfter(LocalDateTime.now());
    }

    public void checkAndSendUpcomingEventNotifications() {
        List<EventTopic> upcomingEvents = repository.findByIsEventTrueAndEventDateAfter(LocalDateTime.now());

        System.out.println("📦 Événements à venir : " + upcomingEvents.size());

        for (EventTopic event : upcomingEvents) {
            Duration duration = Duration.between(LocalDateTime.now(), event.getEventDate());
            long hoursUntilEvent = duration.toHours();

            System.out.println("🔍 " + event.getTitle() + " dans " + hoursUntilEvent + "h");

            if (hoursUntilEvent <= 24) {
                System.out.println("🔔 Notification : L'événement \"" + event.getTitle() +
                        "\" aura lieu bientôt à " + event.getLocation() +
                        " le " + event.getEventDate());
            }
        }

        }

    public StatisticsDTO getStatistics() {
        List<EventTopic> all = repository.findAll();
        int total = all.size();
        int events = (int) all.stream().filter(EventTopic::isEvent).count();
        int topics = total - events;

        return new StatisticsDTO(total, events, topics);
    }
    public ByteArrayInputStream generateStatisticsPdf() {
        StatisticsDTO stats = getStatistics();

        Document document = new Document();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Titre
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLUE);
            Paragraph title = new Paragraph("📊 Event & Topic Statistics", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(Chunk.NEWLINE);

            // Contenu
            Font contentFont = FontFactory.getFont(FontFactory.HELVETICA, 12, Color.DARK_GRAY);

            document.add(new Paragraph("🔢 Total Items: " + stats.getTotal(), contentFont));
            document.add(new Paragraph("📅 Events: " + stats.getEvents(), contentFont));
            document.add(new Paragraph("💬 Topics: " + stats.getTopics(), contentFont));

            document.close();
        } catch (DocumentException e) {
            throw new RuntimeException("Erreur lors de la génération du PDF", e);
        }

        return new ByteArrayInputStream(out.toByteArray());
    }
}


