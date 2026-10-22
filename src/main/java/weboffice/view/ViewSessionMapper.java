package weboffice.view;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ViewSessionMapper {

    int insertSession(@Param("row") ViewSessionRow row, @Param("userKey") String userKey);

    ViewSessionRow selectByTokenHash(@Param("tokenHash") String tokenHash);

    ViewSessionRow selectBySeq(@Param("sessionSeq") Long sessionSeq);

    /** 창 세션 쿠키의 난수 해시로 찾는다. 순번을 그대로 쓰지 않는 이유는 클래스
     * 주석과 {@code ViewSession.xml} 을 본다(IDOR 방지). */
    ViewSessionRow selectBySessionKeyHash(@Param("sessionKeyHash") String sessionKeyHash);

    /** 처음 열릴 때 한 번. 상태를 OPENED 로 바꾸고 연 시각·창 세션 열쇠 해시를 적는다. */
    int markOpened(@Param("sessionSeq") Long sessionSeq,
                   @Param("sessionKeyHash") String sessionKeyHash);

    /** 스냅샷을 받아 갈 때마다. 마지막 본 시각을 적어 "얼마나 봤나" 를 잰다. */
    int touch(@Param("sessionSeq") Long sessionSeq);
}
