package com.example.microserviceevaluation;

import com.example.microserviceevaluation.Question.QuizQuestion;
import com.example.microserviceevaluation.Question.QuizParams;
import com.example.microserviceevaluation.Question.QuizResult;
import com.example.microserviceevaluation.Question.UserAnswer;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @GetMapping("/questions")
    public Flux<QuizQuestion> getQuestions(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String difficulty,
            @RequestParam(defaultValue = "5") int limit) {

        QuizParams params = new QuizParams();
        params.setCategory(category);
        params.setDifficulty(difficulty);
        params.setLimit(limit);

        return quizService.fetchQuestions(params);
    }

    @PostMapping("/evaluate")
    public Mono<QuizResult> evaluateQuiz(@RequestBody List<UserAnswer> answers) {
        return quizService.evaluateQuiz(answers);
    }



}