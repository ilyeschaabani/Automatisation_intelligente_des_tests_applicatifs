package com.example.microserviceevaluation;

import jakarta.persistence.Cacheable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class QuestionServiceImpl implements QuestionService {

	@Autowired
	private QuestionRepository questionRepository;

	@Override
	public Question addQuestion(Question question) {
		return this.questionRepository.save(question);
	}

	@Override
	public Question updateQuestion(Question question) {
		if(!questionRepository.existsById(question.getQuesId())) {
			throw new RuntimeException("Question non trouvée avec ID: " + question.getQuesId());
		}
		return this.questionRepository.save(question);
	}

	@Override
	public List<Question> getQuestions() {
		return questionRepository.findAll();
	}

	@Override
	public Question getQuestion( Long quesId) {
		Optional<Question> question = this.questionRepository.findById(quesId);
		return question.orElseThrow(() -> new RuntimeException("Question non trouvée avec ID: " + quesId));
	}

	@Override
	public Set<Question> getQuestionsByEvaluationId(Long idEvaluation) {
		return questionRepository.findByIdEvaluation(idEvaluation);
	}

	@Override
	public void deleteQuestion(Long quesId ) {

		questionRepository.deleteById(quesId);
	}



}