/**
 * 문단 들여쓰기. 눈금자 손잡이 세 개가 다루는 값이다.
 *
 * <p>화면(Univer)이 줄 왼쪽을 잡는 방법은 이렇다(engine-render 의
 * {@code __getIndentPadding} 실측):
 * <pre>
 *   줄 왼쪽 = indentStart
 *   첫 줄   += indentFirstLine
 *   나머지  += hanging
 * </pre>
 * 워드가 적는 뜻과 다르다 - 워드의 hanging 은 "첫 줄이 앞으로 나온다" 인데
 * 화면은 "나머지 줄이 뒤로 물러난다" 로 쓴다. 글머리표 문단에서 화면이
 * hanging 을 글머리 글자 폭으로 두는 것도 그래서다.
 *
 * <p>그래서 손잡이는 이렇게 잡는다.
 * <ul>
 *   <li>위(첫 줄) = 첫 줄이 시작하는 자리. 글머리표 문단이면 <b>글머리 기호</b> 자리다.
 *   <li>아래 왼쪽 = 둘째 줄부터 시작하는 자리. 글머리표 문단이면 <b>글자</b> 자리다.
 *       끌면 문단이 통째로 따라온다.
 *   <li>아래 오른쪽 = 줄이 끝나는 자리.
 * </ul>
 *
 * <p>길이는 모두 픽셀이다.
 */

/** 눈금 한 칸. 0.25cm 마다 붙는다 - 워드도 이 단위로 붙인다. */
const SNAP = (96 / 2.54) / 4;

/** 문단이 남겨야 할 최소 글 폭. 0 이 되면 문단이 화면에서 사라진다. */
const LEAST_TEXT = SNAP * 2;

/** 화면이 문단에 적어 두는 들여쓰기. 없는 값은 0 으로 본다. */
export interface Indents {
  /** 모든 줄의 왼쪽. */
  indentStart?: number;
  /** 오른쪽 들여쓰기. */
  indentEnd?: number;
  /** 첫 줄에만 더 붙는 거리. */
  indentFirstLine?: number;
  /** 둘째 줄부터 더 붙는 거리(글머리표 문단이면 글머리 글자 폭). */
  hanging?: number;
}

/** 손잡이 세 개가 설 자리. 본문 왼쪽 끝에서 잰 픽셀이다. */
export interface HandlePlaces {
  firstLine: number;
  left: number;
  right: number;
}

export type IndentHandle = 'firstLine' | 'left' | 'right';

/**
 * 문단에 적힌 값과 목록 규칙을 합친 들여쓰기.
 *
 * <p>가져온 목록 문단은 들여쓰기를 제 몸에 안 적는다 - 목록(numbering)이 정한다.
 */
export function effectiveIndents(style: Indents, level?: Indents): Indents {
  if (level == null) {
    return style;
  }
  return {
    indentStart: style.indentStart ?? level.indentStart,
    indentEnd: style.indentEnd ?? level.indentEnd,
    indentFirstLine: style.indentFirstLine ?? level.indentFirstLine,
    hanging: style.hanging ?? level.hanging,
  };
}

/** 손잡이가 설 자리. 본문 폭 안에서 잰다. */
export function indentHandles(indents: Indents, bodyWidth: number): HandlePlaces {
  const start = indents.indentStart ?? 0;
  return {
    firstLine: start + (indents.indentFirstLine ?? 0),
    left: start + (indents.hanging ?? 0),
    right: bodyWidth - (indents.indentEnd ?? 0),
  };
}

/**
 * 손잡이를 끈 뒤의 들여쓰기.
 *
 * <p>아래 왼쪽 손잡이는 문단을 통째로 옮긴다 - 첫 줄과의 간격을 지킨다. 위
 * 손잡이는 첫 줄만 옮긴다. 첫 줄을 나머지보다 앞으로 끌면 나머지 줄이 제자리에
 * 남도록 값을 나눠 적는다.
 */
export function movedIndent(handle: IndentHandle, indents: Indents,
  movedPx: number, bodyWidth: number, free = false): Indents {
  const at = indentHandles(indents, bodyWidth);
  const start = indents.indentStart ?? 0;
  const end = indents.indentEnd ?? 0;
  const most = bodyWidth - end - LEAST_TEXT;

  if (handle === 'right') {
    const right = clamp(snapIndent(at.right + movedPx, free),
      Math.max(at.left, at.firstLine) + LEAST_TEXT, bodyWidth);
    return { ...indents, indentEnd: round(bodyWidth - right) };
  }

  if (handle === 'left') {
    // 문단째 옮긴다. 첫 줄과 나머지 줄 사이 거리는 그대로 둔다.
    const others = clamp(snapIndent(at.left + movedPx, free), 0, most);
    const shift = others - at.left;
    return {
      ...indents,
      indentStart: round(Math.max(0, start + shift)),
    };
  }

  const first = clamp(snapIndent(at.firstLine + movedPx, free), 0, most);
  if (first >= at.left) {
    // 첫 줄이 나머지보다 뒤다. 나머지는 그대로 두고 첫 줄만 민다.
    return {
      ...indents,
      indentStart: round(at.left - (indents.hanging ?? 0)),
      indentFirstLine: round(first - (at.left - (indents.hanging ?? 0))),
    };
  }
  // 첫 줄이 앞으로 나온다. 나머지 줄이 제자리에 남도록 나눠 적는다.
  return {
    ...indents,
    indentStart: round(first),
    indentFirstLine: 0,
    hanging: round(at.left - first),
  };
}

/** 눈금 한 칸에 붙인다. 자유롭게 두면(Alt) 끈 자리 그대로다. */
export function snapIndent(px: number, free = false): number {
  return free ? px : Math.round(px / SNAP) * SNAP;
}

function clamp(value: number, least: number, most: number): number {
  return Math.min(Math.max(value, least), most);
}

/** 소수점 셋째 자리까지. 화면이 그 아래는 못 그린다. */
function round(px: number): number {
  return Math.round(px * 1000) / 1000;
}
