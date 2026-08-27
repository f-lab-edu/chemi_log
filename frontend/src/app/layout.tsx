import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "케미로그",
  description: "친구들과 12개 질문에 답하고 서로의 케미 점수를 확인해요.",
};

// 모바일 웹이 1차 플랫폼이다 (PRD 4장). 입력 포커스 때 iOS 가 화면을 확대하지 않게
// maximumScale 을 고정하지는 않는다. 확대를 막으면 접근성 요구와 충돌한다.
export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: "#7c5cff",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className="h-full antialiased">
      <body className="min-h-full flex flex-col font-sans">{children}</body>
    </html>
  );
}
