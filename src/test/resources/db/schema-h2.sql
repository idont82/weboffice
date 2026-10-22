-- 개발·테스트용 H2 스키마.
--
-- db/schema-postgresql.sql(1차 구축 대상), db/schema-oracle.sql(이식성 유지)과
-- 컬럼 순서·컬럼명·제약 이름을 동일하게 유지한다. 한쪽만 바뀌면 눈으로 잡기 위해서다.
--
-- JSON 컬럼(STYLE_VALUE, ORG_VALUE, MOD_VALUE)은 CLOB 으로 둔다.
-- H2 에도 JSON 타입이 있지만 INSERT 에 FORMAT JSON 이 필요해 매퍼가 벤더별로 갈린다.
-- JSON 검증은 PostgreSQL(jsonb)과 Oracle(IS JSON)의 몫이다.

-- 초대가 사용자를 가리키므로 초대를 먼저 지운다.
DROP TABLE IF EXISTS wo_user_invite;
DROP TABLE IF EXISTS wo_user_account;
DROP TABLE IF EXISTS wo_view_event_log;
DROP TABLE IF EXISTS wo_view_session;
DROP TABLE IF EXISTS wo_view_doc_cache;
DROP TABLE IF EXISTS wo_user_sheet_view;
DROP TABLE IF EXISTS wo_sheet_drawing_line;
DROP TABLE IF EXISTS wo_sheet_image_master;
DROP TABLE IF EXISTS wo_cell_change_log;
DROP TABLE IF EXISTS wo_doc_change_log;
DROP TABLE IF EXISTS wo_sheet_size_line;
DROP TABLE IF EXISTS wo_sheet_lock_line;
DROP TABLE IF EXISTS wo_sheet_merge_line;
DROP TABLE IF EXISTS wo_sheet_cell_line;
DROP TABLE IF EXISTS wo_cell_style_master;
DROP TABLE IF EXISTS wo_workbook_sheet_line;
DROP TABLE IF EXISTS wo_doc_paragraph_line;
DROP TABLE IF EXISTS wo_workbook_resource_line;
DROP TABLE IF EXISTS wo_workbook_snapshot;
DROP TABLE IF EXISTS wo_user_workbook_mark;
DROP TABLE IF EXISTS wo_user_color_label;
DROP TABLE IF EXISTS wo_workbook_member_map;
DROP TABLE IF EXISTS wo_workbook_master;

CREATE TABLE wo_workbook_master (
    WORKBOOK_SEQ    BIGINT GENERATED ALWAYS AS IDENTITY,
    WORKBOOK_NAME   VARCHAR(4000) NOT NULL,
    OWNER_USER       VARCHAR(50)   NOT NULL,
    DOC_VER         INT           DEFAULT 1 NOT NULL,
    -- 문서 종류. SHEET(시트) 또는 DOC(글). 화면이 무엇을 열지 정한다.
    DOC_TYPE        VARCHAR(10)   DEFAULT 'SHEET' NOT NULL,
    USE_YN          CHAR(1)       DEFAULT 'Y' NOT NULL,
    -- 공개 링크 토큰. 있으면 공개, 없으면 비공개다. CHAR 이 아니라 VARCHAR 인
    -- 이유는 고정 길이가 값에 공백을 붙여 URL 을 만들 때마다 trim() 을 기억해야
    -- 하기 때문이다(이미지 해시 CHAR(64) 에서 겪었다).
    SHARE_TOKEN     VARCHAR(32)   NULL,
    INPUT_DATETIME  TIMESTAMP(3)  NULL,
    INPUT_USER       VARCHAR(50)   NULL,
    MOD_DATETIME    TIMESTAMP(3)  NULL,
    MOD_USER         VARCHAR(50)   NULL,
    DEL_DATETIME    TIMESTAMP(3)  NULL,
    DEL_USER         VARCHAR(50)   NULL,
    CONSTRAINT pk_workbook_master PRIMARY KEY (WORKBOOK_SEQ)
);

-- 토큰이 겹치면 남의 문서가 열린다. NULL 은 여럿이어도 된다 -- 세 벤더 모두
-- 유니크 인덱스에서 NULL 을 중복으로 보지 않는다.
CREATE UNIQUE INDEX UKC00_WORKBOOK_MASTER01
    ON wo_workbook_master (SHARE_TOKEN);


