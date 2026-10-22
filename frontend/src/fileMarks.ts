/**
 * 별표(즐겨찾기)와 색 딱지.
 *
 * <p>여기는 셈만 한다 - 무엇을 남길지, 무엇을 앞으로 뽑을지. 그리는 일은
 * documentList 가 하고, 서버에 남기는 일은 api 가 한다.
 *
 * <p>색은 일곱 가지다. 파인더의 태그와 같은 수인데, 이보다 많으면 색만 보고
 * 무슨 뜻인지 못 알아본다 - 색은 분류가 아니라 눈에 띄게 하는 것이다. 더 잘게
 * 나누고 싶으면 색에 이름을 붙여 쓴다.
 */

export const COLORS = ['RED', 'ORANGE', 'YELLOW', 'GREEN', 'BLUE', 'PURPLE', 'GRAY'] as const;

export type ColorCode = (typeof COLORS)[number];

/** 칩과 점에 쓰는 색. 어두운 바탕에서도 보이도록 중간 밝기로 골랐다. */
export const COLOR_HEX: Record<ColorCode, string> = {
  RED: '#e5484d',
  ORANGE: '#f76b15',
  YELLOW: '#f5d90a',
  GREEN: '#30a46c',
  BLUE: '#3b82f6',
  PURPLE: '#8e4ec6',
  GRAY: '#8b8d98',
};

/** 이름을 안 지었을 때 부르는 말. 서버의 기본 이름과 같다. */
const DEFAULT_NAMES: Record<string, string> = {
  RED: '빨강',
  ORANGE: '주황',
  YELLOW: '노랑',
  GREEN: '초록',
  BLUE: '파랑',
  PURPLE: '보라',
  GRAY: '회색',
};

/** 서버가 준 색 이름 한 줄. */
export interface ColorLabel {
  colorCode: string;
  labelName: string;
}

/** 표시가 달린 목록 한 줄. 목록이든 찾은 것이든 이 두 값만 본다. */
export interface Marked {
  starred: boolean;
  color: string | null;
}

/** 지금 걸어 둔 거르개. */
export interface MarkFilter {
  starred: boolean;
  colors: string[];
}

export const NO_FILTER: MarkFilter = { starred: false, colors: [] };

export function isFiltering(filter: MarkFilter): boolean {
  return filter.starred || filter.colors.length > 0;
}

/**
 * 거르개에 맞는 것만 남긴다.
 *
 * <p>색끼리는 「또는」, 별표와 색 사이는 「그리고」다. 칩을 더 누를수록 좁아지기를
 * 바라기 때문이다 - 넓어지면 누를수록 결과가 늘어 거르는 뜻이 사라진다.
 */
export function matching<T extends Marked>(rows: T[], filter: MarkFilter): T[] {
  if (!isFiltering(filter)) {
    return rows;
  }
  return rows.filter((row) => {
    if (filter.starred && !row.starred) {
      return false;
    }
    return filter.colors.length === 0
      || (row.color != null && filter.colors.includes(row.color));
  });
}

/** 색 칩 하나를 켜거나 끈다. 별표 쪽은 건드리지 않는다. */
export function toggleColor(filter: MarkFilter, color: string): MarkFilter {
  const colors = filter.colors.includes(color)
    ? filter.colors.filter((one) => one !== color)
    : [...filter.colors, color];
  return { starred: filter.starred, colors };
}

/**
 * 별표를 앞 묶음으로 뽑는다.
 *
 * <p>묶음 안 차례는 원래 차례 그대로다. 목록이 수정일 최신순이라 여기서 다시
 * 줄을 세우면 사용자가 기억하는 자리와 어긋난다.
 */
export function splitStarred<T extends Marked>(rows: T[]): { starred: T[]; rest: T[] } {
  const starred: T[] = [];
  const rest: T[] = [];
  for (const row of rows) {
    (row.starred ? starred : rest).push(row);
  }
  return { starred, rest };
}

/** 색을 뭐라고 부를까. 이름을 안 지었으면 기본 색 이름이다. */
export function colorName(labels: ColorLabel[], color: string): string {
  const named = labels.find((one) => one.colorCode === color);
  return named?.labelName ?? DEFAULT_NAMES[color] ?? color;
}
