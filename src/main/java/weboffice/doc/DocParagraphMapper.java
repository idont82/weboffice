package weboffice.doc;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocParagraphMapper {

    List<DocParagraphDto> selectParagraphs(@Param("workbookSeq") Long workbookSeq);

    int deleteParagraphs(@Param("workbookSeq") Long workbookSeq,
                         @Param("paragraphNos") List<Integer> paragraphNos);

    int insertParagraphs(@Param("workbookSeq") Long workbookSeq,
                         @Param("rows") List<DocParagraphDto> rows,
                         @Param("userKey") String userKey);
}
