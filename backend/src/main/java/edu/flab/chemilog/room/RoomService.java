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
 * room, participant(방장), room_question 12개가 한 트랜잭션입니다 (docs/domain.md).
 * 2번이 실패하면 방장 없는 방이, 3번이 실패하면 질문이 모자란 방이 남고 둘 다 화면에서
 * 복구할 방법이 없습니다. 문항이 모자란 방은 케미 점수의 분모가 달라져 다른 방과 비교할 수 없습니다.
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
     * 방을 만들고 방장을 첫 참여자로 등록한 뒤 질문 12개를 고정합니다.
     *
     * 방을 먼저 저장해야 방장과 질문에 room_id 를 줄 수 있습니다. 그 뒤 둘의 순서는 바꿔도
     * 되지만, 셋이 한 트랜잭션에서 벗어나면 방장 없는 방이나 질문이 모자란 방이 남습니다.
     *
     * 질문을 저장하는 순서가 곧 사용자가 보는 문항 순서입니다. RoomQuestionFactory 가 섞어서
     * 돌려준 목록을 그대로 넘깁니다. 여기서 정렬하면 그 무작위가 사라집니다 (docs/domain.md).
     */
    @Transactional
    public CreatedRoom create(String rawNickname) {
        Nickname nickname = Nickname.of(rawNickname);

        Room room = roomRepository.save(Room.withShareCode(shareCodeGenerator.generate()));

        String accessToken = accessTokenGenerator.generate();
        Participant host = participantRepository.save(
                Participant.host(room, nickname, accessTokenGenerator.hash(accessToken)));

        roomQuestionRepository.saveAll(roomQuestionFactory.createFor(room));

        // 방 생성 직후에는 언제나 HOST_ANSWERING 입니다. 상수를 그대로 적지 않아야
        // 조회 엔드포인트가 같은 판정을 씁니다.
        return new CreatedRoom(room.getShareCode(), RoomStatus.of(host), accessToken);
    }
}