CREATE TABLE wo_workbook_sheet_line (
    SHEET_SEQ       BIGINT GENERATED ALWAYS AS IDENTITY,
    WORKBOOK_SEQ    BIGINT        NOT NULL,
    SHEET_NAME      VARCHAR(4000) NOT NULL,
    SHEET_NO        INT           NOT NULL,
    MAX_ROW_NO      INT           DEFAULT 0 NOT NULL,
    MAX_COLUMN_NO   INT           DEFAULT 0 NOT NULL,
    -- 격자선(눈금선)을 보일지. 엑셀도 문서에 담는 설정이라 화면에만 두지 않는다.
    GRIDLINE_YN     CHAR(1)       DEFAULT 'Y' NOT NULL,
    -- 틀 고정. 위에서 몇 행, 왼쪽에서 몇 열을 붙박아 둘지. 0 이면 없다.
    FREEZE_ROW_NO   INT           DEFAULT 0 NOT NULL,
    FREEZE_COLUMN_NO INT          DEFAULT 0 NOT NULL,
    TAB_COLOR       VARCHAR(20)   NULL,
    FILTER_RANGE    VARCHAR(50)   NULL,
    INPUT_DATETIME  TIMESTAMP(3)  NULL,
    INPUT_USER       VARCHAR(50)   NULL,
    MOD_DATETIME    TIMESTAMP(3)  NULL,
    MOD_USER         VARCHAR(50)   NULL,
    DEL_DATETIME    TIMESTAMP(3)  NULL,
    DEL_USER         VARCHAR(50)   NULL,
    CONSTRAINT pk_workbook_sheet_line PRIMARY KEY (SHEET_SEQ),
    CONSTRAINT fk_workbook_sheet_line01 FOREIGN KEY (WORKBOOK_SEQ)
        REFERENCES wo_workbook_master (WORKBOOK_SEQ),
    CONSTRAINT UKC00_WORKBOOK_SHEET_LINE01 UNIQUE (WORKBOOK_SEQ, SHEET_NO)
);

CREATE TABLE wo_cell_style_master (
    STYLE_SEQ       BIGINT GENERATED ALWAYS AS IDENTITY,
    WORKBOOK_SEQ    BIGINT        NOT NULL,
    STYLE_CODE      CHAR(32)      NOT NULL,
    STYLE_VALUE     CLOB          NOT NULL,
    INPUT_DATETIME  TIMESTAMP(3)  NULL,
    INPUT_USER       VARCHAR(50)   NULL,
    MOD_DATETIME    TIMESTAMP(3)  NULL,
    MOD_USER         VARCHAR(50)   NULL,
    DEL_DATETIME    TIMESTAMP(3)  NULL,
    DEL_USER         VARCHAR(50)   NULL,
    CONSTRAINT pk_cell_style_master PRIMARY KEY (STYLE_SEQ),
    CONSTRAINT fk_cell_style_master01 FOREIGN KEY (WORKBOOK_SEQ)
        REFERENCES wo_workbook_master (WORKBOOK_SEQ),
    CONSTRAINT UKC00_CELL_STYLE_MASTER01 UNIQUE (WORKBOOK_SEQ, STYLE_CODE)
);

CREATE TABLE wo_sheet_cell_line (
    SHEET_SEQ       BIGINT         NOT NULL,
    ROW_NO          INT            NOT NULL,
    COLUMN_NO       INT            NOT NULL,
    VALUE_TYPE      VARCHAR(20)    NOT NULL,
    TEXT_VALUE      CLOB           NULL,
    NUMBER_VALUE    NUMERIC(28,10) NULL,
    DATETIME_VALUE  TIMESTAMP(3)   NULL,
    BOOLEAN_YN      CHAR(1)        NULL,
    FORMULA_VALUE   CLOB           NULL,
    STYLE_SEQ       BIGINT         NULL,
    INPUT_DATETIME  TIMESTAMP(3)   NULL,
    INPUT_USER       VARCHAR(50)    NULL,
    MOD_DATETIME    TIMESTAMP(3)   NULL,
    MOD_USER         VARCHAR(50)    NULL,
    DEL_DATETIME    TIMESTAMP(3)   NULL,
    DEL_USER         VARCHAR(50)    NULL,
    CONSTRAINT pk_sheet_cell_line PRIMARY KEY (SHEET_SEQ, ROW_NO, COLUMN_NO),
    CONSTRAINT fk_sheet_cell_line01 FOREIGN KEY (SHEET_SEQ)
        REFERENCES wo_workbook_sheet_line (SHEET_SEQ),
    CONSTRAINT fk_sheet_cell_line02 FOREIGN KEY (STYLE_SEQ)
        REFERENCES wo_cell_style_master (STYLE_SEQ)
);

