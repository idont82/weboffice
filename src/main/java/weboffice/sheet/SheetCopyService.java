package weboffice.sheet;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import weboffice.common.AuditUser;
import weboffice.drawing.DrawingService;
import weboffice.excel.CellDto;

/**
 * 시트 복사.
 *
 * <p>값만 옮기면 복사가 아니다. 서식·병합·행높이·열너비·그림·틀고정·눈금선·탭 색·
 * 필터까지 함께 간다 - 표를 그대로 두고 숫자만 바꿔 보려고 복사하기 때문이다.
 *
 * <p>셀은 이력 없이 넣는다(가져오기와 같은 길). 갓 만든 시트라 "무엇이 어떻게
 * 바뀌었나" 를 남길 이전 값이 없다.
 */
@Service
public class SheetCopyService {

    private final SheetMapper sheetMapper;
    private final SheetLifecycleService lifecycleService;
    private final SheetService sheetService;
    private final MergeService mergeService;
    private final SheetSizeService sheetSizeService;
    private final DrawingService drawingService;

    public SheetCopyService(SheetMapper sheetMapper, SheetLifecycleService lifecycleService,
                            SheetService sheetService, MergeService mergeService,
                            SheetSizeService sheetSizeService, DrawingService drawingService) {
        this.sheetMapper = sheetMapper;
        this.lifecycleService = lifecycleService;
        this.sheetService = sheetService;
        this.mergeService = mergeService;
        this.sheetSizeService = sheetSizeService;
        this.drawingService = drawingService;
    }

    /**
     * 시트 하나를 같은 문서 안에 복사한다.
     *
     * @param wantedName 새 이름. 비우면 "원래이름 (사본)" 으로 짓는다
     * @return 새로 만든 시트
     */
    @Transactional
    public SheetSummaryDto copy(Long sheetSeq, String wantedName, AuditUser user) {
        SheetSummaryDto source = sheetMapper.selectSheet(sheetSeq);
        if (source == null) {
            throw new IllegalArgumentException("없는 시트입니다: " + sheetSeq);
        }
        String name = wantedName == null || wantedName.isBlank()
                ? unusedName(source.workbookSeq(), source.sheetName())
                : wantedName.trim();

        SheetSummaryDto made = lifecycleService.add(source.workbookSeq(), name, user);
        Long madeSeq = made.sheetSeq();
        String userKey = user.userKey();

        // 보기 상태를 먼저 옮긴다. 값보다 먼저 맞춰 두어야 복사 도중에 열어도
        // 원본과 같은 모양으로 보인다.
        sheetMapper.updateFreeze(madeSeq, source.freezeRowNo(), source.freezeColumnNo(), userKey);
        sheetMapper.updateGridline(madeSeq, source.showGridlines() ? "Y" : "N", userKey);
        sheetMapper.updateTabColor(madeSeq, source.tabColor(), userKey);
        sheetMapper.updateFilterRange(madeSeq, source.filterRange(), userKey);

        List<CellDto> cells = sheetService.readCells(sheetSeq,
                new CellRange(0, 0, source.maxRowNo(), source.maxColumnNo()));
        sheetService.saveImported(madeSeq, cells, user);
        mergeService.replaceAll(madeSeq, mergeService.read(sheetSeq), user);
        sheetSizeService.saveDelta(madeSeq, sheetSizeService.read(sheetSeq), List.of(), user);
        // 그림 자리(drawingId)는 시트마다 따로라 그대로 써도 부딪히지 않는다.
        // 이미지 바이트는 문서 단위라 새로 넣을 것이 없다.
        drawingService.saveDelta(madeSeq, drawingService.readDrawings(sheetSeq), List.of(), user);

        return sheetMapper.selectSheet(madeSeq);
    }

    /**
     * 안 쓰는 이름을 짓는다.
     *
     * <p>"보고서" → "보고서 (사본)" → "보고서 (사본 2)". 엑셀은 "보고서 (2)" 로
     * 짓는데, 그러면 사람이 손으로 지은 "보고서 (2)" 와 구별되지 않는다.
     */
    private String unusedName(Long workbookSeq, String sourceName) {
        List<String> used = sheetMapper.selectSheetsByWorkbook(workbookSeq).stream()
                .map(SheetSummaryDto::sheetName)
                .toList();
        String first = sourceName + " (사본)";
        if (!used.contains(first)) {
            return first;
        }
        for (int at = 2; at < 1000; at += 1) {
            String next = sourceName + " (사본 " + at + ")";
            if (!used.contains(next)) {
                return next;
            }
        }
        throw new IllegalStateException("사본 이름을 짓지 못했습니다: " + sourceName);
    }
}
