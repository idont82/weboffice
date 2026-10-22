package weboffice.lock;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LockMapper {

    /** 시트 행을 잠가 같은 시트에 대한 잠금 요청을 직렬화한다. */
    Long lockSheetRow(@Param("sheetSeq") Long sheetSeq);

    List<LockRow> selectActiveLocks(@Param("sheetSeq") Long sheetSeq);

    LockRow selectLock(@Param("lockSeq") Long lockSeq);

    int insertLock(@Param("lock") LockRow lock, @Param("userKey") String userKey,
                   @Param("ttlSeconds") int ttlSeconds);

    int releaseLock(@Param("lockSeq") Long lockSeq, @Param("userKey") String userKey);

    int heartbeat(@Param("lockSeq") Long lockSeq, @Param("userKey") String userKey,
                  @Param("ttlSeconds") int ttlSeconds);

    /** @return 회수한 잠금 수 */
    int expireOverdueLocks();
}