CREATE TABLE wo_sheet_merge_line (
    MERGE_SEQ       BIGINT GENERATED ALWAYS AS IDENTITY,
    SHEET_SEQ       BIGINT        NOT NULL,
    FROM_ROW_NO     INT           NOT NULL,
    FROM_COLUMN_NO  INT           NOT NULL,
    TO_ROW_NO       INT           NOT NULL,
    TO_COLUMN_NO    INT           NOT NULL,
    INPUT_DATETIME  TIMESTAMP(3)  NULL,
    INPUT_USER       VARCHAR(50)   NULL,
    MOD_DATETIME    TIMESTAMP(3)  NULL,
    MOD_USER         VARCHAR(50)   NULL,
    DEL_DATETIME    TIMESTAMP(3)  NULL,
    DEL_USER         VARCHAR(50)   NULL,
    CONSTRAINT pk_sheet_merge_line PRIMARY KEY (MERGE_SEQ),
    CONSTRAINT fk_sheet_merge_line01 FOREIGN KEY (SHEET_SEQ)
        REFERENCES wo_workbook_sheet_line (SHEET_SEQ)
);

CREATE TABLE wo_sheet_size_line (
    SHEET_SEQ       BIGINT        NOT NULL,
    -- ROW / COLUMN. 행과 열은 모양이 같아 표를 나누지 않고 구분자를 둔다.
    AXIS_TYPE       VARCHAR(20)   NOT NULL,
    INDEX_NO        INT           NOT NULL,
    SIZE_PX         INT           NOT NULL,
    INPUT_DATETIME  TIMESTAMP(3)  NULL,
    INPUT_USER       VARCHAR(50)   NULL,
    MOD_DATETIME    TIMESTAMP(3)  NULL,
    MOD_USER         VARCHAR(50)   NULL,
    DEL_DATETIME    TIMESTAMP(3)  NULL,
    DEL_USER         VARCHAR(50)   NULL,
    CONSTRAINT pk_sheet_size_line PRIMARY KEY (SHEET_SEQ, AXIS_TYPE, INDEX_NO),
    CONSTRAINT fk_sheet_size_line01 FOREIGN KEY (SHEET_SEQ)
        REFERENCES wo_workbook_sheet_line (SHEET_SEQ)
);

CREATE TABLE wo_sheet_lock_line (
    LOCK_SEQ          BIGINT GENERATED ALWAYS AS IDENTITY,
    SHEET_SEQ         BIGINT        NOT NULL,
    FROM_ROW_NO       INT           NOT NULL,
    FROM_COLUMN_NO    INT           NOT NULL,
    TO_ROW_NO         INT           NOT NULL,
    TO_COLUMN_NO      INT           NOT NULL,
    LOCK_USER          VARCHAR(50)   NOT NULL,
    -- 자리를 잡은 브라우저 탭. 색을 사람이 아니라 탭 단위로 나누기 위한 것이다.
    SESSION_KEY       VARCHAR(64)   NULL,
    ACQUIRE_DATETIME  TIMESTAMP(3)  NOT NULL,
    EXPIRE_DATETIME   TIMESTAMP(3)  NOT NULL,
    INPUT_DATETIME    TIMESTAMP(3)  NULL,
    INPUT_USER         VARCHAR(50)   NULL,
    MOD_DATETIME      TIMESTAMP(3)  NULL,
    MOD_USER           VARCHAR(50)   NULL,
    DEL_DATETIME      TIMESTAMP(3)  NULL,
    DEL_USER           VARCHAR(50)   NULL,
    CONSTRAINT pk_sheet_lock_line PRIMARY KEY (LOCK_SEQ),
    CONSTRAINT fk_sheet_lock_line01 FOREIGN KEY (SHEET_SEQ)
        REFERENCES wo_workbook_sheet_line (SHEET_SEQ)
);
CREATE INDEX ix_sheet_lock_line01
    ON wo_sheet_lock_line (SHEET_SEQ, EXPIRE_DATETIME);

