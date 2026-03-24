package com.example.microserviceevaluation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long quesId;

    @Column(nullable = false)
    private String contenu;

    private String texte;
    private String image;
    private String option1;
    private String option2;
    private String option3;
    private String option4;
    private String answer;

    @Column(name = "bonne_reponse")
    private String bonneReponse;

    @ManyToOne
    @JoinColumn(name = "id_evaluation")
    private Evaluation evaluation;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Reponse> reponses;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuizQuestion {
        private Long id;
        private String question;
        private String description;
        private Map<String, String> answers;

        @JsonProperty("correct_answers")
        private Map<String, Boolean> correctAnswers;

        @JsonIgnore
        public String getCorrectAnswerKey() {
            return correctAnswers.entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
        }
    }

    @Data
    public static class QuizParams {
        private String category;
        private String difficulty;
        private Integer limit = 10;
    }

    @Data
    @AllArgsConstructor
    public static class QuizResult {
        private int score;
        private int total;
        private List<QuestionResult> details;
    }

    @Data
    @AllArgsConstructor
    public static class QuestionResult {
        private Long questionId;
        private boolean correct;
        private String correctAnswer;
    }

    @Data
    public static class UserAnswer {
        private Long questionId;
        private String selectedAnswer;
    }


    @Data
    @AllArgsConstructor
    public static class TimedQuizSession {
        private String sessionId;
        private List<QuizQuestion> questions;
        private Duration timeLimit;
        private Duration timeElapsed;
        private int currentQuestionIndex;
    }

    @Data
    @AllArgsConstructor
    public static class TimedQuizResult {
        private int score;
        private Duration timeUsed;
        private double accuracy; // Pourcentage de bonnes réponses
        private List<QuestionResult> details;
        private String performanceComment;
    }
}