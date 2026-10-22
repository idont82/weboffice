/**
 * 상자에 안 들어가는 글을 줄이는 셈.
 *
 * <p>파워포인트는 "넘치면 줄여 맞춤"이 켜진 상자의 배율을 파일에 안 적어 두는 일이
 * 흔하다 - 열 때 스스로 잰다. 우리도 그려 놓고 재서 줄인다(2026-08-21 지적).
 */

/** 이보다 더는 줄이지 않는다. 파워포인트도 이쯤에서 멈춘다. */
const FLOOR = 0.25;

/**
 * 넘치는 만큼 줄일 배율.
 *
 * @param needed 글이 실제로 차지한 높이
 * @param room 상자 높이
 */
export function shrinkRatio(needed: number, room: number): number {
  if (needed <= 0 || room <= 0 || needed <= room) {
    return 1;
  }
  // 글자를 줄이면 줄 수도 함께 줄어 높이는 배율보다 더 준다. 한 번에 딱 맞추려
  // 들면 너무 작아진다 - 넘친 만큼만 줄이고 남으면 다시 잰다.
  return Math.max(FLOOR, room / needed);
}
