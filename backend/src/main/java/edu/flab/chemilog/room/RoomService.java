package edu.flab.chemilog.room;

import edu.flab.chemilog.participant.AccessTokenGenerator;
import edu.flab.chemilog.participant.Nickname;
import edu.flab.chemilog.participant.Participant;
import edu.flab.chemilog.participant.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방 생성의 트랜잭션 경계.
 *
 * room, participant, room.host_participant_id, room_question 12개가 한 트랜잭션이다
 * (docs/domain.md). 부분 적용되면 방장이 없는 방이나 문항이 모자란 방이 남는다.
 * 문항이 모자란 방은 케미 점수의 분모가 달라져 다른 방과 비교할 수 없게 된다.
 */
@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;
    private final RoomQuestionRepository roomQuestionRepository;
    private final RoomQuestionFactory roomQuestionFactory;
    private final ShareCodeGenerator shareCodeGenerator;
    private final AccessTokenGenerator accessTokenGenerator;

    public RoomService(RoomRepository roomRepository,
                       ParticipantRepository participantRepository,
                       RoomQuestionRepository roomQuestionRepository,
                       RoomQuestionFactory roomQuestionFactory,
                       ShareCodeGenerator shareCodeGenerator,
                       AccessTokenGenerator accessTokenGenerator) {
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.roomQuestionRepository = roomQuestionRepository;
        this.roomQuestionFactory = roomQuestionFactory;
        this.shareCodeGenerator = shareCodeGenerator;
        this.accessTokenGenerator = accessTokenGenerator;
    }

    /**
     * 방을 만들고 방장을 첫 참여자로 등록한다.
     *
     * 순서를 바꾸지 마라. 스키마의 fk_room_host_participant 가 (room.id, host_participant_id) 를
     * participant (room_id, id) 에서 찾으므로, 방장 행이 없는 상태로 room 을 갱신하면 DB 가 거절한다.
     * 방을 먼저 만들어야 방장에게 room_id 를 줄 수 있고, 방장 id 가 나와야 방이 그것을 가리킬 수 있다.
     */
    @Transactional
    public CreatedRoom create(String rawNickname) {
        Nickname nickname = Nickname.of(rawNickname);

        Room room = roomRepository.save(Room.openFor(shareCodeGenerator.generate()));

        String accessToken = accessTokenGenerator.generate();
        Participant host = participantRepository.save(
                Participant.join(room, nickname, accessTokenGenerator.hash(accessToken)));

        room.assignHost(host);
        roomQuestionRepository.saveAll(roomQuestionFactory.createFor(room));

        return new CreatedRoom(room.getShareCode(), room.getStatus(), accessToken);
    }
}
