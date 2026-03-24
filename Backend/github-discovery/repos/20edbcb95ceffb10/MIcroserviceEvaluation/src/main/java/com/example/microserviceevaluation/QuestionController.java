package com.example.microserviceevaluation;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
public class QuestionController {

    @Autowired
    private QuestionService questionService;
//
    @PostMapping("")
    public Question createQuestion(@RequestBody Question question) {
        return questionService.addQuestion(question);
    }
    @GetMapping("")
    public List <Question> getAllQuestions() {
        return questionService.getQuestions();
    }
//
    @PutMapping("/{quesId}")
    public ResponseEntity<Question> updateQuestion(@PathVariable  Long quesId, @RequestBody Question question) {
        try {
            // Vérifier que l'ID correspond
            if (!quesId.toString().equals(question.getQuesId())) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            Question updatedQuestion = questionService.updateQuestion(question);
            return new ResponseEntity<>(updatedQuestion, HttpStatus.OK);
        } catch (RuntimeException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
//
//    @GetMapping("/{quesId}")
//    public ResponseEntity<Question> getQuestionById(@PathVariable  Long quesId) {
//        try {
//            Question question = questionService.getQuestion(quesId);
//            return new ResponseEntity<>(question, HttpStatus.OK);
//        } catch (RuntimeException e) {
//            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
//        }
//    }
//

    @GetMapping("/by-evaluation/{idEvaluation}")
    public ResponseEntity<List<Question>> getQuestionsByEvaluation(@PathVariable Long idEvaluation) {
        return (ResponseEntity<List<Question>>) questionService.getQuestionsByEvaluationId(idEvaluation);

    }

    @DeleteMapping("/{quesId}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long quesId) {

        questionService.deleteQuestion(quesId);
        return ResponseEntity.noContent().build();
    }

}