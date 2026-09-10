package edu.flab.chemilog.room;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 조회 메서드를 추가할 때는 **ORDER BY id 를 함께 적습니다.** 표시 순서 컬럼이 없어서
 * 순서가 id 오름차순인데, 정렬을 빠뜨리면 MySQL 이 어떤 순서로 돌려줘도 규약 위반이
 * 아닙니다 (docs/domain.md 의 문항 표시 순서).
 */
public interface RoomQuestionRepository extends JpaRepository<RoomQuestion, Long> {
}
