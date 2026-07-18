# Clan Manager 데이터 초기화 메모

## 지금 밀어도 되는 주요 데이터

- 클랜원 목록: `members`에서 `미피`만 남김
- 출석기록: `activity_attendances`
- 보스참여기록: `boss_participation_records`, `boss_participation_members`
- 참여율/회차 기록: `participation_periods`
- 클랜원별 컬렉템 지급 상태: `collection_statuses`
- 아이템 신청 내역: `item_requests`
- 스펙/전투력 수정기록: `member_spec_histories`

## 보통 남겨도 되는 설정 데이터

- 활동/출석보스 설정: `activity_types`, `activity_schedules`
- 컬렉템 항목 목록: `collection_items`
- 컬렉템 수정/지급 로그: `collection_histories`, `collection_records`
- 전체아이템 재고 틀: `all_item_stocks`
- 공지: `notices`
- 기타 설정성 데이터

## 금고/분배 기록 선택

### 금고/분배 기록 유지

`docs/db-reset-core-keep-vault.sql`

- `vault_transactions`, `distribution_snapshots`, `clan_vault`는 유지합니다.
- 삭제되는 회원을 가리키던 금고 거래의 회원 참조는 `NULL`로 바꿉니다.
- 과거 기록은 남지만, 누가 받았는지 회원 연결은 일부 사라질 수 있습니다.

### 금고/분배 기록 삭제

`docs/db-reset-core-clear-vault.sql`

- 금고 거래내역과 분배 히스토리를 모두 삭제합니다.
- 금고 잔액도 0으로 초기화합니다.
- 새 시즌처럼 완전히 다시 시작할 때 추천합니다.

## 실행 전 백업

운영 DB에서 반드시 먼저 백업하세요.

```bash
mysqldump -h <HOST> -P <PORT> -u <USER> -p --single-transaction --routines --triggers clanmanager > clanmanager_backup_YYYYMMDD.sql
```

Railway에서 MySQL 접속 정보는 MySQL 서비스의 Variables 또는 Connect 탭에서 확인하면 됩니다.

## 실행 후 확인

```sql
SELECT COUNT(*) FROM members;
SELECT COUNT(*) FROM activity_attendances;
SELECT COUNT(*) FROM boss_participation_records;
SELECT COUNT(*) FROM boss_participation_members;
SELECT COUNT(*) FROM participation_periods;
```

`members`는 미피 1명만 남는 것이 정상입니다.

