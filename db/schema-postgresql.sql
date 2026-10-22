-- PostgreSQL 스키마. 이것이 운영 스키마다.
--
-- docker compose 가 처음 뜰 때 이 파일을 한 번 돌린다. 앞머리에 DROP 이 있으니
-- 이미 자료가 든 DB 에 그대로 돌리지 말 것 - compose 는 자료 디렉터리가 비어
-- 있을 때만 돌린다.
--
-- src/test/resources/db/schema-h2.sql(시험용)과 컬럼 순서·컬럼명·제약 이름을
-- 동일하게 유지한다. 둘이 어긋나면 시험이 통과하는 SQL 이 운영에서 깨진다.
--
-- 미인용 식별자는 PostgreSQL 에서 소문자로 폴딩된다. 큰따옴표를 쓰지 않는다 --
-- 한 번 인용하면 이후 모든 쿼리에서 인용이 강제되고 Oracle 이식성이 깨진다.

--
--

-- 초대가 사용자를 가리키므로 초대를 먼저 지운다.
DROP TABLE IF EXISTS wo_user_invite;
DROP TABLE IF EXISTS wo_user_account;
DROP TABLE IF EXISTS wo_view_event_log;
DROP TABLE IF EXISTS wo_view_session;
DROP TABLE IF EXISTS wo_view_doc_cache;
DROP TABLE IF EXISTS wo_sheet_drawing_line;
DROP TABLE IF EXISTS wo_sheet_image_master;
DROP TABLE IF EXISTS wo_cell_change_log;
DROP TABLE IF EXISTS wo_sheet_size_line;
DROP TABLE IF EXISTS wo_sheet_lock_line;
DROP TABLE IF EXISTS wo_sheet_merge_line;
DROP TABLE IF EXISTS wo_sheet_cell_line;
DROP TABLE IF EXISTS wo_cell_style_master;
DROP TABLE IF EXISTS wo_workbook_sheet_line;
DROP TABLE IF EXISTS wo_workbook_snapshot;
DROP TABLE IF EXISTS wo_user_workbook_mark;
DROP TABLE IF EXISTS wo_user_color_label;
DROP TABLE IF EXISTS wo_user_sheet_view;
DROP TABLE IF EXISTS wo_doc_change_log;
-- 이 둘이 빠져 있었다(2026-10-22, 진짜 PostgreSQL 로 돌려 보고 찾았다).
-- 둘 다 wo_workbook_master 를 참조하므로 그보다 먼저 지워야 한다 - 안 그러면
-- 이미 자료가 든 DB 에 스키마를 다시 올릴 때 "의존하고 있어 삭제할 수 없음"
-- 으로 멈춘다. H2 는 이것을 잡지 못한다.
DROP TABLE IF EXISTS wo_workbook_resource_line;
DROP TABLE IF EXISTS wo_doc_paragraph_line;
DROP TABLE IF EXISTS wo_workbook_member_map;
DROP TABLE IF EXISTS wo_workbook_master;

CREATE TABLE wo_workbook_master (
    WORKBOOK_SEQ    bigint GENERATED ALWAYS AS IDENTITY,
    WORKBOOK_NAME   text          NOT NULL,
    OWNER_USER       varchar(50)   NOT NULL,
    DOC_VER         int           DEFAULT 1 NOT NULL,
    -- 문서 종류. SHEET(시트) 또는 DOC(글). 화면이 무엇을 열지 정한다.
    DOC_TYPE        varchar(10)   DEFAULT 'SHEET' NOT NULL,
    USE_YN          char(1)       DEFAULT 'Y' NOT NULL,
    -- 공개 링크 토큰. 있으면 공개, 없으면 비공개다.
    SHARE_TOKEN     varchar(32)   NULL,
    INPUT_DATETIME  timestamp(3)  NULL,
    INPUT_USER       varchar(50)   NULL,
    MOD_DATETIME    timestamp(3)  NULL,
    MOD_USER         varchar(50)   NULL,
    DEL_DATETIME    timestamp(3)  NULL,
    DEL_USER         varchar(50)   NULL,
    CONSTRAINT pk_workbook_master PRIMARY KEY (WORKBOOK_SEQ)
);

