-- Clan Manager 데이터 초기화 스크립트: 금고/분배 기록 유지 버전
-- 목적:
--   - 클랜원 목록, 출석/보스참여/참여율 회차 관련 기록을 비우고 미피 계정만 남깁니다.
--   - vault_transactions, distribution_snapshots, clan_vault 잔액은 유지합니다.
--
-- 실행 전 필수:
--   1) 운영 DB 백업
--      mysqldump -h <HOST> -P <PORT> -u <USER> -p --single-transaction --routines --triggers clanmanager > clanmanager_backup.sql
--   2) 미피 계정이 존재하는지 확인
--      SELECT member_id, character_name FROM members WHERE character_name = '미피';
--
-- 주의:
--   - 금고/분배 기록을 유지하면서 members를 삭제하면 vault_transactions의 target_member_id/created_by가
--     삭제된 회원을 가리킬 수 있습니다. 이를 방지하기 위해 아래에서 미피가 아닌 회원 참조를 NULL로 정리합니다.
--   - notices.created_by도 삭제될 회원 참조를 NULL로 정리합니다.

START TRANSACTION;

SET @mipi_id := (SELECT member_id FROM members WHERE character_name = '미피' LIMIT 1);

-- 미피 계정이 없으면 여기서 중단하세요.
-- SELECT @mipi_id;

-- 1. 회원 FK를 가진 유지 테이블의 참조 정리
UPDATE vault_transactions
SET target_member_id = NULL
WHERE target_member_id IS NOT NULL
  AND target_member_id <> @mipi_id;

UPDATE vault_transactions
SET created_by = NULL
WHERE created_by IS NOT NULL
  AND created_by <> @mipi_id;

UPDATE notices
SET created_by = NULL
WHERE created_by IS NOT NULL
  AND created_by <> @mipi_id;

-- 2. 출석/참여/신청/스펙/컬렉템 상태 기록 삭제
DELETE FROM activity_attendances;
DELETE FROM boss_participation_members;
DELETE FROM boss_participation_records;
DELETE FROM collection_statuses;
DELETE FROM item_requests;
DELETE FROM member_spec_histories;

-- 3. 참여율 회차 기록 삭제
DELETE FROM participation_periods;

-- 4. 클랜원: 미피만 남기고 삭제
DELETE FROM members WHERE character_name <> '미피';

COMMIT;

-- 확인용
SELECT COUNT(*) AS member_count FROM members;
SELECT COUNT(*) AS activity_attendance_count FROM activity_attendances;
SELECT COUNT(*) AS boss_record_count FROM boss_participation_records;
SELECT COUNT(*) AS boss_member_count FROM boss_participation_members;
SELECT COUNT(*) AS period_count FROM participation_periods;

