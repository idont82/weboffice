import { describe, expect, it } from 'vitest';
import {
  effectiveIndents, indentHandles, movedIndent, snapIndent, type Indents,
} from './docIndent';

const BODY = 600;

describe('indentHandles', () => {
  it('들여쓰기가 없으면 본문 양끝에 붙는다', () => {
    const at = indentHandles({}, BODY);

    expect(at.firstLine).toBe(0);
    expect(at.left).toBe(0);
    expect(at.right).toBe(BODY);
  });

  it('왼쪽 들여쓰기를 두 손잡이가 함께 따른다', () => {
    const at = indentHandles({ indentStart: 48 }, BODY);

    expect(at.firstLine).toBe(48);
    expect(at.left).toBe(48);
  });

  it('첫 줄 들여쓰기는 위 손잡이만 옮긴다', () => {
    const at = indentHandles({ indentStart: 48, indentFirstLine: 24 }, BODY);

    expect(at.firstLine).toBe(72);
    expect(at.left).toBe(48);
  });

  it('글머리표 문단은 아래 손잡이가 글자 자리에 선다', () => {
    // 화면은 hanging 을 "둘째 줄부터 더 붙는 거리" 로 쓴다. 글머리표 문단에서는
    // 그것이 곧 글머리 글자 폭이라, 글머리는 위 손잡이 자리에 선다.
    const at = indentHandles({ indentStart: 48, hanging: 21 }, BODY);

    expect(at.firstLine).toBe(48);
    expect(at.left).toBe(69);
  });

  it('오른쪽 들여쓰기는 오른쪽에서 잰다', () => {
    expect(indentHandles({ indentEnd: 40 }, BODY).right).toBe(BODY - 40);
  });
});

describe('movedIndent', () => {
  it('아래 손잡이는 문단을 통째로 옮긴다', () => {
    // 글머리표와 글 사이 거리는 그대로여야 한다.
    const after = movedIndent('left', { indentStart: 48, hanging: 21 }, 24, BODY, true);

    expect(after.indentStart).toBe(72);
    expect(after.hanging).toBe(21);
  });

  it('아래 손잡이를 왼쪽으로 끌면 줄어든다', () => {
    // 어느 쪽으로 끌어도 늘어나기만 하던 버그를 막는다(2026-08-19 지적).
    const after = movedIndent('left', { indentStart: 48, hanging: 21 }, -24, BODY, true);

    expect(after.indentStart).toBe(24);
  });

  it('위 손잡이는 첫 줄만 옮긴다', () => {
    // 글머리표 문단(첫 줄 48, 나머지 69)에서 글머리만 12px 뒤로 민다.
    const after = movedIndent('firstLine', { indentStart: 48, hanging: 21 }, 12, BODY, true);
    const at = indentHandles(after, BODY);

    expect(at.firstLine).toBe(60);
    expect(at.left).toBe(69);
  });

  it('첫 줄을 나머지보다 뒤로 끌면 첫 줄 들여쓰기로 적는다', () => {
    const after = movedIndent('firstLine', { indentStart: 48 }, 24, BODY, true);

    expect(after.indentStart).toBe(48);
    expect(after.indentFirstLine).toBe(24);
  });

  it('첫 줄을 나머지보다 앞으로 끌면 나머지는 제자리다', () => {
    const before: Indents = { indentStart: 48, hanging: 21 };
    const after = movedIndent('firstLine', before, -24, BODY, true);

    // 첫 줄은 24px 앞으로, 나머지 줄(48+21=69)은 그대로.
    expect(after.indentStart).toBe(24);
    expect((after.indentStart ?? 0) + (after.hanging ?? 0)).toBe(69);
    expect(after.indentFirstLine).toBe(0);
  });

  it('오른쪽 손잡이는 끈 쪽으로 간다', () => {
    const after = movedIndent('right', {}, -48, BODY, true);

    expect(after.indentEnd).toBe(48);
  });

  it('본문 밖으로는 나가지 않는다', () => {
    expect(movedIndent('left', {}, -50, BODY).indentStart).toBe(0);
  });
});

describe('snapIndent', () => {
  it('0.25cm 자리에 붙는다', () => {
    expect(snapIndent(10)).toBeCloseTo(9.449, 2);
  });

  it('자유롭게 두면 그대로다', () => {
    expect(snapIndent(10, true)).toBe(10);
  });
});

describe('effectiveIndents', () => {
  it('문단에 안 적힌 값은 목록 규칙을 따른다', () => {
    const at = effectiveIndents({}, { indentStart: 96, hanging: 24 });

    expect(at.indentStart).toBe(96);
    expect(at.hanging).toBe(24);
  });

  it('문단에 적힌 값이 목록보다 세다', () => {
    const at = effectiveIndents({ indentStart: 10, hanging: 0 },
      { indentStart: 96, hanging: 24 });

    expect(at.indentStart).toBe(10);
    expect(at.hanging).toBe(0);
  });
});