CREATE TABLE wo_cell_change_log (
    LOG_SEQ         BIGINT GENERATED ALWAYS AS IDENTITY,
    SHEET_SEQ       BIGINT        NOT NULL,
    ROW_NO          INT           NOT NULL,
    COLUMN_NO       INT           NOT NULL,
    CHANGE_TYPE     VARCHAR(20)   NOT NULL,
    ORG_VALUE       CLOB          NULL,
    MOD_VALUE       CLOB          NULL,
    INPUT_DATETIME  TIMESTAMP(3)  NULL,
    INPUT_USER       VARCHAR(50)   NULL,
    MOD_DATETIME    TIMESTAMP(3)  NULL,
    MOD_USER         VARCHAR(50)   NULL,
    DEL_DATETIME    TIMESTAMP(3)  NULL,
    DEL_USER         VARCHAR(50)   NULL,
    CONSTRAINT pk_cell_change_log PRIMARY KEY (LOG_SEQ)
);
CREATE INDEX ix_cell_change_log01
    ON wo_cell_change_log (SHEET_SEQ, INPUT_DATETIME DESC);

-- 글 문서 문단의 변경 이력. 셀 이력과 같은 얼개다 - 버전 기록과 되돌리기가 읽는다.
-- 값(TEXT/STYLE/RUNS)을 통째로 담는다. 문단은 셀과 달리 한 줄이 곧 한 덩어리다.
CREATE TABLE wo_doc_change_log (
    LOG_SEQ         BIGINT GENERATED ALWAYS AS IDENTITY,
    WORKBOOK_SEQ    BIGINT        NOT NULL,
    PARAGRAPH_NO    INT           NOT NULL,
    CHANGE_TYPE     VARCHAR(20)   NOT NULL,
    ORG_VALUE       CLOB          NULL,
    MOD_VALUE       CLOB          NULL,
    INPUT_DATETIME  TIMESTAMP(3)  NULL,
    INPUT_USER       VARCHAR(50)   NULL,
    MOD_DATETIME    TIMESTAMP(3)  NULL,
    MOD_USER         VARCHAR(50)   NULL,
    DEL_DATETIME    TIMESTAMP(3)  NULL,
    DEL_USER         VARCHAR(50)   NULL,
    CONSTRAINT pk_doc_change_log PRIMARY KEY (LOG_SEQ)
);
CREATE INDEX ix_doc_change_log01
    ON wo_doc_change_log (WORKBOOK_SEQ, INPUT_DATETIME DESC);

-- 외부 시스템이 넘긴 문서의 보기 세션. 토큰은 해시로만 둔다 - 평문으로 두면
-- 관리자 화면·백업·로그에 남는 것이 그대로 열쇠가 된다.
CREATE TABLE wo_view_session (
    SESSION_SEQ     BIGINT GENERATED ALWAYS AS IDENTITY,
    TOKEN_HASH      VARCHAR(64)   NOT NULL,
    FILE_NAME       VARCHAR(400)  NOT NULL,
    DOC_TYPE        VARCHAR(10)   NOT NULL,
    FILE_HASH       VARCHAR(64)   NOT NULL,
    BYTE_SIZE       BIGINT        NULL,
    CALLER_SYSTEM   VARCHAR(100)  NULL,
    VIEWER_NAME     VARCHAR(200)  NULL,
    ENCRYPTED_YN    CHAR(1)       DEFAULT 'N' NOT NULL,
    STATE           VARCHAR(20)   NOT NULL,
    EXPIRES_AT      TIMESTAMP(3)  NOT NULL,
    OPENED_AT       TIMESTAMP(3)  NULL,
    LAST_SEEN_AT    TIMESTAMP(3)  NULL,
    -- 창 세션 쿠키 난수의 해시. 발급 시점엔 비어 있고 열리는 순간 채워진다.
    -- SESSION_SEQ 를 쿠키에 그대로 담으면 순번을 찍어 남의 문서를 열어 볼 수
    -- 있다(IDOR) - 보기 토큰과 같은 방식으로 막는다.
    SESSION_KEY_HASH VARCHAR(64)  NULL,
    INPUT_DATETIME  TIMESTAMP(3)  NULL,
    INPUT_USER       VARCHAR(50)   NULL,
    MOD_DATETIME    TIMESTAMP(3)  NULL,
    MOD_USER         VARCHAR(50)   NULL,
    DEL_DATETIME    TIMESTAMP(3)  NULL,
    DEL_USER         VARCHAR(50)   NULL,
    CONSTRAINT pk_view_session PRIMARY KEY (SESSION_SEQ)
);
CREATE UNIQUE INDEX ix_view_session01 ON wo_view_session (TOKEN_HASH);
-- 창 세션 쿠키 해시로 찾는 길. 새로고침과 스냅샷 요청이 모두 이 칸으로 세션을
-- 잡는다 - 열려 있는 세션이 쌓이면 그때마다 표를 통째로 훑게 된다.
CREATE INDEX ix_view_session02 ON wo_view_session (SESSION_KEY_HASH);

