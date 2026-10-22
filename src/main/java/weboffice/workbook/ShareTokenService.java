package weboffice.workbook;

import java.security.SecureRandom;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import weboffice.common.AuditUser;

/**
 * 공개 링크 토큰.
 *
 * <p>workbookSeq 는 1, 2, 3… 순번이라 그 자체로는 아무것도 감추지 못한다. 추측할 수
 * 없는 토큰을 따로 발급해 그것을 아는 사람만 들어오게 한다(설계 2절).
 */
@Service
public class ShareTokenService {

    /** 16바이트 = 32자 hex. Random 을 쓰면 예측 가능하다. */
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 16;

    private final WorkbookMapper workbookMapper;

    public ShareTokenService(WorkbookMapper workbookMapper) {
        this.workbookMapper = workbookMapper;
    }

    /** 발급과 재발급이 같다. 새 토큰이 옛 토큰을 덮으므로 옛 링크가 그 순간 죽는다. */
    @Transactional
    public String issue(Long workbookSeq, AuditUser actor) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);

        workbookMapper.updateShareToken(workbookSeq, token, actor.userKey());
        return token;
    }

    @Transactional
    public void revoke(Long workbookSeq, AuditUser actor) {
        workbookMapper.updateShareToken(workbookSeq, null, actor.userKey());
    }

    /** @return 문서 번호. 없는 토큰이면 null */
    public Long redeem(String token) {
        WorkbookDto workbook = workbookMapper.selectWorkbookByShareToken(token);
        return workbook == null ? null : workbook.workbookSeq();
    }
}
