package com.example.microserviceevaluation;

import com.example.microserviceevaluation.Question.QuizQuestion;
import com.example.microserviceevaluation.Question.QuizParams;
import com.example.microserviceevaluation.Question.QuizResult;
import com.example.microserviceevaluation.Question.UserAnswer;
import com.example.microserviceevaluation.Question.QuestionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class QuizServiceImpl implements QuizService {

    private static final String QUESTIONS_PATH = "/questions";
    private final WebClient webClient;

    public QuizServiceImpl(@Qualifier("quizApiWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    @Cacheable(value = "quizQuestions", key = "{#params.category, #params.difficulty, #params.limit}")
    public Flux<QuizQuestion> fetchQuestions(QuizParams params) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(QUESTIONS_PATH)
                        .queryParamIfPresent("category", Optional.ofNullable(params.getCategory()))
                        .queryParamIfPresent("difficulty", Optional.ofNullable(params.getDifficulty()))
                        .queryParamIfPresent("limit", Optional.ofNullable(params.getLimit()))
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(error -> Mono.error(new QuizApiException("Quiz API error: " + error)))
                )
                .bodyToFlux(QuizQuestion.class)
                .timeout(Duration.ofSeconds(5))
                .doOnError(e -> log.error("Quiz API call failed", e));
    }

    @Override
    public Mono<QuizResult> evaluateQuiz(List<UserAnswer> answers) {
        return Flux.fromIterable(answers)
                .flatMap(answer -> fetchQuestionById(answer.getQuestionId())
                        .map(question -> new QuestionResult(
                                question.getId(),
                                answer.getSelectedAnswer().equals(question.getCorrectAnswerKey()),
                                question.getCorrectAnswerKey()
                        ))
                )
                .collectList()
                .map(results -> new QuizResult(
                        (int) results.stream().filter(QuestionResult::isCorrect).count(),
                        results.size(),
                        results
                ));
    }

    private Mono<QuizQuestion> fetchQuestionById(Long questionId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(QUESTIONS_PATH + "/{id}")
                        .build(questionId))
                .retrieve()
                .bodyToMono(QuizQuestion.class)
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(e -> {
                    log.error("Failed to fetch question with id: " + questionId, e);
                    return Mono.empty();
                });
    }
}

// Ajout de la classe d'exception si elle n'existe pas
class QuizApiException extends RuntimeException {
    public QuizApiException(String message) {
        super(message);
    }
}