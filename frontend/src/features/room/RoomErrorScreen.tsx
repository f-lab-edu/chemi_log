import Link from "next/link";
import type { ApiError } from "@/shared/api/client";
import { Button, buttonClass } from "@/shared/ui/Button";
import { NewRoomLink, StateScreen } from "@/shared/ui/StateScreen";

interface RoomErrorScreenProps {
  error: ApiError;
  /** 다시 시도할 수 있는 화면이면 넘긴다. 잘못된 링크처럼 재시도가 의미 없으면 생략한다. */
  onRetry?: () => void;
  /** 방 안으로 돌려보낼 수 있으면 넘긴다. 없으면 홈으로 가는 버튼을 준다. */
  shareCode?: string;
}

/**
 * 방 관련 실패를 화면으로 바꾼다. 분기는 `code` 로만 한다.
 *
 * `message` 로 분기하지 않는 이유는 규약이 "문구는 언제든 바뀌지만 `code` 는 계약" 이라고
 * 못박았기 때문이다. 여기 문구는 PRD 12장의 상황 설명을 옮긴 것이고 서버 문구를 쓰지 않는다.
 */
export function RoomErrorScreen({
  error,
  onRetry,
  shareCode,
}: RoomErrorScreenProps) {
  switch (error.code) {
    case "ROOM_NOT_FOUND":
      return (
        <StateScreen
          emoji="🔗"
          title={<>링크가 잘못됐어요</>}
          description={
            <>
              사라졌거나 주소가 바뀐 방이에요.
              <br />
              초대한 친구에게 링크를 다시 받아 보세요.
            </>
          }
          action={<NewRoomLink />}
        />
      );

    case "ROOM_NOT_OPEN":
      return (
        <StateScreen
          emoji="⏳"
          title={<>방을 준비하고 있어요</>}
          description={
            <>
              방장이 질문 12개에 답하는 중이에요.
              <br />
              끝나면 바로 참여할 수 있어요.
            </>
          }
          action={
            onRetry && (
              <Button variant="soft" onClick={onRetry}>
                새로고침
              </Button>
            )
          }
        />
      );

    case "RESULT_NOT_ALLOWED":
      return (
        <StateScreen
          emoji="🔒"
          title={<>먼저 답변을 제출해 주세요</>}
          description={
            <>
              12개 질문에 모두 답해야
              <br />
              케미 결과를 볼 수 있어요.
            </>
          }
          // 버튼이 없으면 여기서 나갈 길이 사라진다. 할 일이 정해져 있는 화면이라 그 길을 준다.
          action={
            shareCode ? (
              <Link
                href={`/rooms/${shareCode}/answer`}
                className={buttonClass("primary")}
              >
                답변하러 가기
              </Link>
            ) : (
              <NewRoomLink />
            )
          }
        />
      );

    default:
      return (
        <StateScreen
          emoji="😵"
          title={<>불러오지 못했어요</>}
          description={error.message}
          action={
            onRetry ? (
              <Button variant="primary" onClick={onRetry}>
                다시 시도
              </Button>
            ) : (
              <NewRoomLink />
            )
          }
        />
      );
  }
}