-- 변환 결과 캐시. 열쇠는 파일 바이트의 해시라 같은 문서는 한 번만 변환한다.
-- 원본 바이트는 담지 않는다.
CREATE TABLE wo_view_doc_cache (
    FILE_HASH       VARCHAR(64)   NOT NULL,
    DOC_TYPE        VARCHAR(10)   NOT NULL,
    SNAPSHOT_GZIP   VARBINARY(104857600) NOT NULL,
    BYTE_SIZE       BIGINT        NULL,
    MADE_MS         INT           NULL,
    ENCRYPTED_YN    CHAR(1)       DEFAULT 'N' NOT NULL,
    INPUT_DATETIME  TIMESTAMP(3)  NULL,
    INPUT_USER       VARCHAR(50)   NULL,
    MOD_DATETIME    TIMESTAMP(3)  NULL,
    MOD_USER         VARCHAR(50)   NULL,
    DEL_DATETIME    TIMESTAMP(3)  NULL,
    DEL_USER         VARCHAR(50)   NULL,
    CONSTRAINT pk_view_doc_cache PRIMARY KEY (FILE_HASH)
);

-- 이력. 요청·열람·오류·관리자 접근을 한 표에 모은다 - 나누면 관리자 화면에서
-- 다시 합쳐야 한다.
CREATE TABLE wo_view_event_log (
    EVENT_SEQ       BIGINT GENERATED ALWAYS AS IDENTITY,
    EVENT_TYPE      VARCHAR(20)   NOT NULL,
    SESSION_SEQ     BIGINT        NULL,
    CALLER_SYSTEM   VARCHAR(100)  NULL,
    FILE_NAME       VARCHAR(400)  NULL,
    BYTE_SIZE       BIGINT        NULL,
    ENCRYPTED_YN    CHAR(1)       NULL,
    VIEWER_NAME     VARCHAR(200)  NULL,
    TOOK_MS         INT           NULL,
    RESULT_CODE     VARCHAR(30)   NULL,
    MESSAGE         VARCHAR(1000) NULL,
    CLIENT_IP       VARCHAR(50)   NULL,
    USER_AGENT      VARCHAR(400)  NULL,
    INPUT_DATETIME  TIMESTAMP(3)  NULL,
    INPUT_USER       VARCHAR(50)   NULL,
    MOD_DATETIME    TIMESTAMP(3)  NULL,
    MOD_USER         VARCHAR(50)   NULL,
    DEL_DATETIME    TIMESTAMP(3)  NULL,
    DEL_USER         VARCHAR(50)   NULL,
    CONSTRAINT pk_view_event_log PRIMARY KEY (EVENT_SEQ)
);
CREATE INDEX ix_view_event_log01 ON wo_view_event_log (INPUT_DATETIME DESC);

CREATE TABLE wo_workbook_member_map (
    WORKBOOK_SEQ    BIGINT        NOT NULL,
    MEMBER_USER      VARCHAR(50)   NOT NULL,
    ROLE_CODE       VARCHAR(20)   NOT NULL,
    USE_YN          CHAR(1)       DEFAULT 'Y' NOT NULL,
    INPUT_DATETIME  TIMESTAMP(3)  NULL,
    INPUT_USER       VARCHAR(50)   NULL,
    MOD_DATETIME    TIMESTAMP(3)  NULL,
    MOD_USER         VARCHAR(50)   NULL,
    DEL_DATETIME    TIMESTAMP(3)  NULL,
    DEL_USER         VARCHAR(50)   NULL,
    CONSTRAINT pk_workbook_member_map PRIMARY KEY (WORKBOOK_SEQ, MEMBER_USER),
    CONSTRAINT fk_workbook_member_map01 FOREIGN KEY (WORKBOOK_SEQ)
        REFERENCES wo_workbook_master (WORKBOOK_SEQ)
);

