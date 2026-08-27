package edu.flab.chemilog.room;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomQuestionRepository extends JpaRepository<RoomQuestion, Long> {

    List<RoomQuestion> findByRoomIdOrderByDisplayOrder(Long roomId);
}
