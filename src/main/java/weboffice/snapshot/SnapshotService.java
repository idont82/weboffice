package weboffice.snapshot;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import weboffice.common.AuditUser;
import weboffice.security.DirectoryUser;
import weboffice.security.UserDirectory;
import weboffice.workbook.WorkbookService;

/**
 * 복구 지점.
 *
 * <p>버전 기록과 하는 일이 다르다. 이력은 칸 하나하나의 변화를 남기지만 시트·병합·
 * 크기·그림 같은 얼개는 안 남아, 시트를 지운 것은 되돌리지 못한다. 여기는 문서를
 * 통째로 담아 통째로 되돌린다.
 */
@Service
public class SnapshotService {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 사람이 붙일 수 있는 이유. 나머지는 서버가 스스로 정한다. */
    private static final Set<String> ASKABLE =
            Set.of("MANUAL", "BEFORE_AI", "BEFORE_IMPORT");

    /** 이름은 짧게. 목록 한 줄에 들어가야 한다. */
    private static final int LABEL_MAX = 50;

    private final SnapshotMapper snapshotMapper;
    private final DocumentReader reader;
    private final DocumentWriter writer;
    private final WorkbookService workbookService;
    private final UserDirectory userDirectory;

    public SnapshotService(SnapshotMapper snapshotMapper, DocumentReader reader,
                           DocumentWriter writer, WorkbookService workbookService,
                           UserDirectory userDirectory) {
        this.snapshotMapper = snapshotMapper;
        this.reader = reader;
        this.writer = writer;
        this.workbookService = workbookService;
        this.userDirectory = userDirectory;
    }

    /**
     * 지금 모습을 찍는다.
     *
     * @param reason 왜 찍는가. 사람이 부를 수 있는 것은 {@link #ASKABLE} 뿐이다
     * @param label  사람이 붙인 이름. 없으면 null
     */
    @Transactional
    public Long take(Long workbookSeq, String reason, String label, AuditUser user) {
        SnapshotBody body = reader.read(workbookSeq);
        String json = MAPPER.writeValueAsString(body);
        byte[] packed = GzipBody.pack(json);

        SnapshotRow row = new SnapshotRow(workbookSeq, LocalDateTime.now(), user.userKey(),
                reason, trimmed(label), body.docType(),
                json.getBytes(StandardCharsets.UTF_8).length, packed);
        snapshotMapper.insertSnapshot(row, user.userKey());

        prune(workbookSeq);
        return row.snapshotSeq();
    }

    /** 사람이 부탁한 찍기. 모르는 이유는 손으로 찍은 것으로 본다. */
    @Transactional
    public Long takeAsked(Long workbookSeq, String reason, String label, AuditUser user) {
        String wanted = reason == null ? "MANUAL" : reason.trim().toUpperCase(java.util.Locale.ROOT);
        return take(workbookSeq, ASKABLE.contains(wanted) ? wanted : "MANUAL", label, user);
    }

    private static String trimmed(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        String wanted = label.trim();
        return wanted.length() <= LABEL_MAX ? wanted : wanted.substring(0, LABEL_MAX);
    }

    /** 최근 것이 앞이다. 찍은 사람은 이름으로 바꿔 담는다 - 사번은 화면에 안 나간다. */
    public List<SnapshotSummaryDto> list(Long workbookSeq) {
        List<SnapshotListRow> rows = snapshotMapper.selectSnapshots(workbookSeq);
        Map<String, String> names = lookUpNames(rows.stream()
                .map(SnapshotListRow::takenUser)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        return rows.stream()
                .map(row -> new SnapshotSummaryDto(row.snapshotSeq(), row.takenAt(),
                        row.reason(), row.label(), names.get(row.takenUser()), row.bodySize()))
                .toList();
    }

    /**
     * 그 시점으로 되돌린다.
     *
     * <p>되돌리기 전에 지금 모습을 먼저 찍는다. 복원 자체도 되돌릴 수 있어야 한다 -
     * 잘못 고른 복구 지점이 마지막 손이 되면 이 기능이 사고를 키운다.
     */
    @Transactional
    public void restore(Long snapshotSeq, AuditUser user) {
        SnapshotRow row = require(snapshotSeq);
        take(row.workbookSeq(), "BEFORE_RESTORE", null, user);
        writer.apply(row.workbookSeq(), bodyOf(row), user);
    }

    /**
     * 그 시점 내용으로 새 문서를 만든다. 원본은 안 건드린다.
     *
     * @return 새 문서 번호
     */
    @Transactional
    public Long copyToNewWorkbook(Long snapshotSeq, AuditUser user) {
        SnapshotRow row = require(snapshotSeq);
        SnapshotBody body = bodyOf(row);
        WorkbookService.CreatedWorkbook created = workbookService.createBlank(
                body.workbookName(),
                weboffice.workbook.DocType.of(body.docType()), user);
        writer.apply(created.workbookSeq(), body, user);
        // 이름은 옮겨 담은 뒤에 붙인다. 스냅샷 본문에 든 이름이 먼저 덮어쓰기 때문이다.
        workbookService.rename(created.workbookSeq(), copyName(body.workbookName()), user);
        return created.workbookSeq();
    }

    /** 어느 시점을 되살린 것인지 이름으로 알 수 있게 한다. */
    private static String copyName(String name) {
        String base = name == null || name.isBlank() ? "문서" : name.trim();
        return base + " (복구본)";
    }

    /** 문서를 지울 때 함께 지운다. 문서를 참조하므로 남기면 지우기가 막힌다. */
    @Transactional
    public void removeAllOf(Long workbookSeq) {
        snapshotMapper.deleteByWorkbook(workbookSeq);
    }

    private SnapshotRow require(Long snapshotSeq) {
        SnapshotRow row = snapshotMapper.selectSnapshot(snapshotSeq);
        if (row == null) {
            throw new IllegalArgumentException("없는 복구 지점입니다: " + snapshotSeq);
        }
        return row;
    }

    /** 어느 문서의 복구 지점인가. 권한 판정이 이것을 먼저 묻는다. */
    public Long workbookOf(Long snapshotSeq) {
        return require(snapshotSeq).workbookSeq();
    }

    private SnapshotBody bodyOf(SnapshotRow row) {
        return MAPPER.readValue(GzipBody.unpack(row.body()), SnapshotBody.class);
    }

    private void prune(Long workbookSeq) {
        List<Long> stale = SnapshotPruning.stale(snapshotMapper.selectHeads(workbookSeq),
                LocalDateTime.now());
        if (!stale.isEmpty()) {
            snapshotMapper.deleteSnapshots(stale);
        }
    }

    private Map<String, String> lookUpNames(Set<String> userKeys) {
        if (userKeys.isEmpty()) {
            return Map.of();
        }
        return userDirectory.findByKeys(userKeys).stream()
                .filter(user -> user.displayName() != null)
                .collect(Collectors.toMap(DirectoryUser::userKey, DirectoryUser::displayName,
                        (first, second) -> first));
    }
}
