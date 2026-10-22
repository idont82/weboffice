package weboffice.sheet;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * wo_sheet_cell_line 한 행. PK 가 복합 자연키라 생성 키가 없으므로 record 다.
 *
 * <p>{@code excel.CellDto} 와 필드가 거의 같지만 별도 타입인 이유: CellDto 는
 * numberFormat 문자열을 들고, 이 타입은 그것이 해석된 STYLE_SEQ 를 든다.
 * 두 계층의 관심사가 다르다.
 */
public record CellRow(
        int rowNo,
        int columnNo,
        String valueType,
        String textValue,
        BigDecimal numberValue,
        LocalDateTime datetimeValue,
        String booleanYn,
        String formulaValue,
        Long styleSeq
) {
}