-- 이미지 바이트. 문서 단위로 보관하고 SHA-256 해시로 중복을 제거한다.
-- 같은 그림을 열 번 붙여넣어도 바이트는 한 벌이다.
CREATE TABLE wo_sheet_image_master (
    IMAGE_SEQ       BIGINT GENERATED ALWAYS AS IDENTITY,
    WORKBOOK_SEQ    BIGINT        NOT NULL,
    IMAGE_HASH      CHAR(64)      NOT NULL,
    MIME_TYPE       VARCHAR(50)   NOT NULL,
    IMAGE_SIZE      INT           NOT NULL,
    -- BLOB 이 아니라 VARBINARY 다. MODE=PostgreSQL 인 H2 는 BLOB 을 모른다
    -- ("Unknown data type: BLOB", 2026-08-05 실측). PostgreSQL 에 BLOB 이 없어서다.
    -- CLOB 은 같은 모드에서도 받아들여지는 비대칭이 있으니 흉내내 바꾸지 않는다.
    -- 길이는 상한(5MB)보다 넉넉히 잡는다. H2 는 미리 잡아두지 않는다.
    IMAGE_DATA      VARBINARY(104857600) NOT NULL,
    INPUT_DATETIME  TIMESTAMP(3)  NULL,
    INPUT_USER       VARCHAR(50)   NULL,
    MOD_DATETIME    TIMESTAMP(3)  NULL,
    MOD_USER         VARCHAR(50)   NULL,
    DEL_DATETIME    TIMESTAMP(3)  NULL,
    DEL_USER         VARCHAR(50)   NULL,
    CONSTRAINT pk_sheet_image_master PRIMARY KEY (IMAGE_SEQ),
    CONSTRAINT fk_sheet_image_master01 FOREIGN KEY (WORKBOOK_SEQ)
        REFERENCES wo_workbook_master (WORKBOOK_SEQ),
    CONSTRAINT UKC00_SHEET_IMAGE_MASTER01 UNIQUE (WORKBOOK_SEQ, IMAGE_HASH)
);

-- 시트에 놓인 그림 한 장이 한 행이다. 오프셋 단위는 픽셀이다 -- Univer 가
-- 픽셀을 쓰고 이 값의 주 소비자이기 때문이다. POI 의 EMU 는 경계에서 걷어낸다.
--
-- DRAWING_EXTRA 는 컬럼으로 빼지 않은 Univer 필드(자르기·도형·회전)를 담는다.
-- 그냥 버리면 사용자가 자른 그림이 새로고침 때 조용히 원래대로 돌아간다.
-- 파일 머리말대로 JSON 은 CLOB 으로 둔다.
CREATE TABLE wo_sheet_drawing_line (
    DRAWING_SEQ         BIGINT GENERATED ALWAYS AS IDENTITY,
    SHEET_SEQ           BIGINT        NOT NULL,
    DRAWING_ID          VARCHAR(50)   NOT NULL,
    IMAGE_SEQ           BIGINT        NOT NULL,
    FROM_ROW_NO         INT           NOT NULL,
    FROM_ROW_OFFSET     INT           NOT NULL,
    FROM_COLUMN_NO      INT           NOT NULL,
    FROM_COLUMN_OFFSET  INT           NOT NULL,
    TO_ROW_NO           INT           NOT NULL,
    TO_ROW_OFFSET       INT           NOT NULL,
    TO_COLUMN_NO        INT           NOT NULL,
    TO_COLUMN_OFFSET    INT           NOT NULL,
    ANCHOR_TYPE         VARCHAR(20)   NOT NULL,
    Z_ORDER             INT           NOT NULL,
    DRAWING_EXTRA       CLOB          NULL,
    INPUT_DATETIME      TIMESTAMP(3)  NULL,
    INPUT_USER           VARCHAR(50)   NULL,
    MOD_DATETIME        TIMESTAMP(3)  NULL,
    MOD_USER             VARCHAR(50)   NULL,
    DEL_DATETIME        TIMESTAMP(3)  NULL,
    DEL_USER             VARCHAR(50)   NULL,
    CONSTRAINT pk_sheet_drawing_line PRIMARY KEY (DRAWING_SEQ),
    CONSTRAINT fk_sheet_drawing_line01 FOREIGN KEY (SHEET_SEQ)
        REFERENCES wo_workbook_sheet_line (SHEET_SEQ),
    CONSTRAINT fk_sheet_drawing_line02 FOREIGN KEY (IMAGE_SEQ)
        REFERENCES wo_sheet_image_master (IMAGE_SEQ),
    CONSTRAINT UKC00_SHEET_DRAWING_LINE01 UNIQUE (SHEET_SEQ, DRAWING_ID)
);

