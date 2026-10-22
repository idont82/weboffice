-- 2026-08-31 추가분(PostgreSQL). 이미 돌아가는 DB 에 표 셋을 더한다.
--
-- 스키마 전체를 다시 만들 수는 없으므로(DROP 이 들어 있다) 늘어난 표만 뽑아 둔다.
--
--   psql -h <host> -U <user> -d <database> -f db/migration-2026-08-31-mark-snapshot.sql
--
-- 표 셋이 하는 일
--   wo_user_workbook_mark  파일함의 별표(즐겨찾기)와 색 딱지. 사람마다 따로다.
--   wo_user_color_label    색에 붙인 내 이름("빨강" 대신 "급함").
--   wo_workbook_snapshot   복구 지점. 문서를 통째로 담아 통째로 되돌린다.

-- 사람마다 "이 문서를 어떻게 표시해 두었는가". 별표(즐겨찾기)와 색 딱지다.
-- 개인 것이다 - 내가 칠한 색이 남의 목록에 보이면 색은 공용 규칙이 되어 버리고,
-- 공유받은 남의 문서에는 아예 표시를 못 하게 된다.
CREATE TABLE wo_user_workbook_mark (
    USER_KEY          varchar(50)   NOT NULL,
    WORKBOOK_SEQ    bigint        NOT NULL,
    -- 즐겨찾기. 목록 맨 위 묶음으로 올라간다.
    STARRED_YN      char(1)       DEFAULT 'N' NOT NULL,
    -- 색 딱지 하나. RED·ORANGE·YELLOW·GREEN·BLUE·PURPLE·GRAY 또는 NULL(안 칠함).
    COLOR_CODE      varchar(10)   NULL,
    INPUT_DATETIME  timestamp(3)  NULL,
    INPUT_USER       varchar(50)   NULL,
    MOD_DATETIME    timestamp(3)  NULL,
    MOD_USER         varchar(50)   NULL,
    DEL_DATETIME    timestamp(3)  NULL,
    DEL_USER         varchar(50)   NULL,
    CONSTRAINT pk_user_workbook_mark PRIMARY KEY (USER_KEY, WORKBOOK_SEQ)
);
CREATE INDEX ix_user_workbook_mark01 ON wo_user_workbook_mark (WORKBOOK_SEQ);

-- 색에 붙인 내 이름. 안 지으면 색 이름 그대로다.
CREATE TABLE wo_user_color_label (
    USER_KEY          varchar(50)   NOT NULL,
    COLOR_CODE      varchar(10)   NOT NULL,
    LABEL_NAME      varchar(20)   NOT NULL,
    INPUT_DATETIME  timestamp(3)  NULL,
    INPUT_USER       varchar(50)   NULL,
    MOD_DATETIME    timestamp(3)  NULL,
    MOD_USER         varchar(50)   NULL,
    DEL_DATETIME    timestamp(3)  NULL,
    DEL_USER         varchar(50)   NULL,
    CONSTRAINT pk_user_color_label PRIMARY KEY (USER_KEY, COLOR_CODE)
);

-- 복구 지점. 문서 전체를 한 덩어리로 찍어 둔 것이다.
--
-- 버전 기록과 하는 일이 다르다. 이력은 칸 하나하나의 변화를 남기지만 시트·병합·
-- 크기·그림 같은 얼개는 아예 안 남는다 - 시트를 지운 뒤 되돌리면 값만 오고 시트는
-- 안 돌아온다. 여기는 통째로 담아 통째로 되돌린다.
--
-- 이미지 바이트는 안 담는다. wo_sheet_image_master 가 해시로 중복을 제거해 이미
-- 한 벌만 갖고 있고, 이미지를 지우는 길이 없어 참조가 끊기지 않는다.
CREATE TABLE wo_workbook_snapshot (
    SNAPSHOT_SEQ    bigint GENERATED ALWAYS AS IDENTITY,
    WORKBOOK_SEQ    bigint        NOT NULL,
    TAKEN_AT        timestamp(3)  NOT NULL,
    TAKEN_USER       varchar(50)   NOT NULL,
    -- 왜 찍었는가. MANUAL(사람) · SESSION(편집이 끝나서) · DAILY(하루 한 번) ·
    -- BEFORE_RESTORE · BEFORE_AI · BEFORE_IMPORT(위험한 일 직전)
    REASON          varchar(20)   NOT NULL,
    -- 사람이 붙인 이름. 없으면 NULL
    LABEL           varchar(50)   NULL,
    DOC_TYPE        varchar(10)   NOT NULL,
    -- 푼 크기(바이트). 목록에서 얼마나 큰지 보여 준다.
    BODY_SIZE       bigint        DEFAULT 0 NOT NULL,
    -- gzip 한 JSON.
    BODY            bytea         NOT NULL,
    INPUT_DATETIME  timestamp(3)  NULL,
    INPUT_USER       varchar(50)   NULL,
    MOD_DATETIME    timestamp(3)  NULL,
    MOD_USER         varchar(50)   NULL,
    DEL_DATETIME    timestamp(3)  NULL,
    DEL_USER         varchar(50)   NULL,
    CONSTRAINT pk_workbook_snapshot PRIMARY KEY (SNAPSHOT_SEQ),
    CONSTRAINT fk_workbook_snapshot01 FOREIGN KEY (WORKBOOK_SEQ)
        REFERENCES wo_workbook_master (WORKBOOK_SEQ)
);
CREATE INDEX ix_workbook_snapshot01
    ON wo_workbook_snapshot (WORKBOOK_SEQ, TAKEN_AT DESC);
