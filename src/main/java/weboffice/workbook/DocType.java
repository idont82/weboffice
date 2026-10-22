package weboffice.workbook;

/**
 * 문서 종류.
 *
 * <p>화면이 무엇을 열지 정한다 - 시트 격자냐 글 편집기냐. 파일 이름이나 시트 개수로
 * 짐작하면 빈 문서에서 갈래가 없다.
 *
 * <p>이름을 "WORKBOOK" 그대로 두고 종류만 나눈다. 표를 새로 파면 공유·잠금·목록이
 * 종류마다 둘이 된다.
 */
public enum DocType {
    /** 시트. 지금까지 만든 문서가 전부 이것이다. */
    SHEET,
    /** 글 문서. */
    DOC,
    /**
     * 발표 자료(슬라이드).
     *
     * <p>보여 주기만 한다. 고쳐 쓰는 화면은 없다 - 쓸 만한 오픈소스 슬라이드
     * 편집기가 Apache/MIT 계열에 없다(2026-08-13 조사).
     */
    SLIDE;

    /** 모르는 값은 거절한다. 담아 두면 화면이 무엇을 열지 몰라 빈 화면이 뜬다. */
    public static DocType of(String value) {
        if (value == null || value.isBlank()) {
            return SHEET;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("모르는 문서 종류입니다: " + value);
        }
    }
}