-- 문서 리소스. Univer 의 셀 값이 아닌 상태(필터·조건부 서식·데이터 확인·메모)를
-- 이름표 붙인 JSON 한 덩어리로 담는다. 기능마다 표를 새로 파지 않는다.
CREATE TABLE wo_workbook_resource_line (
    WORKBOOK_SEQ    BIGINT        NOT NULL,
    RESOURCE_NAME   VARCHAR(100)  NOT NULL,
    RESOURCE_JSON   CLOB          NOT NULL,
    INPUT_DATETIME  TIMESTAMP(3)  NULL,
    INPUT_USER       VARCHAR(50)   NULL,
    MOD_DATETIME    TIMESTAMP(3)  NULL,
    MOD_USER         VARCHAR(50)   NULL,
    -- 지우기는 물리 삭제다(리소스는 통째로 갈아 끼운다). 열은 사내 표준이라 둔다.
    DEL_DATETIME    TIMESTAMP(3)  NULL,
    DEL_USER         VARCHAR(50)   NULL,
    CONSTRAINT pk_workbook_resource_line PRIMARY KEY (WORKBOOK_SEQ, RESOURCE_NAME),
    CONSTRAINT fk_workbook_resource_line01 FOREIGN KEY (WORKBOOK_SEQ)
        REFERENCES wo_workbook_master (WORKBOOK_SEQ)
);

-- 글 문서의 문단. 문단 한 줄에 한 행이다.
--
-- 통째로 담지 않는 이유: 둘이 동시에 고칠 때 나중 저장이 앞선 것을 덮는다.
-- 시트에서 셀 단위로 한 것과 같은 얼개다 - 잠금·변경이력·실시간이 여기에 붙는다.
CREATE TABLE wo_doc_paragraph_line (
    WORKBOOK_SEQ    BIGINT        NOT NULL,
    PARAGRAPH_NO    INT           NOT NULL,
    TEXT_VALUE      CLOB          NULL,
    -- 문단 서식(제목·정렬·글머리 기호). Univer 모양 그대로 왕복만 시킨다.
    STYLE_VALUE     CLOB          NULL,
    -- 글자 서식. 문단 안 자리로 적는다 - 문서 전체 자리로 담으면 앞 문단이
    -- 길어질 때마다 뒤쪽이 전부 어긋난다.
    RUNS_VALUE      CLOB          NULL,
    INPUT_DATETIME  TIMESTAMP(3)  NULL,
    INPUT_USER       VARCHAR(50)   NULL,
    MOD_DATETIME    TIMESTAMP(3)  NULL,
    MOD_USER         VARCHAR(50)   NULL,
    DEL_DATETIME    TIMESTAMP(3)  NULL,
    DEL_USER         VARCHAR(50)   NULL,
    CONSTRAINT pk_doc_paragraph_line PRIMARY KEY (WORKBOOK_SEQ, PARAGRAPH_NO),
    CONSTRAINT fk_doc_paragraph_line01 FOREIGN KEY (WORKBOOK_SEQ)
        REFERENCES wo_workbook_master (WORKBOOK_SEQ)
);

CREATE TABLE wo_user_sheet_view (
    USER_KEY          VARCHAR(50)   NOT NULL,
    WORKBOOK_SEQ    BIGINT        NOT NULL,
    SHEET_SEQ       BIGINT        NOT NULL,
    ZOOM_RATIO      NUMERIC(4,2)  DEFAULT 1 NOT NULL,
    SEEN_DATETIME   TIMESTAMP(3)  NOT NULL,
    CONSTRAINT pk_user_sheet_view PRIMARY KEY (USER_KEY, WORKBOOK_SEQ, SHEET_SEQ)
);

CREATE TABLE wo_user_workbook_mark (
    USER_KEY          VARCHAR(50)   NOT NULL,
    WORKBOOK_SEQ    BIGINT        NOT NULL,
    STARRED_YN      CHAR(1)       DEFAULT 'N' NOT NULL,
    COLOR_CODE      VARCHAR(10)   NULL,
    INPUT_DATETIME  TIMESTAMP(3)  NULL,
    INPUT_USER       VARCHAR(50)   NULL,
    MOD_DATETIME    TIMESTAMP(3)  NULL,
    MOD_USER         VARCHAR(50)   NULL,
    DEL_DATETIME    TIMESTAMP(3)  NULL,
    DEL_USER         VARCHAR(50)   NULL,
    CONSTRAINT pk_user_workbook_mark PRIMARY KEY (USER_KEY, WORKBOOK_SEQ)
);

