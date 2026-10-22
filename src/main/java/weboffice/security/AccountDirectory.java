package weboffice.security;

import java.util.Collection;
import java.util.List;

/**
 * 등록 모드의 인명부. 등록된 사용자 표를 본다.
 *
 * <p>막힌 사람도 {@link #findByKey} 로는 찾힌다 - 그가 만든 문서의 소유자
 * 이름이 사라지면 안 된다. 이름으로 찾는 길({@link #searchByName})에서만
 * 막힌 사람을 뺀다 - 공유 창에서 고를 수 있으면 안 되기 때문이다.
 */
public class AccountDirectory implements UserDirectory {

    private final UserAccountMapper mapper;

    public AccountDirectory(UserAccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public DirectoryUser findByKey(String userKey) {
        if (userKey == null || userKey.isBlank()) {
            return null;
        }
        UserAccountRow found = mapper.selectByKey(userKey);
        return found == null ? null : found.toDirectoryUser();
    }

    /** 등록 모드에서는 이메일이 곧 로그인 아이디다. */
    @Override
    public DirectoryUser findByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        UserAccountRow found = mapper.selectByEmail(userId.trim().toLowerCase());
        return found == null ? null : found.toDirectoryUser();
    }

    @Override
    public List<DirectoryUser> searchByName(String keyword, int limit) {
        return mapper.searchByName(keyword, limit).stream()
                .map(UserAccountRow::toDirectoryUser)
                .toList();
    }

    @Override
    public List<DirectoryUser> findByKeys(Collection<String> userKeys) {
        if (userKeys.isEmpty()) {
            return List.of();
        }
        return mapper.selectByKeys(userKeys).stream()
                .map(UserAccountRow::toDirectoryUser)
                .toList();
    }
}
