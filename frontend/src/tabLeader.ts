/**
 * 목차 점선을 손수 채운다.
 *
 * <p>워드 목차는 제목과 쪽 번호 사이를 탭으로 벌리고 점선으로 잇는다. 탭이 멈추는
 * 자리(오른쪽 탭 정지)와 채움(점선)은 문단 서식에 적혀 있다. Univer 는 그것을
 * 모른다 - 탭을 늘 같은 폭으로 그려 번호가 제목 바로 뒤에 붙는다(2026-08-21
 * 실측: engine-render 는 defaultTabStop 하나만 본다).
 *
 * <p>그래서 폭을 재서 우리가 채운다. 보기 전용 화면에서만 쓴다 - 편집 화면에서
 * 채우면 사람이 쓴 적 없는 점선이 문서에 저장된다.
 */

import type { DocParagraph } from './docBody';

/** 글자 폭을 재는 자(px). 화면은 캔버스로, 시험은 셈으로 잰다. */
export type Measure = (text: string) => number;

const TAB = '\t';

/** 채울 자리가 좁을 때 두는 최소 사이. 붙으면 글자가 겹쳐 보인다. */
const GAP = ' ';

/**
 * 채울 자리를 이만큼만 쓴다.
 *
 * <p>우리가 재는 폭과 화면이 그리는 폭이 조금 어긋난다. 딱 맞춰 채우면 점선이
 * 앞말에 붙어 한 낱말이 되고, 그 낱말이 줄에 안 들어가 통째로 다음 줄로 내려간다
 * (2026-08-21 실측: 목차 한 줄이 두 줄로 접혔다). 조금 덜 채워 그것을 막는다.
 */
const SAFETY = 0.94;

export function fillTabLeaders(rows: DocParagraph[],
  measureFor: (row: DocParagraph) => Measure): DocParagraph[] {
  return rows.map((row) => {
    const at = row.text.indexOf(TAB);
    if (at < 0 || !row.style) {
      return row;
    }
    let style: Record<string, unknown>;
    try {
      style = JSON.parse(row.style) as Record<string, unknown>;
    } catch {
      return row;
    }
    const stop = typeof style.tabRight === 'number' ? style.tabRight : null;
    if (stop == null) {
      return row;
    }

    const measure = measureFor(row);
    const head = row.text.slice(0, at);
    const tail = row.text.slice(at + 1);
    // 들여쓴 문단은 그만큼 늦게 시작한다. 안 빼면 줄이 넘쳐 두 줄로 접힌다
    // (2026-08-21 실측: 목차 한 줄이 두 줄이 됐다).
    const room = (stop - indentOf(style) - measure(head) - measure(tail)) * SAFETY;
    const dotted = style.tabLeader === 'dot';
    const mark = dotted ? '.' : GAP;
    const unit = measure(mark);
    let count = unit > 0 ? Math.floor(room / unit) : 0;
    // 잰 것과 그리는 것이 조금 어긋난다. 넘치면 하나씩 뺀다.
    while (count > 0 && measure(mark.repeat(count)) > room) {
      count -= 1;
    }
    const filler = count > 0 ? mark.repeat(count) : GAP;

    // 탭 한 글자가 채움으로 늘었다. 그만큼 뒤쪽 서식 자리를 민다.
    const moved = filler.length - 1;
    const runs = shift(row.runs, at, moved);
    return { ...row, text: head + filler + tail, runs };
  });
}

/** 문단 왼쪽 들여쓰기(px). 없으면 0 이다. */
function indentOf(style: Record<string, unknown>): number {
  const paragraph = style.paragraphStyle as Record<string, unknown> | undefined;
  const start = paragraph?.indentStart as { v?: number } | undefined;
  return typeof start?.v === 'number' ? start.v : 0;
}

function shift(runs: string | null, from: number, moved: number): string | null {
  if (!runs || moved === 0) {
    return runs;
  }
  let parsed: { st: number; ed: number }[];
  try {
    parsed = JSON.parse(runs) as { st: number; ed: number }[];
  } catch {
    return runs;
  }
  return JSON.stringify(parsed.map((run) => ({
    ...run,
    st: run.st > from ? run.st + moved : run.st,
    ed: run.ed > from ? run.ed + moved : run.ed,
  })));
}
