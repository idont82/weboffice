package weboffice.drawing;

/**
 * 시트에 놓인 그림 한 장.
 *
 * <p>매퍼와 REST 가 같은 타입을 쓴다. 필드가 완전히 같아 나눌 이유가 없다
 * ({@code CellRow}/{@code CellDto} 가 나뉜 것은 한쪽이 styleSeq, 다른 쪽이
 * numberFormat 을 갖기 때문이다).
 *
 * <p>{@code sheetSeq} 가 없는 것은 빠뜨린 게 아니다. 한 번의 호출은 언제나 한
 * 시트에 대한 것이라 매퍼가 {@code @Param("sheetSeq")} 로 따로 받는다.
 * {@code CellRow} 와 같은 방식이다.
 *
 * <p>오프셋 단위는 <b>픽셀</b>이다. Univer 가 픽셀을 쓰고 이 값의 주 소비자이기
 * 때문이다. POI 는 EMU 를 쓰므로 그 경계에서만 {@code Emu} 로 변환한다.
 *
 * @param drawingId 클라이언트(Univer)가 만든 식별자. 서버 시퀀스와 별개인 이유는
 *                  클라이언트가 그림을 넣은 직후부터 이것을 키로 갱신·삭제를
 *                  보내오기 때문이다. 서버 왕복을 기다려야 한다면 그 사이의
 *                  이동·크기 변경이 유실된다.
 * @param drawingExtra 컬럼으로 빼지 않은 Univer 필드를 담은 JSON 문자열.
 *                     자르기(srcRect)·도형(prstGeom)·회전(angle)이 여기 들어간다.
 *                     서버는 내용을 해석하지 않고 그대로 왕복시킨다 - 받는 쪽이
 *                     JSON.parse 한다. 컬럼으로 뺀 값은 여기 넣지 않는다.
 *                     두 군데 있으면 어긋난다.
 */
public record DrawingDto(
        String drawingId,
        Long imageSeq,
        int fromRowNo,
        int fromRowOffset,
        int fromColumnNo,
        int fromColumnOffset,
        int toRowNo,
        int toRowOffset,
        int toColumnNo,
        int toColumnOffset,
        String anchorType,
        int zOrder,
        String drawingExtra
) {
}
