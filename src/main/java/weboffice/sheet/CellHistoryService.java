package weboffice.sheet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import weboffice.security.DirectoryUser;
import weboffice.security.UserDirectory;

/**
 * 칸 하나의 수정 기록.
 *
 * <p>셀 변경 로그(wo_cell_change_log)를 사람이 읽을 모양으로 바꾼다. 값 JSON 을
 * 그대로 내보내지 않는 까닭: 화면마다 다시 해석해야 하고, 사번이 섞여 나갈 길이
 * 생긴다.
 */
@Service
public class CellHistoryService {

    /** 한 칸에서 보여 줄 기록 수. 구글도 한 칸을 무한정 되짚지는 않는다. */
    public static final int HISTORY_LIMIT = 50;

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final ChangeLogMapper changeLogMapper;
    private final UserDirectory userDirectory;

    public CellHistoryService(ChangeLogMapper changeLogMapper,
                              UserDirectory userDirectory) {
        this.changeLogMapper = changeLogMapper;
        this.userDirectory = userDirectory;
    }

    public List<CellHistoryDto> historyOf(Long sheetSeq, int rowNo, int columnNo) {
        List<CellHistoryLogRow> rows =
                changeLogMapper.selectCellHistory(sheetSeq, rowNo, columnNo, HISTORY_LIMIT);
        Map<String, String> names = lookUpNames(rows.stream()
                .map(CellHistoryLogRow::changedUser)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        List<CellHistoryDto> history = new ArrayList<>(rows.size());
        for (CellHistoryLogRow row : rows) {
            history.add(new CellHistoryDto(
                    row.changeType(),
                    displayOf(row.orgValue()),
                    displayOf(row.modValue()),
                    row.changedAt(),
                    names.get(row.changedUser())));
        }
        return history;
    }

    /**
     * 값 JSON 을 사람이 읽는 글로.
     *
     * <p>수식은 등호를 붙인다 - 화면에서 보던 모양이 그것이다. 값이 아예 없으면
     * null 이다(칸을 지운 기록).
     */
    static String displayOf(String valueJson) {
        if (valueJson == null || valueJson.isBlank()) {
            return null;
        }
        JsonNode value;
        try {
            value = MAPPER.readTree(valueJson);
        } catch (RuntimeException e) {
            // 손으로 고친 값이 들어 있을 수 있다. 기록 하나 때문에 화면이 막히면 안 된다.
            return null;
        }
        String formula = text(value, "formulaValue");
        if (formula != null) {
            return "=" + formula;
        }
        for (String field : List.of("textValue", "numberValue", "datetimeValue", "booleanYn")) {
            String found = text(value, field);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String text(JsonNode value, String field) {
        JsonNode found = value.path(field);
        return found.isMissingNode() || found.isNull() ? null : found.asString();
    }

    private Map<String, String> lookUpNames(Set<String> userKeys) {
        if (userKeys.isEmpty()) {
            // 인명부가 없는 환경이다. 이름이 없어도 언제 무엇이 바뀌었는지는 보인다.
            return Map.of();
        }
        return userDirectory.findByKeys(userKeys).stream()
                .filter(user -> user.displayName() != null)
                .collect(Collectors.toMap(DirectoryUser::userKey, CellHistoryService::nameOf,
                        (first, second) -> first));
    }

    /** "홍길동 과장". 직급이 없으면 이름만이다. */
    private static String nameOf(DirectoryUser user) {
        String rank = user.rank() == null ? "" : user.rank().trim();
        return rank.isEmpty() ? user.displayName() : user.displayName() + " " + rank;
    }
}
