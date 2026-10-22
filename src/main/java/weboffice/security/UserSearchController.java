package weboffice.security;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사람 찾기.
 *
 * <p>공유 대화상자에서 사람을 고르기 위한 것이다. 들어온 사람만 부를 수 있고
 * 이름·직급·부서만 내보낸다 - 신원 열쇠와 부서코드는 나가지 않는다.
 *
 * <p>로그인 없는 모드에서는 찾을 인명부가 없어 늘 빈 목록이다
 * ({@link GuestDirectory}). 오류가 아니다.
 */
@RestController
public class UserSearchController {

    /** 인명부를 통째로 훑지 못하게 막는 최소 길이와 상한이다. */
    private static final int MIN_KEYWORD_LENGTH = 2;
    private static final int MAX_RESULTS = 20;

    private final UserDirectory directory;

    public UserSearchController(UserDirectory directory) {
        this.directory = directory;
    }

    @GetMapping("/api/users")
    public List<UserSummaryDto> search(@RequestParam("q") String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.length() < MIN_KEYWORD_LENGTH) {
            throw new IllegalArgumentException("두 글자 이상 입력하세요");
        }
        return directory.searchByName(trimmed, MAX_RESULTS).stream()
                .map(UserSummaryDto::of)
                .toList();
    }
}
