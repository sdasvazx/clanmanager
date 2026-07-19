-- 소수쟁 하루 여러 건 출석 인정용 수동 마이그레이션
-- 실행 전 DB 백업 권장.
--
-- 목적:
-- 1) 보스참여 기록에서 생성된 출석을 boss_participation_record_id로 구분
-- 2) 같은 날짜의 소수쟁이 여러 번 등록되어도 각각 참여횟수/점수에 반영
-- 3) 기존 수동 출석은 boss_participation_record_id = NULL 상태로 유지
--
-- 주의:
-- Hibernate ddl-auto=update가 컬럼/외래키를 먼저 만든 경우
-- ADD COLUMN 또는 ADD CONSTRAINT 구문은 이미 존재 오류가 날 수 있습니다.
-- 이 경우 해당 구문은 건너뛰고 유니크 인덱스 변경만 확인하세요.

ALTER TABLE activity_attendances
    ADD COLUMN boss_participation_record_id BIGINT NULL;

ALTER TABLE activity_attendances
    ADD CONSTRAINT fk_activity_attendance_boss_record
        FOREIGN KEY (boss_participation_record_id)
        REFERENCES boss_participation_records (record_id);

ALTER TABLE activity_attendances
    DROP INDEX uk_member_activity_date;

ALTER TABLE activity_attendances
    ADD CONSTRAINT uk_member_activity_date_record
        UNIQUE (member_id, activity_type_id, attendance_date, boss_participation_record_id);
