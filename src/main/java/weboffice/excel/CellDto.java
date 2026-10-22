package weboffice.excel;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 셀 1개의 값 표현. POI · 서버 · 브라우저가 공유하는 유일한 셀 표현이다.
 * 필드명은 물리 컬럼명(ROW_NO 등)의 camelCase 변환형이다.
 */
public record CellDto(
        int rowNo,
        int columnNo,
        ValueType valueType,
        String textValue,
        BigDecimal numberValue,
        LocalDateTime datetimeValue,
        String booleanYn,
        String formulaValue,
        /**
         * 서식 JSON 전체. 화면(Univer)이 쓰는 모양 그대로다.
         *
         * <p>2026-08-06 이전에는 숫자 서식 문자열 하나만 담아 테두리·정렬·글꼴이
         * 저장 뒤에 사라졌다. 서버는 이 문자열을 해석하지 않고 왕복만 시킨다 -
         * 서식 종류가 늘어도 서버를 고칠 일이 없다.
         */
        String styleValue
) {
    /**
     * 셀의 값 종류. 수식 셀도 캐시된 계산 결과의 종류로 분류한다.
     * 즉 valueType 은 "이 셀이 담은 값이 무엇인가"이고,
     * formulaValue 가 null 이 아니면 "그 값이 수식으로 계산된 것"이다.
     */
    public enum ValueType {
        STRING, NUMBER, DATETIME, BOOLEAN, ERROR
    }
}
