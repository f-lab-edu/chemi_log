# database

로컬 개발용 MySQL 8.4 구성.

## 실행

```bash
cp .env.example .env   # 값을 채운다
docker compose up -d
```

## 초기화 스크립트

`init/` 하위 `.sql` 파일은 **볼륨이 비어 있는 최초 기동에만** 실행된다.
스키마를 추가하거나 수정한 뒤에는 볼륨을 지우고 다시 띄워야 반영된다.

```bash
docker compose down -v && docker compose up -d
```

## 설계 문서

스키마 설계와 그 근거는 [docs/database.md](../docs/database.md) 에 있다.
`init/01-schema.sql` 이 실제 정의를 소유하며, JPA `ddl-auto` 는 `none` 이다.
