package weboffice.sheet;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import weboffice.common.AuditUser;

/**
 * 서식 사전. 같은 서식 JSON 은 한 행만 두고 셀들이 그 STYLE_SEQ 를 공유한다.
 *
 * <p>2026-08-06 부터 <b>서식 전체</b>를 보관한다. 그 전에는 숫자 서식만 남겨
 * 테두리·정렬·글꼴·색이 저장 뒤에 사라졌다. 화면(Univer)이 쓰는 서식 객체를 그대로
 * 담고, 우리는 해석하지 않는다 - 필드가 늘어도 서버를 고칠 일이 없다.
 *
 * <p>xlsx 로 내보낼 때만 그 안에서 숫자 서식을 꺼내 본다. POI 가 그것만 따로 받는다.
 */
@Service
public class StyleService {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final StyleMapper styleMapper;

    public StyleService(StyleMapper styleMapper) {
        this.styleMapper = styleMapper;
    }

    /**
     * 서식 JSON 을 사전에 넣고 STYLE_SEQ 를 돌려준다.
     *
     * <p>화면이 보낸 문자열을 그대로 해시하고 그대로 저장한다. 다시 만들지 않는
     * 이유: 이 문자열이 해시의 입력이라 표현이 한 글자만 달라져도 같은 서식이 두
     * 행이 된다. 화면은 같은 서식에 같은 문자열을 보낸다(JSON.stringify 는 객체의
     * 필드 순서를 유지한다).
     *
     * @return 서식이 없거나 빈 객체면 null
     */
    @Transactional
    public Long resolveStyle(Long workbookSeq, String styleValue, AuditUser user) {
        if (isBlankStyle(styleValue)) {
            return null;
        }

        String styleCode = md5Hex(styleValue);

        Long existing = styleMapper.selectStyleSeqByCode(workbookSeq, styleCode);
        if (existing != null) {
            return existing;
        }

        StyleRow row = new StyleRow(null, workbookSeq, styleCode, styleValue);
        styleMapper.insertStyle(row, user.userKey());
        return row.styleSeq();
    }

    /** @return STYLE_SEQ 가 가리키는 서식 JSON 그대로. 없으면 null. */
    public String findStyle(Long styleSeq) {
        return styleSeq == null ? null : styleMapper.selectStyleValue(styleSeq);
    }

    /**
     * 여러 서식을 한 번에 읽는다.
     *
     * <p>시트 하나에 서식이 백 가지를 넘는다. 셀마다 따로 물으면 그만큼 DB 를
     * 오가느라 시트 여는 시간의 절반을 여기서 썼다(2026-08-20 실측).
     *
     * @return STYLE_SEQ -> 서식 JSON. 못 찾은 번호는 담기지 않는다.
     */
    public Map<Long, String> findStyles(Collection<Long> styleSeqs) {
        if (styleSeqs.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> found = new HashMap<>();
        for (StyleRow row : styleMapper.selectStylesBySeqs(styleSeqs)) {
            found.put(row.styleSeq(), row.styleValue());
        }
        return found;
    }

    /** @return 서식 안의 숫자 서식 문자열. 없으면 null. xlsx 내보내기가 쓴다. */
    public String findNumberFormat(Long styleSeq) {
        return numberFormatOf(styleMapper.selectStyleValue(styleSeq));
    }

    /**
     * 서식이 없는 것과 같은가.
     *
     * <p>빈 객체를 저장하면 서식 없는 셀들이 사전에 행을 만들고, 그 행을 가리키는
     * 셀이 늘어 조회가 헛돌게 된다.
     */
    private boolean isBlankStyle(String styleValue) {
        if (styleValue == null || styleValue.isBlank()) {
            return true;
        }
        try {
            JsonNode parsed = MAPPER.readTree(styleValue);
            return !parsed.isObject() || parsed.isEmpty();
        } catch (RuntimeException e) {
            // 읽을 수 없는 값이다. 저장해 봐야 화면이 쓰지 못한다.
            return true;
        }
    }

    /**
     * 서식 JSON 에서 숫자 서식만 꺼낸다.
     *
     * <p><b>읽는 쪽은 반드시 파서여야 한다.</b> 예전에는 우리가 쓴 고정 형태만
     * 다룬다고 보고 {@code "numberFormat":"} 문자열을 찾았는데, PostgreSQL 의
     * {@code jsonb} 는 저장할 때 값을 <b>재직렬화</b>한다 - 콜론 뒤에 공백이 붙어
     * {@code {"numberFormat": "#,##0.00"}} 으로 돌아온다. 그러면 찾기가 실패해
     * <b>모든 셀의 숫자 서식이 조용히 사라졌다</b>(2026-08-05 실 PG 실측).
     *
     * <p>H2 는 CLOB 에 문자열을 그대로 담아 이 차이가 드러나지 않았다. 테스트
     * 200개가 전부 통과하는 상태에서 운영 DB 에서만 깨지는 종류였다.
     *
     * <p>쓰는 쪽은 화면이 보낸 문자열을 그대로 쓴다. 그쪽은 해시 입력이라 표현이
     * 고정돼야 한다. 두 방향의 요구가 다르므로 방법도 다르다.
     */
    private String numberFormatOf(String styleValueJson) {
        if (styleValueJson == null) {
            return null;
        }
        try {
            // Univer 서식은 n.pattern 에 담는다. 옛 문서는 numberFormat 에 있다 -
            // 2026-08-06 이전에 저장된 것들이라 둘 다 본다.
            JsonNode root = MAPPER.readTree(styleValueJson);
            JsonNode pattern = root.path("n").path("pattern");
            if (!pattern.isMissingNode() && !pattern.isNull()) {
                return pattern.asString();
            }
            JsonNode legacy = root.get("numberFormat");
            return legacy == null || legacy.isNull() ? null : legacy.asString();
        } catch (RuntimeException e) {
            // 사람이 손으로 고친 값이 들어올 수 있다. 서식 하나 때문에 문서 전체가
            // 안 열리는 것보다 서식을 잃는 편이 낫다.
            return null;
        }
    }

    /** STYLE_CODE 가 CHAR(32) 이므로 MD5 16진 표현이 정확히 들어맞는다. 중복 제거 키라 충분하다. */
    private String md5Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 는 모든 JVM 이 제공한다", e);
        }
    }
}
