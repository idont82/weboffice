import { describe, expect, it } from 'vitest';

import { withDefaultFont, withUsableFont } from './docFont';

describe('withUsableFont', () => {
  it('없는 글꼴을 비슷한 글꼴로 바꾼다', () => {
    // 원본 글꼴(Aptos)이 이 컴퓨터에 없으면 화면이 아무 글꼴이나 골라 쓴다. 그것이
    // 원본보다 넓어 글자가 커 보였다(2026-08-13 실측: 같은 11pt 인데 11% 넓다).
    expect(withUsableFont({ st: 0, ed: 3, ts: { ff: 'Aptos', fs: 11 } }))
      .toEqual({ st: 0, ed: 3, ts: { ff: 'Calibri', fs: 11 } });
  });

  it('있는 글꼴은 그대로 둔다', () => {
    expect(withUsableFont({ st: 0, ed: 3, ts: { ff: '맑은 고딕' } }))
      .toEqual({ st: 0, ed: 3, ts: { ff: '맑은 고딕' } });
  });

  it('글꼴이 없는 서식은 건드리지 않는다', () => {
    expect(withUsableFont({ st: 0, ed: 3, ts: { fs: 9 } }))
      .toEqual({ st: 0, ed: 3, ts: { fs: 9 } });
    expect(withUsableFont({ st: 0, ed: 3 })).toEqual({ st: 0, ed: 3 });
  });

  it('굵기 같은 다른 서식은 지키고 이름만 바꾼다', () => {
    expect(withUsableFont({ st: 1, ed: 4, ts: { ff: 'Aptos Display', bl: 1 } }))
      .toEqual({ st: 1, ed: 4, ts: { ff: 'Calibri', bl: 1 } });
  });
});

describe('문서 기본 글꼴', () => {
  it('글꼴을 안 적은 조각에 기본 글꼴을 준다', () => {
    const run = { st: 0, ed: 3, ts: { fs: 11 } as Record<string, unknown> };

    expect(withDefaultFont(run, 'Century Gothic').ts.ff).toBe('Century Gothic');
  });

  it('제 글꼴을 적은 조각은 그대로 둔다', () => {
    const run = { st: 0, ed: 3, ts: { ff: 'Gulim' } };

    expect(withDefaultFont(run, 'Century Gothic').ts.ff).toBe('Gulim');
  });

  it('기본 글꼴이 없으면 그대로 둔다', () => {
    const run = { st: 0, ed: 3, ts: {} as Record<string, unknown> };

    expect(withDefaultFont(run, null).ts?.ff).toBeUndefined();
  });
});
