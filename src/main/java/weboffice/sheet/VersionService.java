package weboffice.sheet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import weboffice.common.AuditUser;
import weboffice.doc.DocParagraphService;
import weboffice.excel.CellDto;
import weboffice.security.DirectoryUser;
import weboffice.security.UserDirectory;

/**
 * 문서 버전 기록과 되돌리기.
 *
 * <p>버전을 따로 저장하지 않는다. 셀 변경 로그를 편집 덩어리로 묶은 것이 곧 버전이다
 * (구글도 이 방식이다). 그래서 예전에 쌓아 둔 기록까지 그대로 버전이 된다.
 *
 * <p>되돌리기는 <b>덮어쓰기가 아니라 새 편집</b>이다. 고른 버전 뒤의 변경을 물려
 * 그때 값으로 다시 저장한다 - 그 되돌리기 자체도 기록에 남아 다시 앞으로 갈 수 있다.
 */
@Service
public class VersionService {

    /** 버전 목록에 훑을 기록 수. 이보다 오래된 것은 목록에 나오지 않는다. */
    public static final int LOG_SCAN_LIMIT = 2000;

    private final ChangeLogMapper changeLogMapper;
    private final weboffice.doc.DocChangeLogMapper docChangeLogMapper;
    private final SheetService sheetService;
    private final DocParagraphService docParagraphService;
    private final UserDirectory userDirectory;

    public VersionService(ChangeLogMapper changeLogMapper,
                          weboffice.doc.DocChangeLogMapper docChangeLogMapper,
                          SheetService sheetService,
                          DocParagraphService docParagraphService,
                          UserDirectory userDirectory) {
        this.changeLogMapper = changeLogMapper;
        this.docChangeLogMapper = docChangeLogMapper;
        this.sheetService = sheetService;
        this.docParagraphService = docParagraphService;
        this.userDirectory = userDirectory;
    }

