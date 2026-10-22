package weboffice.drawing;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ImageMapper {

    /** 같은 문서 안에 같은 바이트가 이미 있으면 그 IMAGE_SEQ 를, 없으면 null 을 준다. */
    Long selectImageSeqByHash(@Param("workbookSeq") Long workbookSeq,
                              @Param("imageHash") String imageHash);

    int insertImage(@Param("image") ImageRow image, @Param("userKey") String userKey);

    ImageRow selectImage(@Param("imageSeq") Long imageSeq);
}
