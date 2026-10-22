package weboffice.excel;

/**
 * 픽셀 ↔ EMU 변환. POI 경계에서만 쓴다.
 *
 * <p>DB 와 Univer 는 픽셀을 쓰고 OOXML 만 EMU 를 쓴다. 변환을 여기 한 곳에
 * 모으지 않으면 import 와 export 가 서로 다른 상수를 쓰다 그림이 왕복마다
 * 조금씩 밀린다.
 *
 * <p>OOXML 은 1인치 = 914400 EMU 로 정의한다. 화면 기준 96 DPI 를 적용해
 * 1픽셀 = 9525 EMU 다.
 */
public final class Emu {

    private static final int PER_PIXEL = 9525;

    private Emu() {
    }

    public static int toEmu(int pixels) {
        return pixels * PER_PIXEL;
    }

    /** 버림이다. 반올림하면 왕복마다 크기가 한 픽셀씩 흔들릴 수 있다. */
    public static int toPixels(long emu) {
        return (int) (emu / PER_PIXEL);
    }
}
