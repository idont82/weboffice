package weboffice.sheet;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StyleMapper {

    Long selectStyleSeqByCode(@Param("workbookSeq") Long workbookSeq,
                              @Param("styleCode") String styleCode);

    int insertStyle(@Param("style") StyleRow style, @Param("userKey") String userKey);

    /** 서식 JSON 전문을 돌려준다. 파싱은 자바가 한다 — JSON 함수는 벤더마다 달라 분기가 는다. */
    String selectStyleValue(@Param("styleSeq") Long styleSeq);

    /** 여러 서식을 한 번에 읽는다. 시트를 열 때 서식 종류만큼 왕복하지 않으려고 쓴다. */
    java.util.List<StyleRow> selectStylesBySeqs(
            @Param("styleSeqs") java.util.Collection<Long> styleSeqs);
}
