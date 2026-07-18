-- Clan Manager 데이터 초기화 스크립트: 금고/분배 기록 삭제 버전
-- 목적:
--   - 클랜원 목록, 출석/보스참여/참여율 회차 관련 기록을 비웁니다.
--   - 금고 거래내역/분배 히스토리도 함께 비우고 금고 잔액을 0으로 만듭니다.
--   - 미피 계정만 남깁니다.
--
-- 실행 전 필수:
--   mysqldump -h <HOST> -P <PORT> -u <USER> -p --single-transaction --routines --triggers clanmanager > clanmanager_backup.sql

START TRANSACTION;

SET @mipi_id := (SELECT member_id FROM members WHERE character_name = '미피' LIMIT 1);

-- 1. 출석/참여/신청/스펙/컬렉템 상태 기록 삭제
DELETE FROM activity_attendances;
DELETE FROM boss_participation_members;
DELETE FROM boss_participation_records;
DELETE FROM collection_statuses;
DELETE FROM item_requests;
DELETE FROM member_spec_histories;

-- 2. 참여율 회차 기록 삭제
DELETE FROM participation_periods;

-- 3. 금고/분배 기록 삭제
DELETE FROM vault_transactions;
DELETE FROM distribution_snapshots;
UPDATE clan_vault
SET balance_diamonds = 0,
    version = 0;

-- 4. 공지 작성자 참조 정리
UPDATE notices
SET created_by = NULL
WHERE created_by IS NOT NULL
  AND created_by <> @mipi_id;

-- 5. 클랜원: 미피만 남기고 삭제
DELETE FROM members WHERE character_name <> '미피';

COMMIT;

-- 확인용
SELECT COUNT(*) AS member_count FROM members;
SELECT COUNT(*) AS activity_attendance_count FROM activity_attendances;
SELECT COUNT(*) AS boss_record_count FROM boss_participation_records;
SELECT COUNT(*) AS boss_member_count FROM boss_participation_members;
SELECT COUNT(*) AS period_count FROM participation_periods;
SELECT COUNT(*) AS vault_transaction_count FROM vault_transactions;
SELECT balance_diamonds FROM clan_vault;

