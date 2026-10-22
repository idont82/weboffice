package weboffice.sheet;

/**
 * 셀 범위. 양 끝을 포함한다(inclusive).
 *
 * <p>겹침 판정을 이 타입에 두는 이유: 잠금 겹침 검사를 자바와 SQL 두 곳에 두면
 * 두 판정이 어긋났을 때 잠금이 새는지 아무도 모른다. 자바 쪽 정의를 단위
 * 테스트로 고정해 두고 쿼리를 그 정의에 맞춘다.
 */
public record CellRange(int fromRowNo, int fromColumnNo, int toRowNo, int toColumnNo) {

    public CellRange {
        if (fromRowNo > toRowNo || fromColumnNo > toColumnNo) {
            throw new IllegalArgumentException(
                    "범위의 시작이 끝보다 크다: (%d,%d)~(%d,%d)"
                            .formatted(fromRowNo, fromColumnNo, toRowNo, toColumnNo));
        }
    }

    public boolean overlaps(CellRange other) {
        return this.fromRowNo <= other.toRowNo
                && other.fromRowNo <= this.toRowNo
                && this.fromColumnNo <= other.toColumnNo
                && other.fromColumnNo <= this.toColumnNo;
    }
}
