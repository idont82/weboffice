/**
 * 표 칸 너비를 마우스로 끌어 바꾸는 셈.
 *
 * <p>화면 엔진(Univer)에는 표 경계를 끄는 기능이 없다. 우리가 얹는다 - 여기에는
 * 셈만 두고, 손잡이를 그리고 문서를 고치는 일은 화면 쪽(main.ts)이 한다.
 */

/** 칸이 이보다 좁아지지 않는다. 0 이 되면 그 칸의 글을 다시 잡을 수 없다. */
export const MIN_COLUMN_WIDTH = 20;

/** 줄이 이보다 낮아지지 않는다. 글 한 줄이 들어갈 자리는 있어야 한다. */
export const MIN_ROW_HEIGHT = 16;

/** 표 테두리를 잡을 수 있는 언저리. 선 위에 정확히 올리기는 어렵다. */
const EDGE_SLACK = 6;

/**
 * 줄 아래 경계의 y 자리.
 *
 * <p>칸 너비와 달리 마지막 줄 아래도 경계다. 아래로 늘리면 표가 길어질 뿐
 * 종이 밖으로 밀려나지 않는다.
 */
export function rowEdges(heights: number[], top: number): number[] {
  const edges: number[] = [];
  let at = top;
  for (const height of heights) {
    at += height;
    edges.push(at);
  }
  return edges;
}

/**
 * 줄 하나를 끌었을 때의 새 높이.
 *
 * <p>아래 줄에서 뺏지 않는다. 글은 세로로 흐르므로 늘린 만큼 표가 길어지면 된다.
 */
export function resizeRow(heights: number[], rowIndex: number, delta: number): number[] {
  if (rowIndex < 0 || rowIndex >= heights.length) {
    return [...heights];
  }
  const next = [...heights];
  next[rowIndex] = Math.max(MIN_ROW_HEIGHT, heights[rowIndex] + delta);
  return next;
}

/**
 * 표 오른쪽 끝을 끌었을 때의 새 너비.
 *
 * <p>마지막 칸만 늘어나고 표 전체가 넓어진다. 워드·한글도 오른쪽 테두리를 이렇게
 * 다룬다. 본문 폭보다 넓히지는 않는다 - 종이 밖으로 나간 표는 인쇄도 내려받기도
 * 어긋난다.
 */
export function resizeTable(widths: number[], delta: number, maxTotal: number): number[] {
  if (widths.length === 0) {
    return [];
  }
  const last = widths.length - 1;
  const others = widths.slice(0, last).reduce((sum, one) => sum + one, 0);
  const room = Math.max(MIN_COLUMN_WIDTH, maxTotal - others);

  const next = [...widths];
  next[last] = Math.min(room, Math.max(MIN_COLUMN_WIDTH, widths[last] + delta));
  return next;
}

/**
 * 표 왼쪽 끝을 끌었을 때의 새 너비와 들여쓰기.
 *
 * <p>오른쪽 끝은 제자리에 두고 왼쪽 테두리만 움직인다 - 들여쓰기가 그만큼 늘거나
 * 줄고 첫 칸이 반대로 바뀐다. 들여쓰기는 0 아래로 가지 않는다. 음수면 표가 여백을
 * 넘어 종이 밖에 걸린다.
 */
export function resizeTableLeft(widths: number[], indent: number, delta: number,
  maxTotal: number): { widths: number[]; indent: number } {
  if (widths.length === 0) {
    return { widths: [], indent };
  }
  const others = widths.slice(1).reduce((sum, one) => sum + one, 0);
  // 끌 수 있는 만큼만 끈다. 들여쓰기는 0 위로, 첫 칸은 가장 좁은 너비 위로.
  const room = Math.max(MIN_COLUMN_WIDTH, maxTotal - indent - others);
  const moved = Math.max(-indent,
    Math.min(delta, widths[0] - MIN_COLUMN_WIDTH));

  const next = [...widths];
  next[0] = Math.min(room, widths[0] - moved);
  return { widths: next, indent: indent + moved };
}

/** 본문이 이보다 좁아지지 않는다. 글을 넣을 자리는 있어야 한다. */
const MIN_BODY_WIDTH = 60;

/**
 * 눈금자에서 여백을 끌었을 때의 새 여백.
 *
 * <p>0 아래로 가지 않는다 - 음수면 글이 종이 밖에 걸린다. 반대쪽 여백도 넘어서지
 * 않는다 - 본문이 사라지면 글을 넣을 자리가 없다.
 */
export function resizeMargin(margin: number, delta: number, pageWidth: number,
  otherMargin: number): number {
  const most = Math.max(0, pageWidth - otherMargin - MIN_BODY_WIDTH);
  return Math.min(most, Math.max(0, margin + delta));
}

/** 화면에 놓인 표 하나의 자리. */
export interface TableRect {
  tableId: string;
  left: number;
  top: number;
  width: number;
  height: number;
}

/**
 * 마우스가 얹힌 표.
 *
 * <p>표 밖에서는 손잡이를 두지 않는다 - 글자 고르기를 방해할 뿐이다. 테두리
 * 언저리까지는 표로 본다. 선 위에 정확히 올리기는 어렵다.
 */
export function hitTable<T extends TableRect>(rects: T[], x: number, y: number): T | null {
  return rects.find((rect) => x >= rect.left - EDGE_SLACK
    && x <= rect.left + rect.width + EDGE_SLACK
    && y >= rect.top - EDGE_SLACK
    && y <= rect.top + rect.height + EDGE_SLACK) ?? null;
}

/** 본문에 놓인 표 하나가 차지하는 자리. */
export interface TableMark {
  startIndex: number;
  endIndex: number;
  tableId: string;
}

/**
 * 이 자리에 커서가 있을 때 그 커서가 든 표.
 *
 * <p>표 밖이면 아무것도 아니다. 표 밖에서 눈금자에 표 손잡이를 그리면 무엇을
 * 끄는지 알 수 없다.
 */
export function tableAt(tables: TableMark[], offset: number): TableMark | null {
  return tables.find((table) => offset >= table.startIndex && offset <= table.endIndex)
    ?? null;
}

/**
 * 칸 사이 경계의 x 자리.
 *
 * <p>표 바깥 끝은 경계가 아니다 - 그 오른쪽에는 늘려 줄 칸이 없어 표 폭이 변한다.
 */
export function columnEdges(widths: number[], left: number): number[] {
  const edges: number[] = [];
  let at = left;
  for (let index = 0; index < widths.length - 1; index += 1) {
    at += widths[index];
    edges.push(at);
  }
  return edges;
}

/**
 * 경계 하나를 끌었을 때의 새 너비.
 *
 * <p>양옆 두 칸만 바꾸고 표 전체 폭은 그대로 둔다. 폭까지 늘리면 표가 종이 밖으로
 * 나간다.
 */
export function resizeColumns(widths: number[], edgeIndex: number,
  delta: number): number[] {
  if (edgeIndex < 0 || edgeIndex >= widths.length - 1) {
    return [...widths];
  }
  const left = widths[edgeIndex];
  const right = widths[edgeIndex + 1];
  // 양쪽 다 가장 좁은 너비를 지킨다. 끌 수 있는 만큼만 끈다.
  const moved = Math.max(MIN_COLUMN_WIDTH - left,
    Math.min(delta, right - MIN_COLUMN_WIDTH));

  const next = [...widths];
  next[edgeIndex] = left + moved;
  next[edgeIndex + 1] = right - moved;
  return next;
}
