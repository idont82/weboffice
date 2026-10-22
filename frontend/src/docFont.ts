/**
 * 글꼴 바꿔치기.
 *
 * <p>원본 글꼴이 이 컴퓨터에 없으면 화면이 아무 글꼴이나 골라 쓴다. 고른 글꼴이
 * 원본보다 넓으면 크기가 같아도 글자가 커 보인다(2026-08-13 실측: 워드 기본
 * Aptos 를 못 찾아 11pt 글이 원본보다 11% 넓게 그려졌다).
 *
 * <p>그래서 폭이 비슷한 글꼴로 바꾼다. Aptos 는 Calibri 의 뒤를 이은 글꼴이라
 * 폭이 거의 같다.
 */

/** 없는 글꼴 → 폭이 비슷한 글꼴. 이름은 소문자로 견준다. */
const INSTEAD = new Map<string, string>([
  ['aptos', 'Calibri'],
  ['aptos display', 'Calibri'],
  ['aptos narrow', 'Calibri'],
]);

interface Run {
  st: number;
  ed: number;
  ts?: { ff?: string; [key: string]: unknown };
  [key: string]: unknown;
}

export function withUsableFont<T extends Run>(run: T): T {
  const font = run.ts?.ff;
  if (font == null) {
    return run;
  }
  const instead = INSTEAD.get(font.toLowerCase());
  if (instead == null) {
    return run;
  }
  return { ...run, ts: { ...run.ts, ff: instead } };
}

/**
 * 글꼴을 안 적은 조각에 문서 기본 글꼴을 준다.
 *
 * <p>원본은 문서 기본 글꼴(Normal 서식)로 그린다. 화면이 그것을 모르면 Univer
 * 기본 글꼴로 그려 줄이 접히는 자리가 달라진다 - 워드에서 세 줄인 문단이 화면에선
 * 두 줄이 됐다(2026-08-25 실측).
 */
export function withDefaultFont<T extends Run>(run: T, font: string | null): T {
  if (font == null || font === '' || run.ts?.ff != null) {
    return run;
  }
  return { ...run, ts: { ...run.ts, ff: font } };
}
