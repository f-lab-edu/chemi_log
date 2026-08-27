import Link from "next/link";
import { NicknameForm } from "@/features/room/NicknameForm";

/** 02 방 만들기 (위키 `UI-MVP`). */
export default function NewRoomPage() {
  return (
    <div className="flex flex-1 flex-col">
      <nav className="flex h-13 flex-none items-center px-5">
        <Link
          href="/"
          aria-label="뒤로 가기"
          className="flex size-11 items-center justify-center -ml-3 text-xl leading-none"
        >
          ←
        </Link>
      </nav>
      <NicknameForm />
    </div>
  );
}
