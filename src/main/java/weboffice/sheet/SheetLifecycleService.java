package weboffice.sheet;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import weboffice.common.AuditUser;

/**
 * 시트를 더하고 이름을 바꾸고 지운다.
 *
 * <p>셀 읽기·쓰기({@link SheetService})와 나눠 둔다. 그쪽은 요청마다 수만 건이
 * 오가는 자리고 이쪽은 어쩌다 한 번이다. 한 클래스에 두면 자주 고치는 쪽을 볼 때
 * 이 규칙들이 늘 눈에 걸린다.
 */
@Service
public class SheetLifecycleService {

    private final SheetMapper sheetMapper;

    public SheetLifecycleService(SheetMapper sheetMapper) {
        this.sheetMapper = sheetMapper;
    }

    @Transactional
    public SheetSummaryDto add(Long workbookSeq, String requestedName, AuditUser user) {
        List<SheetSummaryDto> siblings = sheetMapper.selectSheetsByWorkbook(workbookSeq);

        String name = requestedName == null || requestedName.isBlank()
                ? nextDefaultName(siblings)
                : requestedName.trim();
        requireUnusedName(siblings, name, null);

        // 순번은 마지막 다음이다. 지운 시트의 번호를 다시 쓰지 않는다 - 되살릴 일이
        // 생기면 그때 순번이 겹친다.
        int sheetNo = siblings.stream().mapToInt(SheetSummaryDto::sheetNo).max().orElse(-1) + 1;

        SheetSummaryDto sheet = new SheetSummaryDto(null, workbookSeq, name, sheetNo, 0, 0);
        sheetMapper.insertSheet(sheet, user.userKey());
        return sheet;
    }

    @Transactional
    public void rename(Long sheetSeq, String requestedName, AuditUser user) {
        if (requestedName == null || requestedName.isBlank()) {
            // 이름 없는 탭은 누를 수는 있어도 무엇인지 알 수 없다.
            throw new IllegalArgumentException("시트 이름을 비울 수 없습니다");
        }
        String name = requestedName.trim();

        SheetSummaryDto sheet = sheetMapper.selectSheet(sheetSeq);
        List<SheetSummaryDto> siblings = sheetMapper.selectSheetsByWorkbook(sheet.workbookSeq());
        requireUnusedName(siblings, name, sheetSeq);

        sheetMapper.updateSheetName(sheetSeq, name, user.userKey());
    }

    /**
     * 틀 고정을 저장한다. 0 이면 없다.
     *
     * <p>화면에서만 걸면 새로고침에 풀린다. 엑셀도 문서에 담는 설정이다.
     *
     * <p>음수를 거절하는 이유: 그대로 담으면 화면이 Univer 에 넘겨 무엇이 될지 알 수
     * 없다. 상한은 두지 않는다 - 시트 크기는 자라므로 지금 기준으로 막으면 나중에
     * 멀쩡한 값이 걸린다.
     */
    @Transactional
    public void setFreeze(Long sheetSeq, int rowNo, int columnNo, AuditUser user) {
        if (rowNo < 0 || columnNo < 0) {
            throw new IllegalArgumentException("틀 고정은 음수가 될 수 없습니다");
        }
        sheetMapper.updateFreeze(sheetSeq, rowNo, columnNo, user.userKey());
    }

    /**
     * 시트 탭 색을 저장한다. 빈 값이면 색을 지운다.
     *
     * <p>화면에서만 칠하면 새로고침에 사라진다(2026-08-19 지적). 엑셀도 문서에
     * 담는 설정이다.
     *
     * <p>빈 값이 "지우기" 인 이유: null 은 "안 보냈다" 는 뜻이라 지우기에 쓸 수
     * 없다. 시트 이름·격자선과 한 요청에 실려 오기 때문이다.
     */
    @Transactional
    public void setTabColor(Long sheetSeq, String color, AuditUser user) {
        String trimmed = color == null ? "" : color.trim();
        sheetMapper.updateTabColor(sheetSeq, trimmed.isEmpty() ? null : trimmed, user.userKey());
    }

    /**
     * 걸러 보기(필터)를 건 자리를 저장한다.
     *
     * <p>빈 값은 "걸러 보기 풀기" 다. null 은 "안 보냈다" 라 푸는 데 못 쓴다.
     */
    @Transactional
    public void setFilterRange(Long sheetSeq, String range, AuditUser user) {
        String trimmed = range == null ? "" : range.trim();
        sheetMapper.updateFilterRange(sheetSeq, trimmed.isEmpty() ? null : trimmed,
                user.userKey());
    }

