package weboffice.sheet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import weboffice.common.Chunks;
import weboffice.common.AfterCommit;
import weboffice.common.AuditUser;
import weboffice.excel.CellDto;
import weboffice.realtime.RealtimeHub;
import weboffice.security.Actor;
import weboffice.security.UserContext;

/**
 * 셀 범위 조회와 delta 저장.
 *
 * <p>저장은 셀 반영과 이력 기록이 한 트랜잭션이다. 갈리면 "누가 언제 무엇을
 * 바꿨는지"를 신뢰할 수 없게 되고, 그건 이 프로젝트의 성공 기준 중 하나다.
 */
@Service
public class SheetService {

    private final CellMapper cellMapper;
    private final ChangeLogMapper changeLogMapper;
    private final SheetMapper sheetMapper;
    private final StyleService styleService;
    private final RealtimeHub realtimeHub;
    /** 묶음 넣기에만 쓴다. 매퍼 하나로는 실행기(ExecutorType)를 못 바꾼다. */
    private final org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory;

    public SheetService(CellMapper cellMapper, ChangeLogMapper changeLogMapper,
                        SheetMapper sheetMapper, StyleService styleService,
                        RealtimeHub realtimeHub,
                        org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory) {
        this.cellMapper = cellMapper;
        this.changeLogMapper = changeLogMapper;
        this.sheetMapper = sheetMapper;
        this.styleService = styleService;
        this.realtimeHub = realtimeHub;
        this.sqlSessionFactory = sqlSessionFactory;
    }

    public List<CellDto> readCells(Long sheetSeq, CellRange range) {
        List<CellRow> rows = cellMapper.selectCellsInRange(sheetSeq, range);
        Map<Long, String> styleBySeq = loadStyles(rows);

        List<CellDto> result = new ArrayList<>(rows.size());
        for (CellRow row : rows) {
            result.add(new CellDto(
                    row.rowNo(), row.columnNo(),
                    CellDto.ValueType.valueOf(row.valueType()),
                    row.textValue(), row.numberValue(), row.datetimeValue(),
                    row.booleanYn(), row.formulaValue(),
                    row.styleSeq() == null ? null : styleBySeq.get(row.styleSeq())));
        }
        return result;
    }

    /**
     * 가져온 파일의 첫 모습을 담는다.
     *
     * <p>변경이력을 남기지 않는다 - 새로 만든 문서에는 "고치기 전" 이 없고, 파일
     * 자체가 첫 모습이라 셀마다 "INSERT" 를 적어 봐야 뜻이 없다. 그런데도 셀 수만큼
     * 이력을 쓰느라 큰 파일 올리기가 30초를 넘었고 이력 표가 셀 표보다 커졌다
     * (2026-08-18 지적: 이력 775MB, 셀 236MB).
     *
     * <p>실시간 알림도 보내지 않는다. 아직 아무도 열지 않은 문서다.
     */
    @Transactional
    public int saveImported(Long sheetSeq, List<CellDto> cells, AuditUser user) {
        if (cells.isEmpty()) {
            return 0;
        }
        Long workbookSeq = sheetMapper.selectSheet(sheetSeq).workbookSeq();

        List<CellRow> rows = new ArrayList<>(cells.size());
        for (CellDto dto : cells) {
            Long styleSeq = styleService.resolveStyle(workbookSeq, dto.styleValue(), user);
            rows.add(new CellRow(dto.rowNo(), dto.columnNo(), dto.valueType().name(),
                    dto.textValue(), dto.numberValue(), dto.datetimeValue(),
                    dto.booleanYn(), dto.formulaValue(), styleSeq));
        }
        insertInBatch(sheetSeq, rows, user);
        growSheetBounds(sheetSeq, cells, user);
        return cells.size();
    }

    /**
     * 셀을 묶음으로 넣는다.
     *
     * <p>매퍼에게 긴 문장을 짓게 하면 파라미터가 백육십만 개가 되고 그것을 문자열로
     * 짓고 묶는 데 시간을 다 쓴다(2026-08-18 실측: DB 자체는 7.6초, 우리를 거치면
     * 18.5초). 한 줄짜리 문장을 묶어 보내면 드라이버가 여러 줄로 다시 엮는다.
     */
    private void insertInBatch(Long sheetSeq, List<CellRow> rows, AuditUser user) {
        try (var session = sqlSessionFactory.openSession(
                org.apache.ibatis.session.ExecutorType.BATCH, false)) {
            CellMapper batched = session.getMapper(CellMapper.class);
            int at = 0;
            for (CellRow row : rows) {
                batched.insertCell(sheetSeq, row, user.userKey());
                if (++at % Chunks.SIZE == 0) {
                    session.flushStatements();
                }
            }
            session.flushStatements();
            session.commit();
        }
    }

