-- 분배 스냅샷에 다중 회차 선택값을 저장하기 위한 컬럼입니다.
-- 기존 period_id 기반 데이터는 삭제하지 않고, 새 기능은 period_ids에 "1,2,3" 형태로 저장합니다.
-- 운영 DB에 이미 컬럼이 생성되어 있다면 이 SQL은 실행하지 않아도 됩니다.

ALTER TABLE distribution_snapshots
    ADD COLUMN period_ids VARCHAR(500) NULL;
