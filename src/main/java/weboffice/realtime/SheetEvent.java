package weboffice.realtime;

/**
 * 시트 토픽으로 나가는 이벤트.
 *
 * <p>행위자는 사번이 아니라 {@code USER_ID} 다. 사번은 브라우저로 내보내지 않는다
 * (설계 3절). 2026-08-06 이전에는 이 자리에 사번이 들어가 모든 이벤트에 실려 나갔다.
 *
 * <p>{@code actorName} 은 "홍길동 책임" 꼴이다. 화면이 아이디로 이름을 다시 찾지
 * 않게 여기에 실어 보낸다 - 셀을 고친 사람이 구성원이 아닐 수도 있다(공개 링크).
 *
 * <p>{@code actorClient} 는 <b>보낸 창</b>이다. 같은 사람이 두 창을 열어 두면
 * 아이디만으로는 자기 것을 가릴 수 없다 - 그 창만 자기 알림을 건너뛰게 한다
 * (2026-08-27 실측: 자기 저장이 남의 변경으로 보여 되돌리기가 그 위에서 돌았다).
 *
 * <p>payload 를 Object 로 둔 이유: 잠금과 셀 변경의 모양이 다르고, 클라이언트는
 * type 으로 분기한다. 타입별 클래스를 만들면 세 개가 되는데 P1 에서 얻는 게 없다.
 */
public record SheetEvent(String type, Long sheetSeq, String userId, String actorName,
                         String actorClient, Object payload) {

    /** 보낸 창을 안 적는 이벤트. 자리 표시처럼 화면이 되받아도 탈이 없는 것들이다. */
    public SheetEvent(String type, Long sheetSeq, String userId, String actorName,
                      Object payload) {
        this(type, sheetSeq, userId, actorName, null, payload);
    }
}
