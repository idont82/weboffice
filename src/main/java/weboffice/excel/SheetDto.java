package weboffice.excel;

import java.util.List;

import weboffice.sheet.SheetSizeDto;

public record SheetDto(
        String sheetName,
        List<CellDto> cells,
        List<MergeRangeDto> mergeRanges,
        List<PictureDto> pictures,
        /**
         * 이 시트의 기본 행 높이(px).
         *
         * <p>손댄 줄만 담는 대신 기본 높이를 한 번 알려 준다. 안 그러면 손대지
         * 않은 줄이 우리 기본(20px)으로 그려져 원본보다 짧아진다.
         *
         * <p>이것을 안 두던 때에는 "우리 기본과 다르다" 는 이유로 온 줄을 담았고,
         * 시트 하나가 49,862줄이 됐다(2026-08-18 실측).
         */
        int defaultRowHeightPx,
        /**
         * 기본과 다른 행 높이·열 너비.
         *
         * <p>손댄 줄만 담는다. 시트 하나에 행이 백만 개, 열이 만 육천 개라 전부
         * 적으면 표가 손댄 적 없는 줄로 채워진다 - 화면 저장과 같은 규칙이다.
         */
        List<SheetSizeDto> sizes,
        /**
         * 얼려 둔 줄 수와 칸 수.
         *
         * <p>엑셀은 "이 줄 위쪽을 얼린다" 로 적는다. 0 이면 얼리지 않은 것이다.
         */
        int freezeRowNo,
        int freezeColumnNo,
        /**
         * 필터를 건 자리({@code A1:B2}). 없으면 null 이다.
         *
         * <p>필터를 흘리면 내려받은 파일에서 걸러 보기가 사라진다(2026-08-25 지적).
         */
        String filterRange
) {

    public SheetDto(String sheetName, List<CellDto> cells,
                    List<MergeRangeDto> mergeRanges, List<PictureDto> pictures,
                    int defaultRowHeightPx, List<SheetSizeDto> sizes) {
        this(sheetName, cells, mergeRanges, pictures, defaultRowHeightPx, sizes, 0, 0, null);
    }
    /** 크기가 없던 시절의 호출부를 위한 것. 새 코드는 여섯 인자를 쓴다. */
    public SheetDto(String sheetName, List<CellDto> cells,
                    List<MergeRangeDto> mergeRanges, List<PictureDto> pictures) {
        this(sheetName, cells, mergeRanges, pictures, Px.DEFAULT_ROW_HEIGHT, List.of(),
                0, 0, null);
    }

    /** 기본 행 높이를 모르는 자리(내보내기)를 위한 것. 우리 기본으로 둔다. */
    /** 내려받기에서 쓴다. 기본 행 높이는 우리 기본으로 둔다. */
    public SheetDto(String sheetName, List<CellDto> cells,
                    List<MergeRangeDto> mergeRanges, List<PictureDto> pictures,
                    List<SheetSizeDto> sizes, int freezeRowNo, int freezeColumnNo,
                    String filterRange) {
        this(sheetName, cells, mergeRanges, pictures, Px.DEFAULT_ROW_HEIGHT, sizes,
                freezeRowNo, freezeColumnNo, filterRange);
    }

    public SheetDto(String sheetName, List<CellDto> cells,
                    List<MergeRangeDto> mergeRanges, List<PictureDto> pictures,
                    List<SheetSizeDto> sizes) {
        this(sheetName, cells, mergeRanges, pictures, Px.DEFAULT_ROW_HEIGHT, sizes,
                0, 0, null);
    }
}
