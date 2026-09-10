package edu.flab.chemilog.room;

import edu.flab.chemilog.common.ApiErrorCode;
import edu.flab.chemilog.common.ApiException;
import edu.flab.chemilog.question.Question;
import edu.flab.chemilog.question.QuestionCategory;
import edu.flab.chemilog.question.QuestionRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * 방에 고정할 질문 12개를 뽑습니다.
 *
 * 규칙은 docs/domain.md 의 "질문 12개 선정 규칙" 입니다. 카테고리 4개에서 각각 3개씩,
 * active = TRUE 인 것만, 카테고리 안에서 무작위, 방끼리 중복 허용.
 *
 * **뽑은 12개를 다시 한 번 섞습니다.** 카테고리별 3개는 배분 규칙이고 화면 순서와 무관합니다.
 * 표시 순서 컬럼이 없어져서 순서를 정하는 수단이 INSERT 순서뿐이므로, 여기서 섞은 결과가
 * 그대로 room_question.id 순서가 되고 그것이 사용자가 보는 순서입니다.
 */
@Component
public class RoomQuestionFactory {

    private static final int QUESTIONS_PER_CATEGORY = 3;

    private final QuestionRepository questionRepository;

    public RoomQuestionFactory(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    /**
     * 반환 순서가 곧 표시 순서입니다. 부르는 쪽은 이 순서 그대로 저장해야 합니다.
     *
     * saveAll 이 목록 순서대로 INSERT 하므로 AUTO_INCREMENT id 가 이 순서로 붙습니다.
     * 순서를 바꿔서 저장하면 카테고리가 묶여 보이는 옛 동작으로 되돌아갑니다.
     */
    public List<RoomQuestion> createFor(Room room) {
        List<Question> picked = new ArrayList<>();
        for (QuestionCategory category : QuestionCategory.values()) {
            picked.addAll(pick(category));
        }

        // 카테고리 묶음을 풉니다. 이 줄이 없으면 대화 3개, 여행 3개 순으로 화면에 나옵니다.
        Collections.shuffle(picked, ThreadLocalRandom.current());

        return picked.stream()
                .map(question -> RoomQuestion.of(room, question))
                .toList();
    }

    /**
     * 한 카테고리에서 3개를 무작위로 뽑습니다.
     *
     * 무작위 추출을 SQL 의 ORDER BY RAND() 로 하지 않습니다. RAND() 는 조건에 맞는 행 전부를
     * 정렬하므로 질문 풀이 커질수록 비용이 늘어납니다. 여기서는 idx_question_category_active
     * (category, active) 로 후보만 읽고 섞는 일은 애플리케이션이 합니다.
     *
     * 뽑기용 난수는 자격증명이 아니라 ThreadLocalRandom 으로 충분합니다.
     * 공유 코드와 참여자 토큰만 SecureRandom 을 씁니다.
     */
    private List<Question> pick(QuestionCategory category) {
        List<Question> candidates = new ArrayList<>(questionRepository.findByCategoryAndActiveTrue(category));
        if (candidates.size() < QUESTIONS_PER_CATEGORY) {
            // 사용자가 고칠 수 있는 것이 없는 서버 구성 문제라 규약의 INTERNAL_ERROR 로 나갑니다.
            // 채워진 만큼만 만들면 방마다 문항 수가 달라져 케미 점수를 방끼리 비교할 수 없습니다.
            // 전체 점수 계산이 12로 나누는 것을 전제합니다 (PRD 10장).
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR,
                    "활성 질문이 모자라 방을 만들 수 없다. category=%s, 후보=%d, 필요=%d"
                            .formatted(category, candidates.size(), QUESTIONS_PER_CATEGORY));
        }
        Collections.shuffle(candidates, ThreadLocalRandom.current());
        return candidates.subList(0, QUESTIONS_PER_CATEGORY);
    }
}
