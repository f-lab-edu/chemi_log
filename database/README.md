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
