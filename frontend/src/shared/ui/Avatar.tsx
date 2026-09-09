/**
 * 닉네임 첫 글자를 보여주는 아바타 (목업 02, 05, 07).
 *
 * 색은 목업에 쓰인 값만 쓴다. 닉네임으로 고르므로 같은 사람은 화면이 바뀌어도 같은 색이다.
 */
const COLORS = ["#7c5cff", "#ff7bb0", "#b45cff", "#5b9bd5", "#f2994a"];

/** 코드포인트를 더해 색을 고른다. UTF-16 코드 유닛으로 세면 이모지 닉네임이 한쪽으로 몰린다. */
function colorOf(nickname: string): string {
  const sum = [...nickname].reduce((acc, ch) => acc + ch.codePointAt(0)!, 0);
  return COLORS[sum % COLORS.length];
}

const SIZE = {
  sm: "size-10 rounded-[13px] text-[15px]",
  md: "size-11.5 rounded-2xl text-base",
  lg: "size-19.5 rounded-[26px] text-3xl",
};

interface AvatarProps {
  nickname: string;
  size?: keyof typeof SIZE;
  /** 브랜드 색 띠 위에서는 반투명 흰색으로 뒤집는다 (목업 `.band .avatar-lg`). */
  onBand?: boolean;
}

export function Avatar({ nickname, size = "sm", onBand = false }: AvatarProps) {
  const initial = [...nickname][0] ?? "?";
  return (
    <div
      aria-hidden
      className={`flex flex-none items-center justify-center font-extrabold text-white ${SIZE[size]} ${
        onBand ? "border-[1.5px] border-white/35 bg-white/20" : ""
      }`}
      style={onBand ? undefined : { background: colorOf(nickname) }}
    >
      {initial}
    </div>
  );
}
