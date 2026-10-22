package weboffice.workbook;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkbookResourceMapper {

    List<WorkbookResourceDto> selectResources(@Param("workbookSeq") Long workbookSeq);

    int deleteResource(@Param("workbookSeq") Long workbookSeq, @Param("name") String name);

    int insertResource(@Param("workbookSeq") Long workbookSeq, @Param("name") String name,
                       @Param("data") String data, @Param("userKey") String userKey);
}
