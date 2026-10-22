package weboffice.security;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 등록된 사용자와 초대. */
@Mapper
public interface UserAccountMapper {

    UserAccountRow selectByKey(@Param("userKey") String userKey);

    /** 이메일은 소문자로 맞춰 넣으므로 찾을 때도 소문자로 묻는다. */
    UserAccountRow selectByEmail(@Param("email") String email);

    List<UserAccountRow> selectByKeys(@Param("userKeys") Collection<String> userKeys);

    /** 이름·이메일로 찾는다. 공유 창의 사람 고르기가 쓴다. */
    List<UserAccountRow> searchByName(@Param("keyword") String keyword,
                                      @Param("limit") int limit);

    /** 관리자 화면의 목록. 막힌 사람도 보여야 다시 열 수 있다. */
    List<UserAccountRow> selectAll();

    int insertUser(@Param("row") UserAccountRow row, @Param("actor") String actor);

    int updatePasswordHash(@Param("userKey") String userKey,
                           @Param("passwordHash") String passwordHash,
                           @Param("actor") String actor);

    int updateDisabled(@Param("userKey") String userKey,
                       @Param("disabled") boolean disabled,
                       @Param("actor") String actor);

    int insertInvite(@Param("tokenHash") String tokenHash,
                     @Param("userKey") String userKey,
                     @Param("expiresAt") LocalDateTime expiresAt,
                     @Param("actor") String actor);

    /** 아직 안 쓰고 안 지난 초대. 없으면 null. */
    UserAccountRow selectInvitedUser(@Param("tokenHash") String tokenHash,
                                     @Param("now") LocalDateTime now);

    int markInviteUsed(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    /** 같은 사람에게 새 초대를 내면 앞선 것은 버린다. */
    int expireInvitesOf(@Param("userKey") String userKey, @Param("now") LocalDateTime now);
}