    @Transactional
    public int saveDelta(Long sheetSeq, List<CellDto> changed, List<CellDto> deleted,
                         AuditUser user) {
        if (changed.isEmpty() && deleted.isEmpty()) {
            return 0;
        }

        Long workbookSeq = sheetMapper.selectSheet(sheetSeq).workbookSeq();
        Map<CellKey, CellDto> before = loadBefore(sheetSeq, changed, deleted);
        List<ChangeLogRow> logs = new ArrayList<>();

        if (!changed.isEmpty()) {
            List<CellRow> rows = new ArrayList<>(changed.size());
            for (CellDto dto : changed) {
                Long styleSeq = styleService.resolveStyle(workbookSeq, dto.styleValue(), user);
                rows.add(new CellRow(dto.rowNo(), dto.columnNo(), dto.valueType().name(),
                        dto.textValue(), dto.numberValue(), dto.datetimeValue(),
                        dto.booleanYn(), dto.formulaValue(), styleSeq));

                CellDto previous = before.get(CellKey.of(dto));
                logs.add(new ChangeLogRow(sheetSeq, dto.rowNo(), dto.columnNo(),
                        (previous == null ? CellChangeType.INSERT : CellChangeType.UPDATE).name(),
                        CellJson.toJson(previous), CellJson.toJson(dto)));
            }
            for (List<CellRow> chunk : Chunks.of(rows)) {
                cellMapper.upsertCells(sheetSeq, chunk, user.userKey());
            }
        }

        if (!deleted.isEmpty()) {
            List<CellRow> rows = new ArrayList<>(deleted.size());
            for (CellDto dto : deleted) {
                rows.add(new CellRow(dto.rowNo(), dto.columnNo(), dto.valueType().name(),
                        null, null, null, null, null, null));
                logs.add(new ChangeLogRow(sheetSeq, dto.rowNo(), dto.columnNo(),
                        CellChangeType.DELETE.name(),
                        CellJson.toJson(before.get(CellKey.of(dto))), null));
            }
            for (List<CellRow> chunk : Chunks.of(rows)) {
                cellMapper.softDeleteCells(sheetSeq, chunk, user.userKey());
            }
        }

        for (List<ChangeLogRow> chunk : Chunks.of(logs)) {
            changeLogMapper.insertLogs(chunk, user.userKey());
        }
        growSheetBounds(sheetSeq, changed, user);

        // 삭제도 함께 보낸다. 받는 쪽은 값이 없는 셀을 지우면 된다.
        List<CellDto> broadcast = new ArrayList<>(changed);
        broadcast.addAll(deleted);
        // 행위자는 USER_ID 다. 사번은 브라우저로 내보내지 않는다(설계 3절).
        Actor actor = UserContext.currentActor();
        AfterCommit.run(() -> realtimeHub.cellsChanged(sheetSeq, actor, broadcast));
        return changed.size() + deleted.size();
    }

    /** 경계값은 늘리기만 한다. 작은 범위를 저장했다고 시트가 줄어들면 스크롤 범위가 사라진다. */
    private void growSheetBounds(Long sheetSeq, List<CellDto> changed, AuditUser user) {
        if (changed.isEmpty()) {
            return;
        }
        SheetSummaryDto sheet = sheetMapper.selectSheet(sheetSeq);
        int maxRowNo = sheet.maxRowNo();
        int maxColumnNo = sheet.maxColumnNo();
        for (CellDto dto : changed) {
            maxRowNo = Math.max(maxRowNo, dto.rowNo());
            maxColumnNo = Math.max(maxColumnNo, dto.columnNo());
        }
        if (maxRowNo != sheet.maxRowNo() || maxColumnNo != sheet.maxColumnNo()) {
            sheetMapper.updateSheetBounds(sheetSeq, maxRowNo, maxColumnNo, user.userKey());
        }
    }

    /**
     * 변경 대상 전체를 감싸는 사각형을 한 번에 읽는다.
     *
     * <p>셀마다 조회하면 delta 500개에 쿼리가 500번 나간다. 사각형이 실제 변경 셀보다
     * 넓을 수는 있지만 왕복 1회로 끝난다.
     */
    private Map<CellKey, CellDto> loadBefore(Long sheetSeq, List<CellDto> changed,
                                             List<CellDto> deleted) {
        List<CellDto> all = new ArrayList<>(changed);
        all.addAll(deleted);

        int minRow = Integer.MAX_VALUE;
        int minCol = Integer.MAX_VALUE;
        int maxRow = Integer.MIN_VALUE;
        int maxCol = Integer.MIN_VALUE;
        for (CellDto dto : all) {
            minRow = Math.min(minRow, dto.rowNo());
            maxRow = Math.max(maxRow, dto.rowNo());
            minCol = Math.min(minCol, dto.columnNo());
            maxCol = Math.max(maxCol, dto.columnNo());
        }

        Map<CellKey, CellDto> before = new HashMap<>();
        for (CellDto dto : readCells(sheetSeq, new CellRange(minRow, minCol, maxRow, maxCol))) {
            before.put(CellKey.of(dto), dto);
        }
        return before;
    }

    /** 서식은 사전에 모여 있어 셀 수보다 훨씬 적다. STYLE_SEQ 당 한 번만 읽는다. */
    private Map<Long, String> loadStyles(List<CellRow> rows) {
        // 번호를 먼저 모아 한 번에 읽는다. 하나씩 물으면 서식 종류만큼 DB 를 오간다 -
        // 서식이 165가지인 시트에서 여는 시간의 절반이 여기였다(2026-08-20 실측).
        Set<Long> wanted = new LinkedHashSet<>();
        for (CellRow row : rows) {
            if (row.styleSeq() != null) {
                wanted.add(row.styleSeq());
            }
        }
        return styleService.findStyles(wanted);
    }

    private record CellKey(int rowNo, int columnNo) {
        static CellKey of(CellDto dto) {
            return new CellKey(dto.rowNo(), dto.columnNo());
        }
    }
}
