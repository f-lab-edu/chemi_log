import type { ButtonHTMLAttributes } from "react";

/**
 * 목업 `ui-mvp.html` 의 `.btn` 계열을 그대로 옮긴 것이다.
 * 높이 56px 은 PRD 4장의 "주요 터치 영역 44px 이상" 을 만족한다.
 */
export type ButtonVariant = "primary" | "soft" | "white" | "ghost";

const VARIANT_CLASS: Record<ButtonVariant, string> = {
  primary: "bg-linear-135 from-brand to-[#9a6bff] text-white",
  soft: "bg-surface text-brand-deep",
  white: "bg-white text-brand-deep",
  ghost: "bg-white border-[1.5px] border-line text-[#4a4458]",
};

const BASE_CLASS =
  "flex h-14 w-full items-center justify-center rounded-2xl text-[16.5px] font-bold " +
  "transition-opacity active:opacity-80 disabled:bg-none disabled:bg-[#f0eff5] " +
  "disabled:text-[#b6b2c4] disabled:active:opacity-100";

export function buttonClass(variant: ButtonVariant): string {
  return `${BASE_CLASS} ${VARIANT_CLASS[variant]}`;
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
}

export function Button({
  variant = "primary",
  className = "",
  type = "button",
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      className={`${buttonClass(variant)} ${className}`}
      {...props}
    />
  );
}
