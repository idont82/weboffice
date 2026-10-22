import { describe, expect, it } from 'vitest';

import { toParagraphs } from './docBody';

/** 표가 남기는 제어문자. Univer 가 칸 경계를 이 글자들로 표시한다. */
const TABLE_START = String.fromCharCode(0x1a);
const ROW_START = String.fromCharCode(0x1b);
const CELL_END = String.fromCharCode(0x1d);
/** 표와 상관없는 제어문자. 글자가 아니므로 문단 글에 넣지 않는다. */
const STRAY = String.fromCharCode(0x02);

describe('toParagraphs 와 제어문자', () => {
  it('표가 남기는 제어문자는 문단으로 담지 않는다', () => {
    // 그것을 글자로 담으면 다시 열 때 문단이 어그러지고, docx 로 내보낸 파일에도
    // 쓰레기가 섞인다(2026-08-11 실측: 문단 21개에 제어문자만 가득했다). 칸 안
    // 글은 문서 한 벌(스냅샷)이 담으므로 문단으로 남기지 않는다.
    const stream = `${TABLE_START}${ROW_START}글자${CELL_END}\r\n`;
    const rows = toParagraphs({
      dataStream: stream,
      paragraphs: [{ startIndex: stream.length - 2 }],
      textRuns: [],
    });

    expect(rows).toEqual([]);
  });

  it('서식 자리도 지운 글자 기준으로 옮긴다', () => {
    // 제어문자를 빼면 뒤 글자의 자리가 앞으로 당겨진다. 서식이 그대로면 엉뚱한
    // 곳이 굵어진다.
    const stream = `${STRAY}굵게보통\r\n`;
    const rows = toParagraphs({
      dataStream: stream,
      paragraphs: [{ startIndex: 5 }],
      textRuns: [{ st: 1, ed: 3, ts: { bl: 1 } }],
    });

    expect(rows[0].text).toBe('굵게보통');
    expect(JSON.parse(rows[0].runs!)).toEqual([{ st: 0, ed: 2, ts: { bl: 1 } }]);
  });
});