    /**
     * 최근 버전이 앞이다. 편집자는 이름으로 바꿔 담는다 - 사번은 화면에 안 나간다.
     *
     * <p>시트 이력과 글 문단 이력을 한 줄기로 본다. 문서 하나는 시트이거나 글이라
     * 보통은 한쪽만 있지만, 섞여 있어도 시각 순으로 묶인다.
     */
    public List<SheetVersion> listVersions(Long workbookSeq) {
        List<LogHeadRow> heads = new ArrayList<>(
                changeLogMapper.selectWorkbookLogHeads(workbookSeq, LOG_SCAN_LIMIT));
        heads.addAll(docChangeLogMapper.selectLogHeads(workbookSeq, LOG_SCAN_LIMIT));
        heads.sort(java.util.Comparator.comparing(LogHeadRow::changedAt,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        Map<String, String> names = lookUpNames(heads.stream()
                .map(LogHeadRow::changedUser)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        List<SheetVersion> named = new ArrayList<>();
        for (SheetVersion version : VersionGrouping.versionsOf(heads)) {
            named.add(new SheetVersion(version.versionId(), version.fromAt(), version.toAt(),
                    version.changeCount(),
                    version.editors().stream().map(emp -> names.getOrDefault(emp, "알 수 없음"))
                            .toList()));
        }
        return named;
    }

    /** 한 버전을 펼쳐 볼 때 보여 줄 자리 수. 한 판에 수천 칸을 고쳐도 화면은 못 읽는다. */
    public static final int CHANGE_LIMIT = 200;

    /**
     * 그 버전에서 무엇이 바뀌었는가.
     *
     * <p>"고친 칸 5개" 만으로는 어느 판인지 알 수 없다(2026-08-20 지적). 버전이
     * 걸친 시각 구간을 다시 세어 그 안의 기록을 사람이 읽는 모양으로 준다.
     */
    public List<VersionChangeDto> changesIn(Long workbookSeq, long versionId) {
        SheetVersion version = listVersions(workbookSeq).stream()
                .filter(one -> one.versionId() == versionId)
                .findFirst()
                .orElse(null);
        if (version == null) {
            return List.of();
        }
        List<VersionChangeDto> changes = new ArrayList<>();
        for (VersionChangeRow row : changeLogMapper.selectWorkbookLogsBetween(
                workbookSeq, version.fromAt(), version.toAt(), CHANGE_LIMIT)) {
            String before = CellHistoryService.displayOf(row.orgValue());
            String after = CellHistoryService.displayOf(row.modValue());
            if (java.util.Objects.equals(before, after)) {
                // 화면이 같은 값을 다시 보낸 자리다. 세어 봐야 무엇이 바뀐 것인지
                // 되레 가린다(2026-08-20 실측: 14건 중 절반이 제자리였다).
                continue;
            }
            changes.add(new VersionChangeDto(
                    row.sheetName() + " · " + CellName.of(row.rowNo(), row.columnNo()),
                    before, after, row.changedAt(), null));
        }
        // 글 문서는 문단 번호로 견주면 안 된다. 위에 한 줄만 넣어도 아래 번호가
        // 모두 밀려 온 문서가 바뀐 것처럼 보인다(2026-08-20 지적: 두 줄 넣었는데
        // 131군데). 그 판의 앞뒤 글을 지어 내용으로 견준다.
        java.time.LocalDateTime justBefore = version.fromAt() == null ? null
                : version.fromAt().minusNanos(1_000_000);
        if (justBefore != null) {
            List<String> was = docParagraphService.textAt(workbookSeq, justBefore);
            List<String> now = docParagraphService.textAt(workbookSeq, version.toAt());
            for (weboffice.doc.DocDiff.Change change
                    : weboffice.doc.DocDiff.between(was, now)) {
                if (isBlank(change.before()) && isBlank(change.after())) {
                    // 엔터만 쳐도 빈 문단이 늘고 준다. 세면 정작 쓴 글이 묻힌다.
                    continue;
                }
                changes.add(new VersionChangeDto("문단 " + (change.paragraphNo() + 1),
                        change.before(), change.after(), version.toAt(), null));
                if (changes.size() >= CHANGE_LIMIT) {
                    break;
                }
            }
        }
        return withNames(changes, workbookSeq, version);
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /** 문단 JSON 에서 글만 꺼낸다. 서식은 화면에 적어 봐야 읽히지 않는다. */
    private static String textOfParagraph(String json) {
        var paragraph = weboffice.doc.DocParagraphService.fromJson(json);
        return paragraph == null ? null : paragraph.text();
    }

    /** 편집자 이름을 채운다. 한 버전은 한 사람이 고친 것이라 이름도 하나다. */
    private List<VersionChangeDto> withNames(List<VersionChangeDto> changes, Long workbookSeq,
                                             SheetVersion version) {
        String who = version.editors().isEmpty() ? null : version.editors().get(0);
        List<VersionChangeDto> named = new ArrayList<>(changes.size());
        for (VersionChangeDto one : changes) {
            named.add(new VersionChangeDto(one.where(), one.before(), one.after(),
                    one.changedAt(), who));
        }
        return named;
    }

    /**
     * 고른 버전의 값으로 되돌린다.
     *
     * <p>버전 뒤의 기록을 오래된 것부터 훑어 칸마다 <b>맨 처음 만난 ORG_VALUE</b> 를
     * 담는다 - 그것이 그 버전에서의 값이다. 값이 없으면(그 뒤에 새로 넣은 칸) 지운다.
     *
     * @return 되돌린 칸 수
     */
    @Transactional
    public int restoreTo(Long workbookSeq, long versionId, AuditUser user) {
        java.time.LocalDateTime afterAt = VersionGrouping.timeOf(versionId);
        Map<Long, Map<CellSpot, CellDto>> wanted = new LinkedHashMap<>();
        Set<CellSpot> seen = new LinkedHashSet<>();

        for (LogValueRow row : changeLogMapper.selectWorkbookLogsAfter(workbookSeq, afterAt)) {
            CellSpot spot = new CellSpot(row.sheetSeq(), row.rowNo(), row.columnNo());
            if (!seen.add(spot)) {
                // 같은 칸을 여러 번 고쳤다. 맨 처음 기록의 "고치기 전" 이 그때 값이다.
                continue;
            }
            wanted.computeIfAbsent(row.sheetSeq(), key -> new LinkedHashMap<>())
                    .put(spot, CellJson.fromJson(row.orgValue()));
        }

        int restored = 0;
        for (Map.Entry<Long, Map<CellSpot, CellDto>> perSheet : wanted.entrySet()) {
            List<CellDto> changed = new ArrayList<>();
            List<CellDto> deleted = new ArrayList<>();
            for (Map.Entry<CellSpot, CellDto> one : perSheet.getValue().entrySet()) {
                CellDto then = one.getValue();
                if (then == null) {
                    // 그 버전에는 없던 칸이다. 지우는 것이 되돌리는 것이다.
                    deleted.add(new CellDto(one.getKey().rowNo(), one.getKey().columnNo(),
                            CellDto.ValueType.STRING, null, null, null, null, null, null));
                } else {
                    changed.add(then);
                }
            }
            restored += changed.size() + deleted.size();
            sheetService.saveDelta(perSheet.getKey(), changed, deleted, user);
        }
        // 글 문서는 문단이 같은 일을 한다. 시트 문서에서는 아무것도 안 나온다.
        restored += docParagraphService.restoreTo(workbookSeq, afterAt, user);
        return restored;
    }

    private Map<String, String> lookUpNames(Set<String> userKeys) {
        if (userKeys.isEmpty()) {
            return Map.of();
        }
        return userDirectory.findByKeys(userKeys).stream()
                .filter(user -> user.displayName() != null)
                .collect(Collectors.toMap(DirectoryUser::userKey, VersionService::nameOf,
                        (first, second) -> first));
    }

    private static String nameOf(DirectoryUser user) {
        String rank = user.rank() == null ? "" : user.rank().trim();
        return rank.isEmpty() ? user.displayName() : user.displayName() + " " + rank;
    }

    private record CellSpot(Long sheetSeq, int rowNo, int columnNo) {
    }
}