-- 토큰이 겹치면 남의 문서가 열린다. NULL 은 여럿이어도 된다 -- 세 벤더 모두
-- 유니크 인덱스에서 NULL 을 중복으로 보지 않는다.
CREATE UNIQUE INDEX UKC00_WORKBOOK_MASTER01
    ON wo_workbook_master (SHARE_TOKEN);

CREATE TABLE wo_workbook_sheet_line (
    SHEET_SEQ       bigint GENERATED ALWAYS AS IDENTITY,
    WORKBOOK_SEQ    bigint        NOT NULL,
    SHEET_NAME      text          NOT NULL,
    SHEET_NO        int           NOT NULL,
    MAX_ROW_NO      int           DEFAULT 0 NOT NULL,
    MAX_COLUMN_NO   int           DEFAULT 0 NOT NULL,
    -- 격자선(눈금선)을 보일지. 엑셀도 문서에 담는 설정이라 화면에만 두지 않는다.
    GRIDLINE_YN     char(1)       DEFAULT 'Y' NOT NULL,
    -- 틀 고정. 위에서 몇 행, 왼쪽에서 몇 열을 붙박아 둘지. 0 이면 없다.
    FREEZE_ROW_NO   int           DEFAULT 0 NOT NULL,
    FREEZE_COLUMN_NO int          DEFAULT 0 NOT NULL,
    -- 시트 탭 색(#rrggbb). 없으면 색을 안 칠한 것이다.
    TAB_COLOR       varchar(20)   NULL,
    -- 걸러 보기(필터)를 건 자리(A1:B2). 없으면 안 건 것이다.
    FILTER_RANGE    varchar(50)   NULL,
    INPUT_DATETIME  timestamp(3)  NULL,
    INPUT_USER       varchar(50)   NULL,
    MOD_DATETIME    timestamp(3)  NULL,
    MOD_USER         varchar(50)   NULL,
    DEL_DATETIME    timestamp(3)  NULL,
    DEL_USER         varchar(50)   NULL,
    CONSTRAINT pk_workbook_sheet_line PRIMARY KEY (SHEET_SEQ),
    CONSTRAINT fk_workbook_sheet_line01 FOREIGN KEY (WORKBOOK_SEQ)
        REFERENCES wo_workbook_master (WORKBOOK_SEQ),
    CONSTRAINT UKC00_WORKBOOK_SHEET_LINE01 UNIQUE (WORKBOOK_SEQ, SHEET_NO)
);

CREATE TABLE wo_cell_style_master (
    STYLE_SEQ       bigint GENERATED ALWAYS AS IDENTITY,
    WORKBOOK_SEQ    bigint        NOT NULL,
    STYLE_CODE      char(32)      NOT NULL,
    STYLE_VALUE     jsonb         NOT NULL,
    INPUT_DATETIME  timestamp(3)  NULL,
    INPUT_USER       varchar(50)   NULL,
    MOD_DATETIME    timestamp(3)  NULL,
    MOD_USER         varchar(50)   NULL,
    DEL_DATETIME    timestamp(3)  NULL,
    DEL_USER         varchar(50)   NULL,
    CONSTRAINT pk_cell_style_master PRIMARY KEY (STYLE_SEQ),
    CONSTRAINT fk_cell_style_master01 FOREIGN KEY (WORKBOOK_SEQ)
        REFERENCES wo_workbook_master (WORKBOOK_SEQ),
    CONSTRAINT UKC00_CELL_STYLE_MASTER01 UNIQUE (WORKBOOK_SEQ, STYLE_CODE)
);

CREATE TABLE wo_sheet_cell_line (
    SHEET_SEQ       bigint         NOT NULL,
    ROW_NO          int            NOT NULL,
    COLUMN_NO       int            NOT NULL,
    VALUE_TYPE      varchar(20)    NOT NULL,
    TEXT_VALUE      text           NULL,
    NUMBER_VALUE    numeric(28,10) NULL,
    DATETIME_VALUE  timestamp(3)   NULL,
    BOOLEAN_YN      char(1)        NULL,
    FORMULA_VALUE   text           NULL,
    STYLE_SEQ       bigint         NULL,
    INPUT_DATETIME  timestamp(3)   NULL,
    INPUT_USER       varchar(50)    NULL,
    MOD_DATETIME    timestamp(3)   NULL,
    MOD_USER         varchar(50)    NULL,
    DEL_DATETIME    timestamp(3)   NULL,
    DEL_USER         varchar(50)    NULL,
    CONSTRAINT pk_sheet_cell_line PRIMARY KEY (SHEET_SEQ, ROW_NO, COLUMN_NO),
    CONSTRAINT fk_sheet_cell_line01 FOREIGN KEY (SHEET_SEQ)
        REFERENCES wo_workbook_sheet_line (SHEET_SEQ),
    CONSTRAINT fk_sheet_cell_line02 FOREIGN KEY (STYLE_SEQ)
        REFERENCES wo_cell_style_master (STYLE_SEQ)
);

CREATE TABLE wo_sheet_merge_line (
    MERGE_SEQ       bigint GENERATED ALWAYS AS IDENTITY,
    SHEET_SEQ       bigint        NOT NULL,
    FROM_ROW_NO     int           NOT NULL,
    FROM_COLUMN_NO  int           NOT NULL,
    TO_ROW_NO       int           NOT NULL,
    TO_COLUMN_NO    int           NOT NULL,
    INPUT_DATETIME  timestamp(3)  NULL,
    INPUT_USER       varchar(50)   NULL,
    MOD_DATETIME    timestamp(3)  NULL,
    MOD_USER         varchar(50)   NULL,
    DEL_DATETIME    timestamp(3)  NULL,
    DEL_USER         varchar(50)   NULL,
    CONSTRAINT pk_sheet_merge_line PRIMARY KEY (MERGE_SEQ),
    CONSTRAINT fk_sheet_merge_line01 FOREIGN KEY (SHEET_SEQ)
        REFERENCES wo_workbook_sheet_line (SHEET_SEQ)
);

CREATE TABLE wo_sheet_size_line (
    SHEET_SEQ       bigint        NOT NULL,
    -- ROW / COLUMN. 행과 열은 모양이 같아 표를 나누지 않고 구분자를 둔다.
    AXIS_TYPE       varchar(20)   NOT NULL,
    INDEX_NO        int           NOT NULL,
    SIZE_PX         int           NOT NULL,
    INPUT_DATETIME  timestamp(3)  NULL,
    INPUT_USER       varchar(50)   NULL,
    MOD_DATETIME    timestamp(3)  NULL,
    MOD_USER         varchar(50)   NULL,
    DEL_DATETIME    timestamp(3)  NULL,
    DEL_USER         varchar(50)   NULL,
    CONSTRAINT pk_sheet_size_line PRIMARY KEY (SHEET_SEQ, AXIS_TYPE, INDEX_NO),
    CONSTRAINT fk_sheet_size_line01 FOREIGN KEY (SHEET_SEQ)
        REFERENCES wo_workbook_sheet_line (SHEET_SEQ)
);

CREATE TABLE wo_sheet_lock_line (
    LOCK_SEQ          bigint GENERATED ALWAYS AS IDENTITY,
    SHEET_SEQ         bigint        NOT NULL,
    FROM_ROW_NO       int           NOT NULL,
    FROM_COLUMN_NO    int           NOT NULL,
    TO_ROW_NO         int           NOT NULL,
    TO_COLUMN_NO      int           NOT NULL,
    LOCK_USER          varchar(50)   NOT NULL,
    -- 자리를 잡은 브라우저 탭. 색을 사람이 아니라 탭 단위로 나누기 위한 것이다.
    -- 세션 식별자가 아니다 - 그것을 내보내면 세션을 훔칠 수 있다.
    SESSION_KEY       varchar(64)   NULL,
    ACQUIRE_DATETIME  timestamp(3)  NOT NULL,
    EXPIRE_DATETIME   timestamp(3)  NOT NULL,
    INPUT_DATETIME    timestamp(3)  NULL,
    INPUT_USER         varchar(50)   NULL,
    MOD_DATETIME      timestamp(3)  NULL,
    MOD_USER           varchar(50)   NULL,
    DEL_DATETIME      timestamp(3)  NULL,
    DEL_USER           varchar(50)   NULL,
    CONSTRAINT pk_sheet_lock_line PRIMARY KEY (LOCK_SEQ),
    CONSTRAINT fk_sheet_lock_line01 FOREIGN KEY (SHEET_SEQ)
        REFERENCES wo_workbook_sheet_line (SHEET_SEQ)
);
-- 부분 인덱스는 PostgreSQL 전용이다. Oracle 에서는 조건 없는 일반 인덱스로 만든다.
CREATE INDEX ix_sheet_lock_line01
    ON wo_sheet_lock_line (SHEET_SEQ, EXPIRE_DATETIME)
    WHERE DEL_DATETIME IS NULL;

CREATE TABLE wo_cell_change_log (
    LOG_SEQ         bigint GENERATED ALWAYS AS IDENTITY,
    SHEET_SEQ       bigint        NOT NULL,
    ROW_NO          int           NOT NULL,
    COLUMN_NO       int           NOT NULL,
    CHANGE_TYPE     varchar(20)   NOT NULL,
    ORG_VALUE       jsonb         NULL,
    MOD_VALUE       jsonb         NULL,
    INPUT_DATETIME  timestamp(3)  NULL,
    INPUT_USER       varchar(50)   NULL,
    MOD_DATETIME    timestamp(3)  NULL,
    MOD_USER         varchar(50)   NULL,
    DEL_DATETIME    timestamp(3)  NULL,
    DEL_USER         varchar(50)   NULL,
    CONSTRAINT pk_cell_change_log PRIMARY KEY (LOG_SEQ)
);
CREATE INDEX ix_cell_change_log01
    ON wo_cell_change_log (SHEET_SEQ, INPUT_DATETIME DESC);

-- 글 문서 문단의 변경 이력. 셀 이력과 같은 얼개다 - 버전 기록과 되돌리기가 읽는다.
-- 값(TEXT/STYLE/RUNS)을 통째로 담는다. 문단은 셀과 달리 한 줄이 곧 한 덩어리다.
CREATE TABLE wo_doc_change_log (
    LOG_SEQ         bigint GENERATED ALWAYS AS IDENTITY,
    WORKBOOK_SEQ    bigint        NOT NULL,
    PARAGRAPH_NO    int           NOT NULL,
    CHANGE_TYPE     varchar(20)   NOT NULL,
    ORG_VALUE       jsonb         NULL,
    MOD_VALUE       jsonb         NULL,
    INPUT_DATETIME  timestamp(3)  NULL,
    INPUT_USER       varchar(50)   NULL,
    MOD_DATETIME    timestamp(3)  NULL,
    MOD_USER         varchar(50)   NULL,
    DEL_DATETIME    timestamp(3)  NULL,
    DEL_USER         varchar(50)   NULL,
    CONSTRAINT pk_doc_change_log PRIMARY KEY (LOG_SEQ)
);
CREATE INDEX ix_doc_change_log01
    ON wo_doc_change_log (WORKBOOK_SEQ, INPUT_DATETIME DESC);

-- 외부 시스템이 넘긴 문서의 보기 세션. 토큰은 해시로만 둔다 - 평문으로 두면
-- 관리자 화면·백업·로그에 남는 것이 그대로 열쇠가 된다.
CREATE TABLE wo_view_session (
    SESSION_SEQ     bigint GENERATED ALWAYS AS IDENTITY,
    TOKEN_HASH      varchar(64)   NOT NULL,
    FILE_NAME       varchar(400)  NOT NULL,
    DOC_TYPE        varchar(10)   NOT NULL,
    FILE_HASH       varchar(64)   NOT NULL,
    BYTE_SIZE       bigint        NULL,
    CALLER_SYSTEM   varchar(100)  NULL,
    VIEWER_NAME     varchar(200)  NULL,
    ENCRYPTED_YN    char(1)       DEFAULT 'N' NOT NULL,
    STATE           varchar(20)   NOT NULL,
    EXPIRES_AT      timestamp(3)  NOT NULL,
    OPENED_AT       timestamp(3)  NULL,
    LAST_SEEN_AT    timestamp(3)  NULL,
    -- 창 세션 쿠키 난수의 해시. 발급 시점엔 비어 있고 열리는 순간 채워진다.
    -- SESSION_SEQ 를 쿠키에 그대로 담으면 순번을 찍어 남의 문서를 열어 볼 수
    -- 있다(IDOR) - 보기 토큰과 같은 방식으로 막는다.
    SESSION_KEY_HASH varchar(64)  NULL,
    INPUT_DATETIME  timestamp(3)  NULL,
    INPUT_USER       varchar(50)   NULL,
    MOD_DATETIME    timestamp(3)  NULL,
    MOD_USER         varchar(50)   NULL,
    DEL_DATETIME    timestamp(3)  NULL,
    DEL_USER         varchar(50)   NULL,
    CONSTRAINT pk_view_session PRIMARY KEY (SESSION_SEQ)
);
CREATE UNIQUE INDEX ix_view_session01 ON wo_view_session (TOKEN_HASH);
-- 창 세션 쿠키 해시로 찾는 길. 새로고침과 스냅샷 요청이 모두 이 칸으로 세션을
-- 잡는다 - 열려 있는 세션이 쌓이면 그때마다 표를 통째로 훑게 된다.
CREATE INDEX ix_view_session02 ON wo_view_session (SESSION_KEY_HASH);

-- 변환 결과 캐시. 열쇠는 파일 바이트의 해시라 같은 문서는 한 번만 변환한다.
-- 원본 바이트는 담지 않는다.
CREATE TABLE wo_view_doc_cache (
    FILE_HASH       varchar(64)   NOT NULL,
    DOC_TYPE        varchar(10)   NOT NULL,
    SNAPSHOT_GZIP   bytea         NOT NULL,
    BYTE_SIZE       bigint        NULL,
    MADE_MS         int           NULL,
    ENCRYPTED_YN    char(1)       DEFAULT 'N' NOT NULL,
    INPUT_DATETIME  timestamp(3)  NULL,
    INPUT_USER       varchar(50)   NULL,
    MOD_DATETIME    timestamp(3)  NULL,
    MOD_USER         varchar(50)   NULL,
    DEL_DATETIME    timestamp(3)  NULL,
    DEL_USER         varchar(50)   NULL,
    CONSTRAINT pk_view_doc_cache PRIMARY KEY (FILE_HASH)
);

-- 이력. 요청·열람·오류·관리자 접근을 한 표에 모은다 - 나누면 관리자 화면에서
-- 다시 합쳐야 한다.
CREATE TABLE wo_view_event_log (
    EVENT_SEQ       bigint GENERATED ALWAYS AS IDENTITY,
    EVENT_TYPE      varchar(20)   NOT NULL,
    SESSION_SEQ     bigint        NULL,
    CALLER_SYSTEM   varchar(100)  NULL,
    FILE_NAME       varchar(400)  NULL,
    BYTE_SIZE       bigint        NULL,
    ENCRYPTED_YN    char(1)       NULL,
    VIEWER_NAME     varchar(200)  NULL,
    TOOK_MS         int           NULL,
    RESULT_CODE     varchar(30)   NULL,
    MESSAGE         varchar(1000) NULL,
    CLIENT_IP       varchar(50)   NULL,
    USER_AGENT      varchar(400)  NULL,
    INPUT_DATETIME  timestamp(3)  NULL,
    INPUT_USER       varchar(50)   NULL,
    MOD_DATETIME    timestamp(3)  NULL,
    MOD_USER         varchar(50)   NULL,
    DEL_DATETIME    timestamp(3)  NULL,
    DEL_USER         varchar(50)   NULL,
    CONSTRAINT pk_view_event_log PRIMARY KEY (EVENT_SEQ)
);
CREATE INDEX ix_view_event_log01 ON wo_view_event_log (INPUT_DATETIME DESC);

CREATE TABLE wo_workbook_member_map (
    WORKBOOK_SEQ    bigint        NOT NULL,
    MEMBER_USER      varchar(50)   NOT NULL,
    ROLE_CODE       varchar(20)   NOT NULL,
    USE_YN          char(1)       DEFAULT 'Y' NOT NULL,
    INPUT_DATETIME  timestamp(3)  NULL,
    INPUT_USER       varchar(50)   NULL,
    MOD_DATETIME    timestamp(3)  NULL,
    MOD_USER         varchar(50)   NULL,
    DEL_DATETIME    timestamp(3)  NULL,
    DEL_USER         varchar(50)   NULL,
    CONSTRAINT pk_workbook_member_map PRIMARY KEY (WORKBOOK_SEQ, MEMBER_USER),
    CONSTRAINT fk_workbook_member_map01 FOREIGN KEY (WORKBOOK_SEQ)
        REFERENCES wo_workbook_master (WORKBOOK_SEQ)
);

-- 이미지 바이트. 문서 단위로 보관하고 SHA-256 해시로 중복을 제거한다.
-- 같은 그림을 열 번 붙여넣어도 바이트는 한 벌이다.
--
-- 중복 제거를 문서 안으로 한정한 것은 의도다. 전역으로 하면 문서를 지울 때
-- 다른 문서가 쓰는 바이트인지 따져야 하고, 권한 경계를 가로질러 같은 행을
-- 공유하게 된다. 용량을 조금 더 쓰고 경계를 단순하게 둔다.
CREATE TABLE wo_sheet_image_master (
    IMAGE_SEQ       bigint GENERATED ALWAYS AS IDENTITY,
    WORKBOOK_SEQ    bigint        NOT NULL,
    IMAGE_HASH      char(64)      NOT NULL,
    MIME_TYPE       varchar(50)   NOT NULL,
    IMAGE_SIZE      int           NOT NULL,
    IMAGE_DATA      bytea         NOT NULL,
    INPUT_DATETIME  timestamp(3)  NULL,
    INPUT_USER       varchar(50)   NULL,
    MOD_DATETIME    timestamp(3)  NULL,
    MOD_USER         varchar(50)   NULL,
    DEL_DATETIME    timestamp(3)  NULL,
    DEL_USER         varchar(50)   NULL,
    CONSTRAINT pk_sheet_image_master PRIMARY KEY (IMAGE_SEQ),
    CONSTRAINT fk_sheet_image_master01 FOREIGN KEY (WORKBOOK_SEQ)
        REFERENCES wo_workbook_master (WORKBOOK_SEQ),
    CONSTRAINT UKC00_SHEET_IMAGE_MASTER01 UNIQUE (WORKBOOK_SEQ, IMAGE_HASH)
);

-- 시트에 놓인 그림 한 장이 한 행이다. 오프셋 단위는 픽셀이다 -- Univer 가
-- 픽셀을 쓰고 이 값의 주 소비자이기 때문이다. POI 의 EMU 는 경계에서 걷어낸다.
--
-- DRAWING_ID 는 클라이언트(Univer)가 만든 식별자다. 서버 시퀀스와 별개로 둔 것은
-- 클라이언트가 그림을 넣은 직후부터 그것을 키로 갱신·삭제를 보내오기 때문이다.
-- 서버 왕복을 기다려 시퀀스를 받아야 한다면 그 사이의 이동·크기 변경이 유실된다.
--
-- DRAWING_EXTRA 는 컬럼으로 빼지 않은 Univer 필드(자르기·도형·회전)를 담는다.
-- 그냥 버리면 사용자가 자른 그림이 새로고침 때 조용히 원래대로 돌아간다.
-- 컬럼으로 뺀 값은 여기 넣지 않는다 -- 두 군데 있으면 어긋난다.
CREATE TABLE wo_sheet_drawing_line (
    DRAWING_SEQ         bigint GENERATED ALWAYS AS IDENTITY,
    SHEET_SEQ           bigint        NOT NULL,
    DRAWING_ID          varchar(50)   NOT NULL,
    IMAGE_SEQ           bigint        NOT NULL,
    FROM_ROW_NO         int           NOT NULL,
    FROM_ROW_OFFSET     int           NOT NULL,
    FROM_COLUMN_NO      int           NOT NULL,
    FROM_COLUMN_OFFSET  int           NOT NULL,
    TO_ROW_NO           int           NOT NULL,
    TO_ROW_OFFSET       int           NOT NULL,
    TO_COLUMN_NO        int           NOT NULL,
    TO_COLUMN_OFFSET    int           NOT NULL,
    ANCHOR_TYPE         varchar(20)   NOT NULL,
    Z_ORDER             int           NOT NULL,
    DRAWING_EXTRA       jsonb         NULL,
    INPUT_DATETIME      timestamp(3)  NULL,
    INPUT_USER           varchar(50)   NULL,
    MOD_DATETIME        timestamp(3)  NULL,
    MOD_USER             varchar(50)   NULL,
    DEL_DATETIME        timestamp(3)  NULL,
    DEL_USER             varchar(50)   NULL,
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
    WORKBOOK_SEQ    bigint        NOT NULL,
    RESOURCE_NAME   varchar(100)  NOT NULL,
    RESOURCE_JSON   text          NOT NULL,
    INPUT_DATETIME  timestamp(3)  NULL,
    INPUT_USER       varchar(50)   NULL,
    MOD_DATETIME    timestamp(3)  NULL,
    MOD_USER         varchar(50)   NULL,
    -- 지우기는 물리 삭제다(리소스는 통째로 갈아 끼운다). 열은 사내 표준이라 둔다.
    DEL_DATETIME    timestamp(3)  NULL,
    DEL_USER         varchar(50)   NULL,
    CONSTRAINT pk_workbook_resource_line PRIMARY KEY (WORKBOOK_SEQ, RESOURCE_NAME),
    CONSTRAINT fk_workbook_resource_line01 FOREIGN KEY (WORKBOOK_SEQ)
        REFERENCES wo_workbook_master (WORKBOOK_SEQ)
);

-- 글 문서의 문단. 문단 한 줄에 한 행이다.
--
-- 통째로 담지 않는 이유: 둘이 동시에 고칠 때 나중 저장이 앞선 것을 덮는다.
-- 시트에서 셀 단위로 한 것과 같은 얼개다 - 잠금·변경이력·실시간이 여기에 붙는다.
CREATE TABLE wo_doc_paragraph_line (
    WORKBOOK_SEQ    bigint        NOT NULL,
    PARAGRAPH_NO    int           NOT NULL,
    TEXT_VALUE      text          NULL,
    -- 문단 서식(제목·정렬·글머리 기호). Univer 모양 그대로 왕복만 시킨다.
    STYLE_VALUE     text          NULL,
    -- 글자 서식. 문단 안 자리로 적는다 - 문서 전체 자리로 담으면 앞 문단이
    -- 길어질 때마다 뒤쪽이 전부 어긋난다.
    RUNS_VALUE      text          NULL,
    INPUT_DATETIME  timestamp(3)  NULL,
    INPUT_USER       varchar(50)   NULL,
    MOD_DATETIME    timestamp(3)  NULL,
    MOD_USER         varchar(50)   NULL,
    DEL_DATETIME    timestamp(3)  NULL,
    DEL_USER         varchar(50)   NULL,
    CONSTRAINT pk_doc_paragraph_line PRIMARY KEY (WORKBOOK_SEQ, PARAGRAPH_NO),
    CONSTRAINT fk_doc_paragraph_line01 FOREIGN KEY (WORKBOOK_SEQ)
        REFERENCES wo_workbook_master (WORKBOOK_SEQ)
);

-- 사람마다 "어디를 어떻게 보고 있었는가". 문서 내용이 아니라 개인 보기다.
-- 남과 나누지 않는다 - 나누면 같은 문서를 여는 사람끼리 시트와 배율이 서로
-- 끌려다닌다(2026-08-13 결정). 계정을 따라가야 해서 브라우저가 아니라 여기 둔다.
CREATE TABLE wo_user_sheet_view (
    USER_KEY          varchar(50)   NOT NULL,
    WORKBOOK_SEQ    bigint        NOT NULL,
    SHEET_SEQ       bigint        NOT NULL,
    -- 보던 배율. 1 이 100% 다.
    ZOOM_RATIO      numeric(4,2)  DEFAULT 1 NOT NULL,
    -- 마지막으로 이 시트를 본 때. 문서를 다시 열 때 가장 최근 것으로 연다.
    SEEN_DATETIME   timestamp(3)  NOT NULL,
    CONSTRAINT pk_user_sheet_view PRIMARY KEY (USER_KEY, WORKBOOK_SEQ, SHEET_SEQ)
);

-- 사람마다 "이 문서를 어떻게 표시해 두었는가". 별표(즐겨찾기)와 색 딱지다.
-- 개인 것이다 - 내가 칠한 색이 남의 목록에 보이면 색은 공용 규칙이 되어 버리고,
-- 공유받은 남의 문서에는 아예 표시를 못 하게 된다(2026-08-31 결정).
--
-- 별표만 지우고 색은 남길 수 있어야 해서 두 값을 한 행에 둔다. 둘 다 비면
-- 행을 지운다 - 안 지우면 표시한 적 없는 문서와 표시를 뗀 문서가 뒤섞인다.
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

-- 색에 붙인 내 이름. "빨강" 대신 "급함" 처럼 부른다. 안 지으면 색 이름 그대로다.
-- 이것도 개인 것이다 - 색 자체가 개인 것이라서 이름만 공용일 이유가 없다.
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
-- 버전 기록(wo_cell_change_log)과 하는 일이 다르다. 이력은 칸 하나하나의 변화를
-- 세밀하게 남기지만 시트·병합·크기·그림 같은 얼개는 아예 안 남는다 - 시트를 지운
-- 뒤 되돌리면 값만 오고 시트는 안 돌아온다(2026-08-31 설계). 여기는 통째로 담아
-- 통째로 되돌린다.
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

-- ---------------------------------------------------------------------------
-- 등록된 사용자. weboffice.auth.mode: registered 일 때만 쓴다.
--
-- 로그인 없는 모드(open)에서는 이 표가 비어 있다 - 손님은 쿠키의 열쇠가 곧
-- 신원이라 적어 둘 것이 없다.
-- ---------------------------------------------------------------------------
CREATE TABLE wo_user_account (
    -- 소유권·잠금·변경이력이 쓰는 신원 열쇠. 다른 표의 *_USER 컬럼에 들어간다.
    USER_KEY        varchar(50)   NOT NULL,
    -- 로그인에 쓰는 이메일. 소문자로 맞춰 넣는다 - 대소문자가 다른 같은
    -- 주소로 두 계정이 생기면 둘 다 "내 계정" 으로 보인다.
    EMAIL           varchar(255)  NOT NULL,
    DISPLAY_NAME    varchar(100)  NOT NULL,
    -- BCrypt 해시. 초대를 받기 전에는 NULL 이고 그동안은 로그인할 수 없다.
    -- 평문은 어디에도 적지 않는다.
    PASSWORD_HASH   varchar(100)  NULL,
    -- ADMIN · USER
    ROLE_CODE       varchar(20)   NOT NULL,
    -- 막은 시각. 지우지 않고 막는다 - 지우면 그가 만든 문서의 소유자가 사라진다.
    DISABLED_AT     timestamp(3)  NULL,
    INPUT_DATETIME  timestamp(3)  NULL,
    INPUT_USER       varchar(50)   NULL,
    MOD_DATETIME    timestamp(3)  NULL,
    MOD_USER         varchar(50)   NULL,
    -- 감사 컬럼 여섯을 다 둔다(SchemaSmokeTest 가 본다). 막기(DISABLED_AT)와
    -- 지우기는 다른 일이다 - 막은 사람은 목록에 남고 다시 풀 수 있다.
    DEL_DATETIME    timestamp(3)  NULL,
    DEL_USER         varchar(50)   NULL,
    CONSTRAINT pk_user_account PRIMARY KEY (USER_KEY)
);
-- 같은 이메일로 두 번 등록되지 않게 한다.
CREATE UNIQUE INDEX ix_user_account01 ON wo_user_account (EMAIL);

-- ---------------------------------------------------------------------------
-- 초대. 등록된 사람이 처음 비밀번호를 정하는 1회용 표다.
--
-- 토큰 자체를 적지 않고 해시만 적는다 - 이 표가 새어도 남의 계정을 가져갈 수
-- 없어야 한다. 평문 토큰은 발급한 그 순간 화면과 메일로만 나간다.
-- ---------------------------------------------------------------------------
CREATE TABLE wo_user_invite (
    TOKEN_HASH      varchar(64)   NOT NULL,
    USER_KEY        varchar(50)   NOT NULL,
    EXPIRES_AT      timestamp(3)  NOT NULL,
    -- 쓴 시각. 채워지면 다시 못 쓴다.
    USED_AT         timestamp(3)  NULL,
    INPUT_DATETIME  timestamp(3)  NULL,
    INPUT_USER       varchar(50)   NULL,
    MOD_DATETIME    timestamp(3)  NULL,
    MOD_USER         varchar(50)   NULL,
    DEL_DATETIME    timestamp(3)  NULL,
    DEL_USER         varchar(50)   NULL,
    CONSTRAINT pk_user_invite PRIMARY KEY (TOKEN_HASH),
    CONSTRAINT fk_user_invite01 FOREIGN KEY (USER_KEY)
        REFERENCES wo_user_account (USER_KEY)
);
CREATE INDEX ix_user_invite01 ON wo_user_invite (USER_KEY);
