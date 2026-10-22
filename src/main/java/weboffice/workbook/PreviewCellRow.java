package weboffice.workbook;

/** 미리보기 한 칸. 문서마다 첫 시트의 앞부분만 담는다. */
public record PreviewCellRow(Long workbookSeq, int rowNo, int columnNo, String text) {
}
