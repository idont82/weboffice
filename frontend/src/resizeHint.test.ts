import { describe, expect, it, test } from 'vitest';

import { correctedDelta, draggedSize, hintText } from './resizeHint';

describe('draggedSize', () => {
  test('끈 만큼 커진다', () => {
    // 눈금이 곧 결과여야 한다. 명령 쪽에서 손잡이 몫을 되돌리므로 여기서도 빼지
    // 않는다 - 빼면 눈금이 실제보다 2px 작게 보인다(2026-08-18 지적).
    expect(draggedSize(20, 10, 1)).toBe(30);
  });

  test('확대 배율로 나눈다', () => {
    // 200% 에서 화면 20픽셀은 시트 10픽셀이다.
    expect(draggedSize(20, 20, 2)).toBe(30);
  });

  test('되돌려 끌면 작아진다', () => {
    expect(draggedSize(64, -24, 1)).toBe(40);
  });

  test('아무리 줄여도 2픽셀 아래로는 안 간다', () => {
    // Univer 도 여기서 멈춘다. 더 작은 수를 보여 주면 거짓말이 된다.
    expect(draggedSize(20, -100, 1)).toBe(2);
  });
});

describe('hintText', () => {
  test('행은 높이, 열은 너비로 부른다', () => {
    expect(hintText('ROW', 32)).toBe('높이 32px');
    expect(hintText('COLUMN', 96)).toBe('너비 96px');
  });
});

describe('correctedDelta', () => {
  it('손잡이 몫 2px 을 되돌려 더한다', () => {
    // Univer 는 손잡이 두께의 절반(2px)을 이동 거리에서 뺀 값을 크기 변화로 쓴다.
    // 30px 을 끌면 28px 만 바뀐다(2026-08-18 지적).
    expect(correctedDelta(28, 1)).toBe(30);
  });

  it('위로 끈 것도 같은 만큼 되돌린다', () => {
    // 위로 30px 끌면 Univer 는 -32 를 보낸다.
    expect(correctedDelta(-32, 1)).toBe(-30);
  });

  it('배율을 감안한다', () => {
    // 200% 에서는 손잡이 몫이 시트 픽셀로 1px 이다.
    expect(correctedDelta(29, 2)).toBe(30);
  });

  it('움직이지 않았으면 0 이다', () => {
    // 그냥 눌렀다 놓으면 -2 가 온다. 되돌리면 0 - 바꿀 것이 없다.
    expect(correctedDelta(-2, 1)).toBe(0);
  });
});
