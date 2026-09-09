import type { NextConfig } from "next";

// 브라우저는 항상 프론트엔드와 같은 오리진의 /api 를 부르고, Next 가 백엔드로 넘긴다.
// 참여자 토큰 쿠키가 SameSite=Lax 다 (위키 API-규약). 브라우저가 백엔드를 직접 부르면
// 배포 환경에서 도메인이 달라져 이 쿠키가 요청에 붙지 않는다. 로컬은 localhost 끼리라
// 포트만 달라도 same-site 로 취급돼 문제가 드러나지 않는다.
//
// 이 값은 **빌드 시점에 굳는다.** rewrites() 는 빌드 때 실행되어
// .next/routes-manifest.json 에 문자열로 박히고, next start 는 그 매니페스트를 쓴다.
// 실행할 때 BACKEND_ORIGIN 을 바꿔도 반영되지 않으므로 배포 대상마다 다시 빌드해야 한다.
// 한 번 빌드한 이미지를 여러 환경에 배포하려면 rewrite 대신 요청마다 process.env 를 읽는
// 자리(Route Handler, middleware)로 옮겨야 한다.
const backendOrigin = process.env.BACKEND_ORIGIN ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${backendOrigin}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
