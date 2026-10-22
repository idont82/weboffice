package weboffice.workbook;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import weboffice.common.AuditUser;
import weboffice.security.DirectoryUser;
import weboffice.security.UserDirectory;
import weboffice.sheet.SheetMapper;
import weboffice.sheet.SheetSummaryDto;

@Service
public class WorkbookService {

    private final WorkbookMapper workbookMapper;
    private final MemberMapper memberMapper;
    private final SheetMapper sheetMapper;
    private final UserDirectory userDirectory;
    private final WorkbookMarkService markService;

    public WorkbookService(WorkbookMapper workbookMapper, MemberMapper memberMapper,
                           SheetMapper sheetMapper,
                           UserDirectory userDirectory,
                           WorkbookMarkService markService) {
        this.workbookMapper = workbookMapper;
        this.memberMapper = memberMapper;
        this.sheetMapper = sheetMapper;
        this.userDirectory = userDirectory;
        this.markService = markService;
    }

    /**
     * 문서 한 건.
     *
     * <p>목록으로는 부족하다. 공개 링크로 들어온 사람은 소유자도 구성원도 아니라
     * 목록에 그 문서가 없는데, 화면은 제목과 자기 역할을 알아야 한다.
     *
     * <p>역할은 부르는 쪽이 이미 권한 판정을 하며 얻은 것을 넘겨받는다. 여기서 다시
     * 판정하면 같은 질의를 두 번 한다.
     */
    public WorkbookSummaryDto summaryOf(Long workbookSeq, WorkbookRole role) {
        WorkbookDto workbook = workbookMapper.selectWorkbook(workbookSeq);
        Map<String, String> namesByUserKey = lookUpNames(Set.of(workbook.ownerUser()));
        return new WorkbookSummaryDto(
                workbook.workbookSeq(), workbook.workbookName(),
                namesByUserKey.get(workbook.ownerUser()), role.name(),
                memberMapper.selectMembers(workbookSeq).size(),
                // 문서 하나를 여는 길이라 미리보기가 필요 없다 - 화면이 이미 셀을 읽는다.
                workbook.shareToken() != null, null, List.of(), workbook.docType(),
                // 문서 하나를 여는 길에는 별표·색이 쓰이지 않는다. 목록 화면 것이다.
                false, null);
    }

    /**
     * 내 문서와 공유받은 문서.
     *
     * <p>소유자 이름은 인명부에서 한 번에 채운다. 줄마다 조회하면 문서 스무 개에
     * 스무 번 인명부를 두드린다.
     */
    public Map<String, List<WorkbookSummaryDto>> listFor(String userKey) {
        List<WorkbookRow> mine = workbookMapper.selectWorkbooksByOwner(userKey, UNTITLED);
        List<WorkbookRow> shared = workbookMapper.selectWorkbooksByMember(userKey);

        Map<String, String> namesByUserKey = lookUpNames(
                Stream.concat(mine.stream(), shared.stream())
                        .map(WorkbookRow::ownerUser)
                        .collect(Collectors.toSet()));

        Map<Long, List<List<String>>> previews = loadPreviews(
                Stream.concat(mine.stream(), shared.stream())
                        .map(WorkbookRow::workbookSeq).toList());

        // 내 별표·색. 한 번에 가져와 줄마다 얹는다.
        Map<Long, WorkbookMarkRow> marks = markService.marksOf(userKey);

        return Map.of(
                "mine", toSummaries(mine, namesByUserKey, previews, marks),
                "shared", toSummaries(shared, namesByUserKey, previews, marks));
    }

    /**
     * 이름이나 내용으로 문서를 찾는다.
     *
     * <p>목록과 같은 모양({@code mine}·{@code shared})으로 준다 - 화면이 같은 카드를
     * 그대로 그린다. 어디서 맞았는지와 맞은 토막을 함께 준다.
     */
    public Map<String, List<WorkbookHitDto>> search(String userKey, String words) {
        String wanted = words == null ? "" : words.trim();
        if (wanted.isEmpty()) {
            return Map.of("mine", List.of(), "shared", List.of());
        }
        List<WorkbookSearchRow> rows = workbookMapper.searchWorkbooks(
                userKey, "%" + wanted.toUpperCase(java.util.Locale.ROOT) + "%");

        Map<String, String> namesByUserKey = lookUpNames(
                rows.stream().map(WorkbookSearchRow::ownerUser).collect(Collectors.toSet()));
        Map<Long, WorkbookMarkRow> marks = markService.marksOf(userKey);

        List<WorkbookHitDto> mine = new java.util.ArrayList<>();
        List<WorkbookHitDto> shared = new java.util.ArrayList<>();
        for (WorkbookSearchRow row : rows) {
            WorkbookHitDto hit = toHit(row, wanted, namesByUserKey, marks);
            (userKey.equals(row.ownerUser()) ? mine : shared).add(hit);
        }
        return Map.of("mine", mine, "shared", shared);
    }

