package weboffice.security;

import java.util.Collection;
import java.util.List;

/**
 * 사람 이름을 찾는 곳.
 *
 * <p>왜 필요한가: 자리 표시·변경이력·구성원 목록은 <b>남의</b> 이름을 띄워야
 * 하는데, 남의 이름은 내 세션에 없다. 사내판은 여기에 인사 시스템을 끼우고,
 * 공개판은 로그인 없는 모드에서 열쇠에서 이름을 지어내거나 등록된 사용자
 * 표를 본다.
 *
 * <p>찾지 못하면 null·빈 목록을 돌려준다. 예외를 던지지 않는다 - 이름을 못
 * 찾았다고 문서가 안 열리면 안 된다.
 */
public interface UserDirectory {

    /** 신원 열쇠로 한 사람. 없으면 null. */
    DirectoryUser findByKey(String userKey);

    /** 로그인 아이디로 한 사람. 없으면 null. */
    DirectoryUser findByUserId(String userId);

    /**
     * 이름으로 찾는다. 공유 창의 사람 고르기가 쓴다.
     *
     * <p>찾을 인명부가 없는 설치에서는 빈 목록이 맞다 - 오류로 다루면 공유 창이
     * 멈춘다.
     */
    List<DirectoryUser> searchByName(String keyword, int limit);

    /**
     * 여러 열쇠를 한 번에. 목록 화면이 쓴다.
     *
     * <p>한 줄씩 조회하면 사람 수만큼 인명부를 두드린다.
     */
    List<DirectoryUser> findByKeys(Collection<String> userKeys);
}
