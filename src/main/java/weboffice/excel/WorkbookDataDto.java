package weboffice.excel;

import java.util.List;

/** 문서 한 개의 전 시트. 첫 시트만 다루면 export 때 나머지 시트가 소실된다. */
public record WorkbookDataDto(String workbookName, List<SheetDto> sheets) {
}
