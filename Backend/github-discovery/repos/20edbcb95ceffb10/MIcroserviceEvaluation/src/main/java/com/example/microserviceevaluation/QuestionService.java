package com.example.microserviceevaluation;



import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

public interface QuestionService {
	
	public Question addQuestion(Question question);

	public Question updateQuestion(Question question);

	public List<Question> getQuestions();

	public Question getQuestion( Long quesId);

	public Set<Question> getQuestionsByEvaluationId(Long idEvaluation);
	public void deleteQuestion(Long quesId);



}
