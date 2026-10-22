/**
 * 쪽마다 되풀이되는 글(머릿글·꼬릿글) 다듬기.
 *
 * <p>머릿글 표는 화면이 그리지 않는다 - 자리는 잡히는데 아무것도 안 나온다
 * (2026-08-13 실측, Univer 1.0.4 에서도 같다: 표를 그대로 넘기면 머릿글이 통째로
 * 빈다). 표 글을 줄로 펴서 적는다. 칸 글자 서식은 옮긴
 * 자리로 함께 옮긴다 - 원본 머릿글은 9pt 인데 서식을 잃으면 문서 기본 크기로
 * 커진다(2026-08-13 지적).
 */

import type { DocParagraph } from './docBody';
import type { DocImage, DocTable } from './docTable';
import { toBodyWithTables } from './docTable';

/** 칸과 칸 사이 띄우기. 표에서 온 줄이라는 것이 눈에 보일 만큼만 띄운다. */
const GAP = ' ';

export interface DocEdge {
  paragraphs: DocParagraph[];
  tables: DocTable[];
  /** 머릿글 로고 같은 그림. 자리는 표 안이지만 화면에는 줄로 편다. */
  pictures?: unknown[];
}

/**
 * 머릿글·꼬릿글 한 벌의 얼개.
 *
 * <p>표는 화면(Univer 0.25.1)이 그리지 않아 줄로 편다. 그림은 얼개에 넣어야
 * 그려진다 - 안 넣으면 머릿글 로고가 통째로 빠진다(2026-08-25 지적).
 */
export function edgeBody(edge: DocEdge, unitId: string, bodyWidth: number,
  defaultFont: string | null = null, images: DocImage[] = []) {
  const lines = edgeLines(edge);
  // 로고는 표에서 편 첫 글줄과 한 줄에 세운다. 제 문단을 주면 머릿글이 한 줄
  // 높아져 본문이 그만큼 밀린다(2026-08-25 실측: 24쪽이 26쪽).
  const at = lines[0]?.paragraphNo ?? edge.paragraphs[0]?.paragraphNo ?? -1;
  const inline = images.map((image) => ({
    ...image, afterParagraphNo: at, inline: at >= 0, everyPage: true, floatLeft: true,
  }));
  // 그림 이름을 본문과 갈라 둔다. 같은 이름이면 화면이 본문 그림 크기로 그린다
  // (2026-08-25 실측: 머릿글 로고가 표지 로고 크기로 나왔다).
  return toBodyWithTables([...edge.paragraphs, ...lines], [], inline,
    unitId, bodyWidth, 'wo-edge-', defaultFont);
}

interface Run {
  st: number;
  ed: number;
}

/**
 * 표 한 줄의 가로 맞춤.
 *
 * <p>글이 든 칸 가운데 맨 뒤 칸을 따른다 - 원본 머릿글은 로고가 왼쪽 칸,
 * 글이 오른쪽 칸이다(2026-08-25 지적: 우측 정렬이 안 됐다).
 */
function alignOf(table: DocTable, rowNo: number): string | null {
  const aligns = table.cellAligns?.[rowNo];
  if (aligns == null) {
    return null;
  }
  let align = 0;
  for (const [at, one] of aligns.entries()) {
    if ((table.cells[rowNo]?.[at] ?? '').trim() !== '' && one > 0) {
      align = one;
    }
  }
  return align > 1 ? JSON.stringify({ paragraphStyle: { horizontalAlign: align } }) : null;
}

export function edgeLines(edge: DocEdge): DocParagraph[] {
  const lines: DocParagraph[] = [];
  let no = edge.paragraphs.length;

  for (const table of edge.tables) {
    for (const [at, row] of table.cells.entries()) {
      const runs: Run[] = [];
      let text = '';
      for (const [column, cell] of row.entries()) {
        // 칸 글자를 그대로 쓴다. 다듬으면 칸 서식이 가리키는 자리가 어긋난다.
        if (cell.trim() === '') {
          continue;
        }
        if (text !== '') {
          text += GAP;
        }
        for (const run of parseRuns(table.cellRuns?.[at]?.[column] ?? null)) {
          // 칸 안 자리를 줄 안 자리로 옮긴다. 앞 칸 글자와 띄운 만큼 밀린다.
          runs.push({ ...run, st: run.st + text.length, ed: run.ed + text.length });
        }
        text += cell;
      }
      if (text.trim() === '') {
        continue;
      }
      lines.push({
        paragraphNo: no += 1,
        text,
        style: alignOf(table, at),
        runs: runs.length === 0 ? null : JSON.stringify(runs),
      });
    }
  }
  return lines;
}

function parseRuns(runs: string | null): Run[] {
  if (!runs) {
    return [];
  }
  try {
    return JSON.parse(runs) as Run[];
  } catch {
    return [];
  }
}
