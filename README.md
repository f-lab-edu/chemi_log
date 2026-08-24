# 케미로그 (ChemiLog)

둘 또는 여러 명이 같은 랜덤 질문에 각자 답하고, 답변을 비교해 서로의 케미 점수와
모임 안에서의 관계 순위를 확인하는 모바일 웹 우선 소셜 서비스.

## 기술 구성

| 영역 | 스택 |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1.1, Spring MVC, Spring Data JPA |
| Frontend | Next.js, TypeScript, App Router, Tailwind CSS |
| Database | MySQL 8.4 |

## 디렉토리

```text
root/
├── backend/    Spring Boot
├── frontend/   Next.js
└── database/   MySQL (docker-compose)
```

## 로컬 실행

### 1. database

```bash
cd database
cp .env.example .env   # 값을 채운다
docker compose up -d
```

### 2. backend

```bash
cd backend
DB_PASSWORD=<.env 의 MYSQL_PASSWORD> ./gradlew bootRun
```

http://localhost:8080

### 3. frontend

```bash
cd frontend
npm install
npm run dev
```

http://localhost:3000
