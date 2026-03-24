package com.example.microserviceevaluation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface QuizService {
    Flux<Question.QuizQuestion> fetchQuestions(Question.QuizParams params);
    Mono<Question.QuizResult> evaluateQuiz(List<Question.UserAnswer> answers);
}