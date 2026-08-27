package edu.flab.chemilog.question;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    /**
     * 방 생성 시 카테고리별 후보를 가져온다.
     *
     * 무작위 추출을 SQL 의 ORDER BY RAND() 로 하지 않는다. RAND() 는 조건에 맞는 행 전부를
     * 정렬하므로 질문 풀이 커질수록 비용이 늘어난다. 여기서는 idx_question_category_active
     * (category, active) 로 후보만 읽고 섞는 일은 애플리케이션이 한다.
     */
    List<Question> findByCategoryAndActiveTrue(QuestionCategory category);
}
