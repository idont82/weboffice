package weboffice.excel;

public record MergeRangeDto(
        int fromRowNo,
        int fromColumnNo,
        int toRowNo,
        int toColumnNo
) {
}