    /** 이어진 공백과 줄바꿈. 목록은 한 줄만 보여 준다. */
    private static final java.util.regex.Pattern BLANKS =
            java.util.regex.Pattern.compile("\\s+");

    /** 맞은 자리 앞뒤로 이만큼씩 보인다. */
    private static final int SNIPPET_AROUND = 20;

    private WorkbookHitDto toHit(WorkbookSearchRow row, String wanted,
                                 Map<String, String> namesByUserKey,
                                 Map<Long, WorkbookMarkRow> marks) {
        String hit;
        String snippet;
        if (containsIgnoringCase(row.workbookName(), wanted)) {
            hit = "NAME";
            snippet = null;
        } else if (row.cellText() != null) {
            hit = "CELL";
            snippet = snippetOf(row.cellText(), wanted);
        } else {
            hit = "PARA";
            snippet = snippetOf(row.paraText(), wanted);
        }
        WorkbookMarkRow mark = marks.get(row.workbookSeq());
        return new WorkbookHitDto(row.workbookSeq(), row.workbookName(),
                namesByUserKey.get(row.ownerUser()), row.roleCode(), row.memberCount(),
                row.sharedPublicly(), row.modifiedAt(), row.docType(), hit, snippet,
                mark != null && mark.starred(),
                mark == null ? null : mark.colorCode());
    }

    private static boolean containsIgnoringCase(String text, String wanted) {
        return text != null && text.toUpperCase(java.util.Locale.ROOT)
                .contains(wanted.toUpperCase(java.util.Locale.ROOT));
    }

    /**
     * 맞은 자리 앞뒤만 잘라 준다. 긴 글을 통째로 실으면 목록이 글밭이 된다.
     *
     * <p>줄바꿈은 공백으로 눌러 한 줄로 만든다 - 목록은 한 줄만 보여 준다.
     */
    private static String snippetOf(String raw, String wanted) {
        if (raw == null) {
            return null;
        }
        String text = BLANKS.matcher(raw).replaceAll(" ").trim();
        int at = text.toUpperCase(java.util.Locale.ROOT)
                .indexOf(wanted.toUpperCase(java.util.Locale.ROOT));
        if (at < 0) {
            return text.length() <= SNIPPET_AROUND * 2
                    ? text : text.substring(0, SNIPPET_AROUND * 2) + "…";
        }
        int from = Math.max(0, at - SNIPPET_AROUND);
        int to = Math.min(text.length(), at + wanted.length() + SNIPPET_AROUND);
        return (from > 0 ? "…" : "") + text.substring(from, to)
                + (to < text.length() ? "…" : "");
    }

    /** 카드에 보일 줄 수·칸 수. 카드가 작아 이보다 많이 담아도 보이지 않는다. */
    private static final int PREVIEW_ROWS = 6;
    private static final int PREVIEW_COLUMNS = 4;

