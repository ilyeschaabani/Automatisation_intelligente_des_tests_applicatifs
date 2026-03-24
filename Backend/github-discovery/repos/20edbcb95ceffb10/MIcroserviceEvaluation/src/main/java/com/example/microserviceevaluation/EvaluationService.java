package com.example.microserviceevaluation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class EvaluationService implements IEvaluationService {

    @Autowired
    private EvaluationRepository evaluationRepository;
    @Autowired
    private QuestionRepository QuestionRepository;

    @Override
    public Evaluation createEvaluation(Evaluation evaluation) {
        return evaluationRepository.save(evaluation);
    }

    //
    @Override
    public List<Evaluation> getAllEvaluations() {
        return evaluationRepository.findAll();
    }



//    public Question createQuestion(Question question, String evaluationId) {
//        Optional<Evaluation> evaluationOpt = evaluationRepository.findById(evaluationId);
//        if (evaluationOpt.isPresent()) {
//            Evaluation evaluation = evaluationOpt.get();
//            question.setEvaluation(evaluation);
//            return QuestionRepository.save(question);
//        } else {
//            throw new RuntimeException("Évaluation non trouvée");
//        }
//    }

//    @Override
//    public List<Question> getQuestionsByEvaluation(Long idEvaluation) {
//        return List.of();
//    }
//

    @Override
    public Evaluation updateEvaluation(Evaluation evaluation) {
        if (evaluationRepository.existsById(evaluation.getIdEvaluation())) {
            return evaluationRepository.save(evaluation);
        } else {
            throw new RuntimeException("Evaluation not found");
        }
    }

    public Evaluation getEvaluationById(Long idEvaluation) {
        return evaluationRepository.findFirstByIdEvaluation(idEvaluation)
                .orElseThrow(() -> new RuntimeException("Evaluation non trouvée avec id: " + idEvaluation));
    }
//
//
    @Override
    public void deleteEvaluation(Long idEvaluation) {
        evaluationRepository.deleteById(idEvaluation);
    }
//
//    @Override
//    public void submitEvaluationAnswers(Long idEvaluation, Map<Long, Object> answers) {
//        Optional<Evaluation> evaluationOpt = evaluationRepository.findById(idEvaluation);
//
//        if (evaluationOpt.isPresent()) {
//            System.out.println("Réponses reçues pour l'évaluation " + idEvaluation + ": " + answers);
//            // Logique pour stocker les réponses...
//        } else {
//            throw new RuntimeException("Évaluation non trouvée avec l'ID: " + idEvaluation);
//        }
//    }
//
//    @Override
//    public List<Question> getQuestionsByEvaluation(Long idEvaluation) {
//        return null;
//    }
//
//    //////////////////////
//    @Override
//    @Transactional
//    public Evaluation addQuestionsToEvaluation(Long idEvaluation, Set<Long> quesIds) {
//        // 1. Trouver l'évaluation
//        Evaluation evaluation = (Evaluation) evaluationRepository.findByIdEvaluation(idEvaluation)
//                .orElseThrow(() -> new RuntimeException("Évaluation non trouvée: " + idEvaluation));
//
//        // 2. Vérifier chaque question
//        Set<Question> questions = new HashSet<>();
//        for (Long quesId : quesIds) {
//            Question question = (Question) QuestionRepository.findByQuesId(quesId)
//                    .orElseThrow(() -> new RuntimeException("Question non trouvée: " + quesId));
//
//            // Vérification cruciale
//            if (question.getQuesId() == null) {
//                throw new IllegalStateException("La question a un ID null");
//            }
//
//            // Mettre à jour la référence
//            question.setIdEvaluation(idEvaluation);
//            QuestionRepository.save(question);
//
//            questions.add(question);
//        }
//        evaluation.getQuestions().clear();
//        evaluation.getQuestions().addAll(
//                quesIds.stream()
//                        .map(id -> QuestionRepository.findByQuesId(idEvaluation).orElseThrow())
//                        .collect(Collectors.toSet())
//        );
//        return evaluationRepository.save(evaluation);
//    }

    @Override
    @Transactional
    public Evaluation addQuestionsToEvaluation(Long idEvaluation, Set<Long> quesIds) {
        // 1. Trouver l'évaluation
        Evaluation evaluation = evaluationRepository.findById(idEvaluation)
                .orElseThrow(() -> new RuntimeException("Évaluation non trouvée: " + idEvaluation));

        // 2. Vérifier chaque question et établir la relation
        Set<Question> questions = quesIds.stream()
                .map(quesId -> {
                    Question question = QuestionRepository.findById(quesId)
                            .orElseThrow(() -> new RuntimeException("Question non trouvée: " + quesId));

                    if (question.getQuesId() == null) {
                        throw new IllegalStateException("La question a un ID null");
                    }

                    // Établir la relation bidirectionnelle
                    question.setEvaluation(evaluation);
                    return QuestionRepository.save(question);
                })
                .collect(Collectors.toSet());

        // 3. Mettre à jour la collection dans l'évaluation
        evaluation.setQuestions(questions);

        return evaluationRepository.save(evaluation);
    }
}