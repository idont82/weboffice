import { describe, expect, it } from 'vitest';

import {
  canCopyImage, cropBoxOf, htmlTableOf, imageOf, readTable, tsvOf, writeTable,
} from './clipboard';

const TAB = String.fromCharCode(9);
const NL = String.fromCharCode(10);

describe('tsvOf', () => {
  it('칸은 탭, 줄은 줄바꿈으로 잇는다', () => {
    expect(tsvOf([['가', '나'], ['다', 1]])).toBe(`가${TAB}나${NL}다${TAB}1`);
  });

  it('빈 칸은 빈 글자다', () => {
    expect(tsvOf([[null, '나']])).toBe(`${TAB}나`);
  });

  it('탭이나 줄바꿈이 든 값은 따옴표로 감싼다', () => {
    // 안 감싸면 한 칸이 두 칸으로 갈라져 붙는다.
    expect(tsvOf([[`한 줄${NL}두 줄`]])).toBe(`"한 줄${NL}두 줄"`);
  });

  it('값 안의 따옴표는 두 번 적는다', () => {
    expect(tsvOf([[`그는 "가자" 했다${TAB}`]])).toBe(`"그는 ""가자"" 했다${TAB}"`);
  });
});

describe('htmlTableOf', () => {
  it('표로 만든다', () => {
    const html = htmlTableOf([['가', '나']]);

    expect(html).toContain('<table');
    expect(html).toContain('<td>가</td>');
  });

  it('꺾쇠는 글자로 바꾼다', () => {
    // 안 바꾸면 남이 적은 값이 내 화면에서 태그가 된다.
    expect(htmlTableOf([['<b>굵게</b>']])).toContain('&lt;b&gt;굵게&lt;/b&gt;');
  });
});

describe('readTable', () => {
  it('붙여넣은 글을 표로 읽는다', () => {
    expect(readTable(`가${TAB}나${NL}다${TAB}라`)).toEqual([['가', '나'], ['다', '라']]);
  });

  it('따옴표 안의 줄바꿈은 한 칸이다', () => {
    expect(readTable(`"한 줄${NL}두 줄"${TAB}나`)).toEqual([[`한 줄${NL}두 줄`, '나']]);
  });

  it('빈 글은 빈 표다', () => {
    expect(readTable('')).toEqual([]);
  });

  it('끝의 빈 줄은 버린다', () => {
    expect(readTable(`가${NL}`)).toEqual([['가']]);
  });
});

describe('cropBoxOf', () => {
  const canvas = { left: 0, top: 100, width: 960, height: 400 } as DOMRect;

  it('화면 자리를 캔버스 안 자리로 옮긴다', () => {
    // 캔버스 픽셀이 화면 픽셀보다 촘촘할 수 있다(고해상도 화면).
    const box = cropBoxOf(canvas, 1920, { left: 100, top: 200, width: 300, height: 50 } as DOMRect);

    expect(box).toEqual({ x: 200, y: 200, width: 600, height: 100 });
  });

  it('캔버스 밖은 잘라 낸다', () => {
    const box = cropBoxOf(canvas, 960, { left: -50, top: 50, width: 100, height: 100 } as DOMRect);

    expect(box).toEqual({ x: 0, y: 0, width: 50, height: 50 });
  });

  it('아예 밖이면 없다고 한다', () => {
    expect(cropBoxOf(canvas, 960, { left: 2000, top: 0, width: 10, height: 10 } as DOMRect))
      .toBeNull();
  });
});

describe('writeTable', () => {
  it('클립보드가 있으면 글과 표를 함께 넣는다', async () => {
    const written: Record<string, Blob>[] = [];
    // jsdom 에는 ClipboardItem 이 없다. 브라우저에 있는 그것을 흉내 낸다.
    (globalThis as { ClipboardItem?: unknown }).ClipboardItem =
      class { constructor(readonly parts: Record<string, Blob>) {} } as never;
    const clipboard = {
      write: async (items: { parts: Record<string, Blob> }[]) => {
        written.push(...items.map((one) => one.parts));
      },
    };

    await writeTable([['가', '나']], { clipboard } as never);

    expect(Object.keys(written[0])).toEqual(['text/plain', 'text/html']);
  });

  it('클립보드가 없으면 옛 방법으로 글만 넣는다', async () => {
    delete (globalThis as { ClipboardItem?: unknown }).ClipboardItem;
    // http 로 열면 navigator.clipboard 자체가 없다(2026-08-26 실측).
    let copied = false;
    document.execCommand = () => { copied = true; return true; };

    await writeTable([['가', '나']], {} as never);

    expect(copied).toBe(true);
  });
});

describe('canCopyImage', () => {
  it('클립보드가 없으면 그림 복사도 없다', () => {
    // http 로 열면 navigator.clipboard 가 아예 없다. 그때는 내려받기로 돌린다.
    delete (globalThis as { ClipboardItem?: unknown }).ClipboardItem;

    expect(canCopyImage({} as never)).toBe(false);
  });

  it('둘 다 있으면 쓴다', () => {
    (globalThis as { ClipboardItem?: unknown }).ClipboardItem = class {} as never;

    expect(canCopyImage({ clipboard: { write: async () => {} } } as never)).toBe(true);
  });
});

describe('imageOf', () => {
  const png = new File([new Uint8Array([1, 2])], 'shot.png', { type: 'image/png' });

  it('붙여넣은 것에서 그림 파일을 고른다', () => {
    expect(imageOf({ files: [png], items: [] } as never)).toBe(png);
  });

  it('그림이 없으면 없다고 한다', () => {
    const text = new File(['가'], 'a.txt', { type: 'text/plain' });

    expect(imageOf({ files: [text], items: [] } as never)).toBeNull();
  });

  it('files 가 비어도 items 에서 찾는다', () => {
    // 브라우저마다 어느 쪽에 담아 주는지 다르다.
    const items = [{ kind: 'file', type: 'image/png', getAsFile: () => png }];

    expect(imageOf({ files: [], items } as never)).toBe(png);
  });

  it('아무것도 없으면 없다고 한다', () => {
    expect(imageOf(null as never)).toBeNull();
  });
});
