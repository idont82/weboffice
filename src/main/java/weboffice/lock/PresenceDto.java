package weboffice.lock;

import weboffice.security.Actor;

/**
 * 지금 누가 어디를 보고 있는지. <b>사번이 없다.</b>
 *
 * <p>잠금이 아니라 표시다. 이것을 받은 화면은 그 범위를 색으로 칠하기만 하고
 * 입력을 막지 않는다(2026-08-06 결정, 구글 시트와 같은 방식).
 *
 * <p>키는 {@code lockSeq} 다. 만료 회수는 사람이 아니라 행 단위로 일어나고,
 * 그때는 누구였는지 다시 조회하지 않는다.
 *
 * <p>{@code sessionKey} 는 자리를 잡은 브라우저 탭이다. 색을 사람이 아니라 탭 단위로
 * 나누기 위한 것이다 - 같은 사람이 두 창을 띄우면 색도 둘이어야 한다. <b>세션 식별자가
 * 아니다.</b> 그것을 내보내면 남이 세션을 가로챌 수 있다.
 *
 * <p>{@code displayName} 이 null 일 수 있다. 인명부가 없는 환경에서는 이름을 모른다 -
 * presence 가 아예 안 뜨는 것보다 아이디만이라도 뜨는 편이 낫다.
 */
public record PresenceDto(Long lockSeq, Long sheetSeq, String userId,
                          String displayName, String rank, String sessionKey,
                          int fromRowNo, int fromColumnNo, int toRowNo, int toColumnNo) {

    /**
     * "홍길동 책임" 꼴. 이름을 모르면 null 이고, 그때는 화면이 아이디로 물러선다.
     *
     * <p>이름을 {@code displayName} 으로 두지 않는다. 그러면 기록체의 접근자를
     * 덮어쓰게 되고, JSON 의 {@code displayName} 이 이름만이 아니라 직급까지
     * 붙은 값으로 조용히 바뀐다 - 컴파일은 되고 계약만 어긋난다.
     */
    public String label() {
        return Actor.display(displayName, rank);
    }
}
