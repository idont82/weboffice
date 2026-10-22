package weboffice.view;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ViewEventMapper {

    int insertEvent(@Param("row") ViewEventRow row, @Param("userKey") String userKey);
}
