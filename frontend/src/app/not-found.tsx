import { NewRoomLink, StateScreen } from "@/shared/ui/StateScreen";

/** 없는 경로 (PRD 12장 잘못된 링크). */
export default function NotFound() {
  return (
    <StateScreen
      emoji="🔗"
      title={<>없는 주소예요</>}
      description={
        <>
          링크가 잘못됐거나 사라진 페이지예요.
          <br />
          초대한 친구에게 링크를 다시 받아 보세요.
        </>
      }
      action={<NewRoomLink />}
    />
  );
}
