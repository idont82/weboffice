import { describe, expect, it } from 'vitest';
import { docLists } from './docLists';

describe('docLists', () => {
  it('워드처럼 층마다 0.5인치씩 들여쓴다', () => {
    // 워드 목록은 720twip(48px)씩 들어가고 글머리표가 360twip(24px) 앞에 걸린다.
    const levels = docLists().BULLET_LIST.nestingLevel;

    expect(levels[0].paragraphProperties.indentStart.v).toBe(48);
    expect(levels[1].paragraphProperties.indentStart.v).toBe(96);
    expect(levels[0].paragraphProperties.hanging.v).toBe(24);
  });

  it('글머리표를 본문 글자 크기로 그린다', () => {
    // Univer 기본 글머리표는 12pt 라 11pt 본문보다 줄이 6px 높아진다 - 쪽마다
    // 서너 줄씩 덜 들어가 원본과 쪽 나눔이 어긋났다(2026-08-19 지적).
    const levels = docLists(11).BULLET_LIST.nestingLevel;

    expect(levels[0].textStyle.fs).toBe(11);
  });

  it('워드가 쓰는 글머리표 모양을 층마다 돌려 쓴다', () => {
    const levels = docLists().BULLET_LIST.nestingLevel;

    expect(levels.map((one) => one.glyphSymbol).slice(0, 4))
      .toEqual(['•', 'o', '▪', '•']);
  });

  it('번호 매기기도 같은 들여쓰기를 쓴다', () => {
    const levels = docLists().ORDER_LIST.nestingLevel;

    expect(levels[0].glyphFormat).toBe('%1.');
    expect(levels[1].paragraphProperties.indentStart.v).toBe(96);
  });
});
