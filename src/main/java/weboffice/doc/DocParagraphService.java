package weboffice.doc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import weboffice.common.AuditUser;

/**
 * 글 문서 본문 저장과 되돌리기.
 *
 * <p>문단이 바뀔 때마다 이력을 남긴다. 셀과 같은 얼개다 - 그 이력이 곧 버전 기록이고
 * 되돌리기의 재료다(2026-08-20). 이력이 없던 동안의 글은 버전으로 못 되돌린다.
 */
@Service
public class DocParagraphService {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final DocParagraphMapper paragraphMapper;
    private final DocChangeLogMapper changeLogMapper;

    public DocParagraphService(DocParagraphMapper paragraphMapper,
                               DocChangeLogMapper changeLogMapper) {
        this.paragraphMapper = paragraphMapper;
        this.changeLogMapper = changeLogMapper;
    }

    /**
     * 바뀐 문단과 지운 문단을 담고 이력을 남긴다.
     *
     * @return 담은 문단 수
     */
    @Transactional
    public int save(Long workbookSeq, List<DocParagraphDto> changed, List<Integer> deleted,
                    AuditUser user) {
        if (changed.isEmpty() && deleted.isEmpty()) {
            return 0;
        }
        Map<Integer, DocParagraphDto> before = beforeOf(workbookSeq);
        List<DocChangeLogRow> logs = new ArrayList<>();

        for (DocParagraphDto row : changed) {
            DocParagraphDto previous = before.get(row.paragraphNo());
            if (sameParagraph(previous, row)) {
                // 값이 그대로면 이력을 남기지 않는다. 문서를 열기만 해도 이력이 쌓인다.
                continue;
            }
            logs.add(new DocChangeLogRow(workbookSeq, row.paragraphNo(),
                    previous == null ? "INSERT" : "UPDATE",
                    toJson(previous), toJson(row)));
        }
        for (Integer paragraphNo : deleted) {
            DocParagraphDto previous = before.get(paragraphNo);
            if (previous == null) {
                continue;
            }
            logs.add(new DocChangeLogRow(workbookSeq, paragraphNo, "DELETE",
                    toJson(previous), null));
        }

        List<Integer> gone = new ArrayList<>(deleted);
        changed.forEach(row -> gone.add(row.paragraphNo()));
        if (!gone.isEmpty()) {
            paragraphMapper.deleteParagraphs(workbookSeq, gone);
        }
        if (!changed.isEmpty()) {
            paragraphMapper.insertParagraphs(workbookSeq, changed, user.userKey());
        }
        if (!logs.isEmpty()) {
            changeLogMapper.insertLogs(logs, user.userKey());
        }
        return changed.size();
    }

    /**
     * 그 시각의 글로 되돌린다.
     *
     * <p>시각 뒤의 이력을 오래된 것부터 훑어 문단마다 <b>맨 처음 만난 "고치기 전"</b>
     * 을 되쓴다. 그때 없던 문단은 지운다. 되돌리기도 이력을 남긴다.
     *
     * @return 되돌린 문단 수
     */
    @Transactional
    public int restoreTo(Long workbookSeq, LocalDateTime afterAt, AuditUser user) {
        Map<Integer, DocParagraphDto> wanted = new LinkedHashMap<>();
        for (DocLogValueRow row : changeLogMapper.selectLogsAfter(workbookSeq, afterAt)) {
            wanted.putIfAbsent(row.paragraphNo(), fromJson(row.orgValue()));
        }
        if (wanted.isEmpty()) {
            return 0;
        }

        List<DocParagraphDto> changed = new ArrayList<>();
        List<Integer> deleted = new ArrayList<>();
        for (Map.Entry<Integer, DocParagraphDto> one : wanted.entrySet()) {
            if (one.getValue() == null) {
                deleted.add(one.getKey());
            } else {
                changed.add(one.getValue());
            }
        }
        save(workbookSeq, changed, deleted, user);
        return changed.size() + deleted.size();
    }

    /**
     * 그 시각의 글.
     *
     * <p>지금 글에서 그 뒤의 기록을 되짚어 돌린다. 문단마다 <b>맨 처음 만난 "고치기
     * 전"</b> 이 그때 값이다 - 되돌리기가 쓰는 셈과 같다.
     */
    public List<String> textAt(Long workbookSeq, LocalDateTime at) {
        Map<Integer, String> lines = new java.util.TreeMap<>();
        for (DocParagraphDto row : paragraphMapper.selectParagraphs(workbookSeq)) {
            lines.put(row.paragraphNo(), row.text() == null ? "" : row.text());
        }
        Map<Integer, Boolean> rolled = new HashMap<>();
        for (DocLogValueRow row : changeLogMapper.selectLogsAfter(workbookSeq, at)) {
            if (rolled.putIfAbsent(row.paragraphNo(), true) != null) {
                continue;
            }
            DocParagraphDto then = fromJson(row.orgValue());
            if (then == null) {
                lines.remove(row.paragraphNo());
            } else {
                lines.put(row.paragraphNo(), then.text() == null ? "" : then.text());
            }
        }
        return new ArrayList<>(lines.values());
    }

    private Map<Integer, DocParagraphDto> beforeOf(Long workbookSeq) {
        Map<Integer, DocParagraphDto> before = new HashMap<>();
        for (DocParagraphDto row : paragraphMapper.selectParagraphs(workbookSeq)) {
            before.put(row.paragraphNo(), row);
        }
        return before;
    }

    private static boolean sameParagraph(DocParagraphDto before, DocParagraphDto now) {
        return before != null && java.util.Objects.equals(before.text(), now.text())
                && java.util.Objects.equals(before.style(), now.style())
                && java.util.Objects.equals(before.runs(), now.runs());
    }

    private static String toJson(DocParagraphDto row) {
        return row == null ? null : MAPPER.writeValueAsString(row);
    }

    /** 읽을 수 없으면 null - 이력 한 줄 때문에 되돌리기가 통째로 막히면 안 된다. */
    public static DocParagraphDto fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, DocParagraphDto.class);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
