package weboffice.security;

import java.util.Collection;
import java.util.List;

/**
 * 로그인 없는 모드의 인명부.
 *
 * <p>손님은 어디에도 적혀 있지 않다. 그래서 찾아보지 않고 <b>열쇠에서 이름을
 * 짓는다</b> - 열쇠가 곧 그 사람이므로 저장소가 필요 없다. 자리 표시와
 * 변경이력이 "손님-ab12" 로 서로를 가릴 수 있으면 그것으로 족하다.
 *
 * <p>이름으로 찾는 길은 없다. 적어 두지 않았으니 찾을 것도 없다 - 그래서
 * 공유 창의 사람 고르기는 이 모드에서 빈 목록을 받는다.
 */
public class GuestDirectory implements UserDirectory {

    @Override
    public DirectoryUser findByKey(String userKey) {
        if (!GuestAuthenticator.isKey(userKey)) {
            return null;
        }
        return new DirectoryUser(userKey, userKey, GuestAuthenticator.nameOf(userKey),
                null, null, null);
    }

    /** 손님에게는 아이디와 열쇠가 하나다. 따로 둘 근거가 없다. */
    @Override
    public DirectoryUser findByUserId(String userId) {
        return findByKey(userId);
    }

    @Override
    public List<DirectoryUser> searchByName(String keyword, int limit) {
        return List.of();
    }

    @Override
    public List<DirectoryUser> findByKeys(Collection<String> userKeys) {
        return userKeys.stream().map(this::findByKey).filter(one -> one != null).toList();
    }
}