    /**
     * 격자선(눈금선)을 보일지 저장한다.
     *
     * <p>화면에서만 끄면 새로고침에 되살아난다. 엑셀도 문서에 담는 설정이다.
     */
    @Transactional
    public void setGridlines(Long sheetSeq, boolean show, AuditUser user) {
        sheetMapper.updateGridline(sheetSeq, show ? "Y" : "N", user.userKey());
    }

    @Transactional
    public void remove(Long sheetSeq, AuditUser user) {
        SheetSummaryDto sheet = sheetMapper.selectSheet(sheetSeq);
        if (sheetMapper.selectSheetsByWorkbook(sheet.workbookSeq()).size() <= 1) {
            // 시트가 없는 문서는 열 수 없다. 목록에는 남는데 들어가면 빈 화면이다.
            throw new IllegalArgumentException("마지막 시트는 지울 수 없습니다");
        }
        sheetMapper.softDeleteSheet(sheetSeq, user.userKey());
    }

    /**
     * 시트 순서를 바꾼다. 받은 차례대로 0 부터 다시 매긴다.
     *
     * <p>전부 받아야 한다. 일부만 주면 나머지 순번이 어떻게 되는지 아무도 모른다 -
     * 앞에 붙일지 뒤에 붙일지, 빈 자리를 남길지가 부르는 쪽마다 달라진다.
     *
     * <p>순번을 이어 붙이지 않고 0 부터 다시 매기는 이유: 겹치면 목록 순서가
     * 흔들린다. 시트는 많아야 수십 개라 전부 고쳐도 부담이 없다.
     */
    @Transactional
    public void reorder(Long workbookSeq, List<Long> sheetSeqs, AuditUser user) {
        List<SheetSummaryDto> siblings = sheetMapper.selectSheetsByWorkbook(workbookSeq);
        Set<Long> mine = siblings.stream().map(SheetSummaryDto::sheetSeq)
                .collect(Collectors.toSet());

        if (sheetSeqs.size() != mine.size() || !mine.containsAll(sheetSeqs)) {
            // 남의 문서 시트가 섞이면 그 문서의 순번이 이 문서 목록으로 덮인다.
            throw new IllegalArgumentException("이 문서의 시트를 빠짐없이 주어야 합니다");
        }

        // 두 번에 나눠 옮긴다. (WORKBOOK_SEQ, SHEET_NO) 가 유일 제약이라 자리를
        // 맞바꾸는 도중에 번호가 겹쳐 INSERT 가 아니라 UPDATE 에서 터진다.
        // 먼저 아무도 안 쓰는 음수로 비켜 두고, 그다음 0 부터 매긴다.
        int parking = -1;
        for (Long sheetSeq : sheetSeqs) {
            sheetMapper.updateSheetNo(sheetSeq, parking--, user.userKey());
        }
        int sheetNo = 0;
        for (Long sheetSeq : sheetSeqs) {
            sheetMapper.updateSheetNo(sheetSeq, sheetNo++, user.userKey());
        }
    }

    /**
     * 쓰이지 않는 기본 이름을 고른다.
     *
     * <p>개수+1 로 만들면 지웠다 더할 때 이미 있는 이름이 나온다. 비어 있을 때까지
     * 올린다.
     */
    private String nextDefaultName(List<SheetSummaryDto> siblings) {
        for (int i = siblings.size() + 1; ; i++) {
            String candidate = "Sheet" + i;
            if (siblings.stream().noneMatch(s -> s.sheetName().equals(candidate))) {
                return candidate;
            }
        }
    }

    /**
     * 같은 이름을 막는다. 엑셀도 막는다 - 허용하면 어느 탭이 어느 것인지 모른다.
     *
     * <p>{@code self} 는 이름을 바꾸는 당사자다. 자기 이름 그대로 확정하는 일이
     * 흔한데 그것을 오류로 내면 사용자는 무엇이 잘못됐는지 알 수 없다.
     */
    private void requireUnusedName(List<SheetSummaryDto> siblings, String name, Long self) {
        boolean taken = siblings.stream()
                .filter(s -> !s.sheetSeq().equals(self))
                .anyMatch(s -> s.sheetName().equals(name));
        if (taken) {
            throw new IllegalArgumentException("같은 이름의 시트가 있습니다: " + name);
        }
    }
}