CREATE TABLE wo_user_color_label (
    USER_KEY          VARCHAR(50)   NOT NULL,
    COLOR_CODE      VARCHAR(10)   NOT NULL,
    LABEL_NAME      VARCHAR(20)   NOT NULL,
    INPUT_DATETIME  TIMESTAMP(3)  NULL,
    INPUT_USER       VARCHAR(50)   NULL,
    MOD_DATETIME    TIMESTAMP(3)  NULL,
    MOD_USER         VARCHAR(50)   NULL,
    DEL_DATETIME    TIMESTAMP(3)  NULL,
    DEL_USER         VARCHAR(50)   NULL,
    CONSTRAINT pk_user_color_label PRIMARY KEY (USER_KEY, COLOR_CODE)
);

CREATE TABLE wo_workbook_snapshot (
    SNAPSHOT_SEQ    BIGINT        GENERATED ALWAYS AS IDENTITY,
    WORKBOOK_SEQ    BIGINT        NOT NULL,
    TAKEN_AT        TIMESTAMP(3)  NOT NULL,
    TAKEN_USER       VARCHAR(50)   NOT NULL,
    REASON          VARCHAR(20)   NOT NULL,
    LABEL           VARCHAR(50)   NULL,
    DOC_TYPE        VARCHAR(10)   NOT NULL,
    BODY_SIZE       BIGINT        DEFAULT 0 NOT NULL,
    BODY            VARBINARY     NOT NULL,
    INPUT_DATETIME  TIMESTAMP(3)  NULL,
    INPUT_USER       VARCHAR(50)   NULL,
    MOD_DATETIME    TIMESTAMP(3)  NULL,
    MOD_USER         VARCHAR(50)   NULL,
    DEL_DATETIME    TIMESTAMP(3)  NULL,
    DEL_USER         VARCHAR(50)   NULL,
    CONSTRAINT pk_workbook_snapshot PRIMARY KEY (SNAPSHOT_SEQ),
    CONSTRAINT fk_workbook_snapshot01 FOREIGN KEY (WORKBOOK_SEQ)
        REFERENCES wo_workbook_master (WORKBOOK_SEQ)
);

-- 등록된 사용자와 초대. PostgreSQL 쪽과 컬럼·제약 이름을 같게 둔다.
CREATE TABLE wo_user_account (
    USER_KEY        VARCHAR(50)   NOT NULL,
    EMAIL           VARCHAR(255)  NOT NULL,
    DISPLAY_NAME    VARCHAR(100)  NOT NULL,
    PASSWORD_HASH   VARCHAR(100)  NULL,
    ROLE_CODE       VARCHAR(20)   NOT NULL,
    DISABLED_AT     TIMESTAMP(3)  NULL,
    INPUT_DATETIME  TIMESTAMP(3)  NULL,
    INPUT_USER       VARCHAR(50)   NULL,
    MOD_DATETIME    TIMESTAMP(3)  NULL,
    MOD_USER         VARCHAR(50)   NULL,
    DEL_DATETIME    TIMESTAMP(3)  NULL,
    DEL_USER         VARCHAR(50)   NULL,
    CONSTRAINT pk_user_account PRIMARY KEY (USER_KEY)
);
CREATE UNIQUE INDEX ix_user_account01 ON wo_user_account (EMAIL);

CREATE TABLE wo_user_invite (
    TOKEN_HASH      VARCHAR(64)   NOT NULL,
    USER_KEY        VARCHAR(50)   NOT NULL,
    EXPIRES_AT      TIMESTAMP(3)  NOT NULL,
    USED_AT         TIMESTAMP(3)  NULL,
    INPUT_DATETIME  TIMESTAMP(3)  NULL,
    INPUT_USER       VARCHAR(50)   NULL,
    MOD_DATETIME    TIMESTAMP(3)  NULL,
    MOD_USER         VARCHAR(50)   NULL,
    DEL_DATETIME    TIMESTAMP(3)  NULL,
    DEL_USER         VARCHAR(50)   NULL,
    CONSTRAINT pk_user_invite PRIMARY KEY (TOKEN_HASH),
    CONSTRAINT fk_user_invite01 FOREIGN KEY (USER_KEY)
        REFERENCES wo_user_account (USER_KEY)
);
CREATE INDEX ix_user_invite01 ON wo_user_invite (USER_KEY);
