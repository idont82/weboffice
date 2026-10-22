package weboffice.sheet;

/** 칸 자리를 사람이 읽는 A1 표기로. 화면(lockUi.ts)이 쓰는 것과 같은 규칙이다. */
public final class CellName {

    private CellName() {
    }

    public static String of(int rowNo, int columnNo) {
        StringBuilder letters = new StringBuilder();
        int at = columnNo;
        while (at >= 0) {
            letters.insert(0, (char) ('A' + at % 26));
            at = at / 26 - 1;
        }
        return letters + String.valueOf(rowNo + 1);
    }
}
