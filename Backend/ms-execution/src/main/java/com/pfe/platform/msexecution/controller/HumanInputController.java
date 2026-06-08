package com.pfe.platform.msexecution.controller;

import com.pfe.platform.msexecution.service.HumanInputService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoint that lets the human tester provide an answer
 * when the AI pauses and asks for information (NEEDS_INPUT).
 *
 * POST /api/functional-evaluation/{id}/respond
 * Body: { "answer": "admin / password123" }
 */
@RestController
@RequestMapping("/api/functional-evaluation")
@RequiredArgsConstructor
@Slf4j
public class HumanInputController {

    private final HumanInputService humanInputService;

    @PostMapping("/{id}/respond")
    public ResponseEntity<Map<String, Object>> respond(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String answer = body.getOrDefault("answer", "").trim();
        if (answer.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Le champ 'answer' est requis"));
        }

        if (!humanInputService.isPaused(id)) {
            return ResponseEntity.ok(Map.of(
                    "accepted", false,
                    "message", "L'évaluation n'est pas en attente d'une réponse humaine"
            ));
        }

        boolean accepted = humanInputService.provideInput(id, answer);
        return ResponseEntity.ok(Map.of(
                "accepted", accepted,
                "message", accepted ? "Réponse transmise à l'IA" : "Réponse ignorée (déjà complété)"
        ));
    }

    @GetMapping("/{id}/paused")
    public ResponseEntity<Map<String, Object>> isPaused(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("paused", humanInputService.isPaused(id)));
    }
}
