package com.example.microserviceevaluation;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    Optional<Object> findByQuesId(Long quesId);

    @Query("SELECT q FROM Question q WHERE q.evaluation.idEvaluation = :idEvaluation")
    Set<Question> findByIdEvaluation(@Param("idEvaluation") Long idEvaluation);
    //  Optional<Question> findByQuesId(Long quesId);
}
