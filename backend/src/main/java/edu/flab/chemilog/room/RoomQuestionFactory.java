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
 * 방에 고정할 질문 12개를 뽑아 스냅샷으로 만든다.
 *
 * 규칙은 docs/domain.md 의 "질문 12개 선정 규칙" 이다. 카테고리 4개에서 각각 3개씩,
 * active = TRUE 인 것만, 카테고리 안에서 무작위, 방끼리 중복 허용.
 *
 * display_order 는 카테고리를 묶어서 부여한다. 1~3 이 CONVERSATION, 4~6 이 TRAVEL,
 * 7~9 가 LIFESTYLE, 10~12 가 SPENDING 이다. 한 화면에 한 문항씩 넘기는 답변 화면에서
 * 같은 주제가 이어져 사용자가 맥락을 유지한 채 답하고, 결과 화면의 카테고리 점수와
 * 문항 순서가 이어지기 때문이다. 순서의 근거는 QuestionCategory 의 선언 순서다.
 */
@Component
public class RoomQuestionFactory {

    private static final int QUESTIONS_PER_CATEGORY = 3;

    private final QuestionRepository questionRepository;

    public RoomQuestionFactory(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    public List<RoomQuestion> createFor(Room room) {
        List<RoomQuestion> snapshots = new ArrayList<>();
        int displayOrder = 1;

        for (QuestionCategory category : QuestionCategory.values()) {
            for (Question picked : pick(category)) {
                snapshots.add(RoomQuestion.copyOf(room, picked, displayOrder++));
            }
        }
        return snapshots;
    }

    /**
     * 한 카테고리에서 3개를 무작위로 뽑는다.
     *
     * 지금 질문 풀은 카테고리별로 정확히 3개라 결과가 항상 같지만 무작위로 둔다.
     * 질문을 추가하면 이 코드를 고치지 않고 그대로 쓴다 (docs/domain.md).
     *
     * 뽑기용 난수는 자격증명이 아니라 ThreadLocalRandom 으로 충분하다.
     * 공유 코드와 참여자 토큰만 SecureRandom 을 쓴다.
     */
    private List<Question> pick(QuestionCategory category) {
        List<Question> candidates = new ArrayList<>(questionRepository.findByCategoryAndActiveTrue(category));
        if (candidates.size() < QUESTIONS_PER_CATEGORY) {
            // 사용자가 고칠 수 있는 것이 없는 서버 구성 문제라 규약의 INTERNAL_ERROR 로 나간다.
            // 채워진 만큼만 만들면 방마다 문항 수가 달라져 케미 점수를 방끼리 비교할 수 없다.
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR,
                    "활성 질문이 모자라 방을 만들 수 없다. category=%s, 후보=%d, 필요=%d"
                            .formatted(category, candidates.size(), QUESTIONS_PER_CATEGORY));
        }
        Collections.shuffle(candidates, ThreadLocalRandom.current());
        return candidates.subList(0, QUESTIONS_PER_CATEGORY);
    }
}
