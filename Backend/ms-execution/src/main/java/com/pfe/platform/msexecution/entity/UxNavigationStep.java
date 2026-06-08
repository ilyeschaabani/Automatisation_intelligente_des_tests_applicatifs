package com.pfe.platform.msexecution.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ux_navigation_steps")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UxNavigationStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evaluation_id", nullable = false)
    private Long evaluationId;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(name = "step_name", columnDefinition = "text")
    private String stepName;

    @Column(name = "action_performed", columnDefinition = "text")
    private String actionPerformed;

    @Column(name = "observation", columnDefinition = "text")
    private String observation;

    @Column(name = "page_url", columnDefinition = "text")
    private String pageUrl;

    @Column(name = "page_title", columnDefinition = "text")
    private String pageTitle;

    @Column(name = "screenshot_base64", columnDefinition = "text")
    private String screenshotBase64;

    @Column(name = "gemini_raw_response", columnDefinition = "text")
    private String geminiRawResponse;

    /** Action type decided by Gemini: CLICK, FILL, SCROLL, DONE, SKIP */
    @Column(name = "action_type", columnDefinition = "text")
    private String actionType;

    /** CSS selector or text target used for the action */
    @Column(name = "selector", columnDefinition = "text")
    private String selector;

    /** Value filled (for FILL actions) */
    @Column(name = "fill_value", columnDefinition = "text")
    private String fillValue;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