    /**
     * 문서마다 첫 시트 앞부분을 모아 온다.
     *
     * <p>한 번에 가져온다. 문서마다 조회하면 목록 한 번에 조회가 문서 수만큼 붙는다.
     *
     * <p>빈 칸도 채워 줄마다 칸 수를 맞춘다. 들쭉날쭉하면 카드가 어긋나 보인다.
     * 값이 있는 마지막 줄까지만 만든다 - 빈 줄을 붙이면 빈 문서도 표가 그려진다.
     */
    private Map<Long, List<List<String>>> loadPreviews(List<Long> workbookSeqs) {
        if (workbookSeqs.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<List<String>>> previews = new java.util.HashMap<>();
        for (PreviewCellRow cell : workbookMapper.selectPreviewCells(
                workbookSeqs, PREVIEW_ROWS, PREVIEW_COLUMNS)) {
            List<List<String>> rows = previews.computeIfAbsent(
                    cell.workbookSeq(), key -> new java.util.ArrayList<>());
            while (rows.size() <= cell.rowNo()) {
                rows.add(new java.util.ArrayList<>(
                        java.util.Collections.nCopies(PREVIEW_COLUMNS, "")));
            }
            rows.get(cell.rowNo()).set(cell.columnNo(), cell.text());
        }
        return previews;
    }

    private Map<String, String> lookUpNames(Set<String> userKeys) {
        if (userKeys.isEmpty()) {
            // 인명부가 없는 환경이다. 이름 없이도 목록은 쓸 만하다.
            return Map.of();
        }
        return userDirectory.findByKeys(userKeys).stream()
                .filter(user -> user.displayName() != null)
                .collect(Collectors.toMap(DirectoryUser::userKey, DirectoryUser::displayName,
                        (first, second) -> first));
    }

    private List<WorkbookSummaryDto> toSummaries(List<WorkbookRow> rows,
                                                 Map<String, String> namesByUserKey,
                                                 Map<Long, List<List<String>>> previews,
                                                 Map<Long, WorkbookMarkRow> marks) {
        return rows.stream()
                .map(row -> {
                    WorkbookMarkRow mark = marks.get(row.workbookSeq());
                    return new WorkbookSummaryDto(
                            row.workbookSeq(), row.workbookName(),
                            namesByUserKey.get(row.ownerUser()),
                            row.roleCode(), row.memberCount(), row.sharedPublicly(),
                            row.modifiedAt(),
                            previews.getOrDefault(row.workbookSeq(), List.of()),
                            row.docType(),
                            mark != null && mark.starred(),
                            mark == null ? null : mark.colorCode());
                })
                .toList();
    }

    @Transactional
    public Long createWorkbook(String workbookName, AuditUser owner) {
        WorkbookDto workbook = new WorkbookDto(null, workbookName, owner.userKey(), null, null);
        workbookMapper.insertWorkbook(workbook, owner.userKey());
        return workbook.workbookSeq();
    }

    /** 빈 문서에 붙는 첫 시트 이름. xlsx 로 내보냈을 때 엑셀 기본과 같아 보이게 한다. */
    private static final String FIRST_SHEET_NAME = "Sheet1";

    public static final String UNTITLED = "제목 없는 문서";

    /**
     * 빈 문서.
     *
     * <p>시트를 하나 같이 만든다. 시트 없는 문서를 만들면 화면이 열 것이 없어 빈
     * 그리드만 뜨고, 사용자는 문서가 안 만들어졌다고 읽는다.
     */
    @Transactional
    public CreatedWorkbook createBlank(String workbookName, DocType docType, AuditUser owner) {
        String trimmed = workbookName == null ? "" : workbookName.trim();
        String name = trimmed.isEmpty() ? UNTITLED : trimmed;

        WorkbookDto workbook = new WorkbookDto(null, name, owner.userKey(), null, null);
        workbook.setDocType(docType.name());
        workbookMapper.insertWorkbook(workbook, owner.userKey());
        Long workbookSeq = workbook.workbookSeq();

        // 글 문서에는 시트를 붙이지 않는다. 빈 시트가 딸려 오면 목록·내보내기가
        // 그것을 세어 이상해진다.
        if (docType != DocType.DOC) {
            SheetSummaryDto sheet =
                    new SheetSummaryDto(null, workbookSeq, FIRST_SHEET_NAME, 0, 0, 0);
            sheetMapper.insertSheet(sheet, owner.userKey());
            return new CreatedWorkbook(workbookSeq, name, List.of(sheet), docType.name());
        }
        return new CreatedWorkbook(workbookSeq, name, List.of(), docType.name());
    }

    /** 만든 직후의 문서. 화면이 곧바로 열 수 있게 시트까지 함께 준다. */
    public record CreatedWorkbook(Long workbookSeq, String workbookName,
                                  List<SheetSummaryDto> sheets, String docType) {
    }

    /** 제목이 비면 목록에서 어느 문서인지 알 수 없다. 앞뒤 공백은 정리해 저장한다. */
    @Transactional
    public void rename(Long workbookSeq, String workbookName, AuditUser user) {
        String trimmed = workbookName == null ? "" : workbookName.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("제목을 입력하세요");
        }
        workbookMapper.updateWorkbookName(workbookSeq, trimmed, user.userKey());
    }

    /**
     * 문서 지우기.
     *
     * <p>소프트 삭제라 셀·시트는 남는다. 되살릴 길을 남겨 두려는 것이다 -
     * 지금 화면에 되살리기는 없지만 DB 를 뒤지면 나온다.
     */
    @Transactional
    public void delete(Long workbookSeq, AuditUser user) {
        workbookMapper.softDeleteWorkbook(workbookSeq, user.userKey());
    }

    /** 휴지통이 문서를 들고 있는 날수. 구글도 30일이다. */
    public static final int TRASH_DAYS = 30;

    /** 지운 지 30일이 안 된 내 문서. 최근에 지운 것이 앞이다. */
    public List<TrashRow> listTrash(String userKey) {
        return workbookMapper.selectTrashByOwner(userKey, trashSince());
    }

    /**
     * 휴지통에서 되살린다.
     *
     * @return 되살렸으면 true, 내 것이 아니거나 이미 살아 있으면 false
     */
    @Transactional
    public boolean restore(Long workbookSeq, AuditUser user) {
        return workbookMapper.restoreWorkbook(workbookSeq, user.userKey(), trashSince()) == 1;
    }

    private static java.time.LocalDateTime trashSince() {
        return java.time.LocalDateTime.now().minusDays(TRASH_DAYS);
    }

    public WorkbookDto getWorkbook(Long workbookSeq) {
        return workbookMapper.selectWorkbook(workbookSeq);
    }
}
