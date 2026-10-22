package weboffice.workbook;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberMapper {

    /** @return ROLE_CODE. 구성원이 아니거나 사용 중지면 null. */
    String selectRole(@Param("workbookSeq") Long workbookSeq, @Param("memberUser") String memberUser);

    /**
     * 단순 삽입. 테스트가 구성원을 만들 때 쓴다.
     *
     * <p>{@code upsertMember} 로 대체하지 않는다 - 여러 테스트가 이것을 쓰고 있고,
     * "이미 있으면 갱신" 이 아니라 "없는 것을 넣는다" 는 뜻이 필요할 때가 있다.
     */
    int insertMember(@Param("workbookSeq") Long workbookSeq,
                     @Param("memberUser") String memberUser,
                     @Param("roleCode") String roleCode,
                     @Param("userKey") String userKey);

    List<MemberRow> selectMembers(@Param("workbookSeq") Long workbookSeq);

    /**
     * 있으면 역할 변경, 없으면 추가. 소프트삭제된 행은 되살린다.
     *
     * <p>벤더 분기 지점이다. 되살리기를 빠뜨리면 뺐다가 다시 넣은 사람이 조회에서
     * 사라진다 - 셀·그림 업서트가 같은 함정을 갖고 있다.
     */
    int upsertMember(@Param("workbookSeq") Long workbookSeq,
                     @Param("memberUser") String memberUser,
                     @Param("roleCode") String roleCode,
                     @Param("userKey") String userKey);

    int softDeleteMember(@Param("workbookSeq") Long workbookSeq,
                         @Param("memberUser") String memberUser,
                         @Param("userKey") String userKey);
}

/** 구성원 한 줄. <b>사번을 담으므로 응답으로 나가지 않는다.</b> */
record MemberRow(String memberUser, String roleCode) {
}
