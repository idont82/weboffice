package weboffice.view;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ViewCacheMapper {

    /**
     * 있으면 덮어쓰고 없으면 담는다(upsert). FILE_HASH 가 기본키라 식은 캐시
     * 줄이 아직 표에 남아 있을 때 insert 를 쓰면 기본키 충돌이 난다 - 자세한
     * 까닭은 ViewCache.xml 상단 주석을 봐라.
     */
    int upsertCache(@Param("row") ViewCacheRow row, @Param("userKey") String userKey);

    /** @param since 이 시각보다 뒤에 담긴 것만 준다. 만료된 것은 없는 것으로 본다. */
    ViewCacheRow selectCache(@Param("fileHash") String fileHash,
                             @Param("since") LocalDateTime since);

    int deleteOld(@Param("before") LocalDateTime before);
}
