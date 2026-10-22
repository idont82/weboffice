package weboffice.sheet;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import weboffice.excel.CellDto;

/**
 * 셀 값을 이력 JSON 으로 만든다. Jackson 3(tools.jackson)만 쓴다.
 *
 * <p>{@code StyleService} 가 서식 JSON 을 손으로 만드는 것과 다른 선택이다. 거기는
 * 문자열 자체가 해시 입력이라 표현이 고정돼야 하지만, 이력은 사람이 읽고 비교할
 * 뿐이라 표현이 바뀌어도 무해하다.
 */
final class CellJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private CellJson() {
    }

    static String toJson(CellDto cell) {
        return cell == null ? null : MAPPER.writeValueAsString(cell);
    }

    /**
     * 이력 JSON 을 셀로 되돌린다. 버전 되돌리기가 쓴다.
     *
     * @return 읽을 수 없으면 null - 기록 한 줄 때문에 되돌리기가 통째로 막히면 안 된다
     */
    static CellDto fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, CellDto.class);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
