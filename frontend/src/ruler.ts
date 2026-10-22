/**
 * 글 문서 눈금자.
 *
 * <p>페이지 위와 왼쪽에 눈금을 그린다. 종이 문서를 만드는 사람은 "여백이 얼마인가",
 * "여기가 몇 센티인가" 를 늘 본다 - 인쇄해서 결재를 올리는 문서라면 더 그렇다.
 *
 * <p>단위는 센티미터다. 인치를 쓰는 문서가 우리 회사에 없다.
 */

/** CSS 픽셀은 96dpi 다. 1인치 = 2.54cm. */
const PX_PER_CM = 96 / 2.54;

export function cmToPx(cm: number): number {
  return cm * PX_PER_CM;
}

/**
 * A4 세로. Univer 의 길이 단위가 CSS 픽셀이라 그 값으로 적는다.
 *
 * <p>여백은 한글 문서의 관례를 따른다 - 위아래 20mm, 좌우 25mm. 워드 기본(사방
 * 1인치)보다 좌우가 좁아 한 줄에 들어가는 글자 수가 국내 서식과 비슷해진다.
 */
export const PAGE = {
  width: 794,
  height: 1124,
  marginTop: cmToPx(2),
  marginBottom: cmToPx(2),
  marginLeft: cmToPx(2.5),
  marginRight: cmToPx(2.5),
};

export interface RulerTick {
  /** 눈금자 왼쪽(또는 위) 끝에서 잰 자리. 픽셀. */
  px: number;
  /** 1cm 자리인가. 작은 눈금은 0.5cm 마다 온다. */
  major: boolean;
  /** 큰 눈금에만 붙는 숫자. 0.5cm 마다 적으면 눈금자가 글씨로 덮인다. */
  label: string | null;
}

/**
 * 눈금 자리를 만든다.
 *
 * @param lengthPx 눈금자 전체 길이
 * @param originPx 0 으로 삼을 자리. 본문 시작(여백 끝)이다 - 워드·한글이 그렇고
 *                 들여쓰기를 재는 기준도 그 자리다.
 */
export function rulerTicks(lengthPx: number, originPx = 0, scale = 1): RulerTick[] {
  if (lengthPx <= 0) {
    return [];
  }
  // 종이가 줄면 눈금도 같이 줄어야 자와 종이가 맞는다(2026-08-19 지적).
  const perCm = PX_PER_CM * Math.max(scale, 0.01);
  const step = perCm / 2;
  const ticks: RulerTick[] = [];

  // 기준점에서 좌우로 센다. 0 에서 세면 여백이 센티 단위가 아닌 문서(레터는
  // 0.5인치=1.27cm)에서 어느 눈금도 정수 센티에 닿지 않아 숫자가 하나도 안
  // 나온다(2026-08-13 지적).
  const first = originPx - Math.floor(originPx / step) * step;
  for (let at = first; at <= lengthPx + 0.001; at += step) {
    const cm = (at - originPx) / perCm;
    const major = Math.abs(cm - Math.round(cm)) < 0.001;
    ticks.push({ px: at, major, label: major ? String(Math.round(cm)) : null });
  }
  return ticks;
}

/**
 * 종이 왼쪽 끝이 편집 캔버스 안 어디에 오는가.
 *
 * <p>종이는 캔버스 한가운데 놓인다. 재는 크기는 문서에 적힌 종이여야 한다 - 우리
 * 기본(A4)으로 재면 레터 문서에서 11px 씩 밀려 눈금자와 표 손잡이가 실제 줄보다
 * 오른쪽에 붙는다(2026-08-13 지적).
 */
export function paperOffset(canvasWidth: number, pageWidth: number,
  scale: number): number {
  return Math.max(0, (canvasWidth - pageWidth * scale) / 2);
}
