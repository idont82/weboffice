package weboffice.excel;

/**
 * xlsx 안의 그림 한 장.
 *
 * <p>오프셋 단위는 <b>픽셀</b>이다. POI 의 EMU 는 {@code Emu} 가 경계에서 걷어낸다.
 * DB 와 Univer 가 픽셀을 쓰므로 이 타입 밖으로 EMU 를 흘리지 않는다.
 *
 * <p>{@code imageSeq} 가 아니라 {@code bytes} 를 갖는 이유: import 시점에는 아직
 * DB 에 넣기 전이고, export 시점에는 이미 DB 에서 꺼낸 뒤다. 양쪽 다 바이트가
 * 손에 있다.
 */
public record PictureDto(
        byte[] bytes,
        int fromRowNo,
        int fromRowOffset,
        int fromColumnNo,
        int fromColumnOffset,
        int toRowNo,
        int toRowOffset,
        int toColumnNo,
        int toColumnOffset,
        int zOrder
) {
}
