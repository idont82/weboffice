package weboffice.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 긴 목록을 문장 하나에 들어갈 크기로 자른다.
 *
 * <p>PostgreSQL 은 문장 하나에 파라미터를 65,535개까지만 받는다. H2 는 상한 대신
 * 큰 문장을 파싱하다 메모리가 터진다. 어느 쪽이든 "한 문장에 다 밀어 넣기" 가
 * 문제라 여기 한 곳에서 자른다.
 *
 * <p>2026-08-13 에 셀 저장에서, 2026-08-18 에 행·열 크기 저장에서 같은 일로 큰
 * 파일 올리기가 통째로 실패했다(파라미터 249,326개). 나눠 담는 자리를 저마다
 * 만들면 다음 표에서 또 겪는다.
 */
public final class Chunks {

    /**
     * 한 문장에 넣는 줄 수.
     *
     * <p>한 줄이 열 개 남짓을 쓰므로 2,000줄이면 파라미터 상한을 넉넉히 밑돈다.
     */
    public static final int SIZE = 2000;

    private Chunks() {
    }

    public static <T> List<List<T>> of(List<T> rows) {
        return by(rows, SIZE);
    }

    /**
     * 크기를 정해 자른다.
     *
     * <p>파라미터 상한 말고 다른 이유로 나눌 때 쓴다 - 휴지통 비우기는 사이사이
     * 다른 저장이 끼어들 수 있게 스무 문서씩 끊는다.
     */
    public static <T> List<List<T>> by(List<T> rows, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("자를 크기는 1 이상이어야 합니다");
        }
        List<List<T>> parts = new ArrayList<>();
        for (int from = 0; from < rows.size(); from += size) {
            parts.add(rows.subList(from, Math.min(rows.size(), from + size)));
        }
        return parts;
    }
}
