/**
 * 이름표를 언제까지 보일지 정한다.
 *
 * <p>이름표는 자리 <b>위 칸</b>에 그린다. 상자 안에 넣을 자리가 없어서다. 그래서
 * 누가 R15 에 앉으면 R14 의 글자가 안 보인다(2026-08-07 지적). 잠시 뒤 이름표만
 * 감추고 테두리는 남긴다 - 누가 어디 있는지는 테두리로 알 수 있고 이름은 위쪽
 * 접속자 칩에 있다.
 *
 * <p>"잠시" 를 <b>실제로 움직였을 때</b>부터 센다. 자리 목록은 주기적으로 다시
 * 읽으므로 받을 때마다 세면 이름표가 영영 안 사라진다.
 */

export interface SeatSignature {
  /** 자리를 가리키는 값. lockSeq 다. */
  key: string;
  /** 이 자리의 지금 모습. 바뀌면 움직인 것으로 친다. */
  signature: string;
}

/** 자리 하나의 "언제부터 이 모습인가". */
export interface SeatMove {
  since: number;
  signature: string;
}

/**
 * 자리별로 "언제 움직였나" 를 갱신한다. 목록에 없는 자리는 잊는다.
 *
 * <p>모습(signature)을 함께 들고 다닌다. 시각만 남기면 다음 번에 "그대로인지" 를
 * 알 수 없어 받을 때마다 새로 세게 된다.
 */
export function trackMoves(
  previous: Map<string, SeatMove>,
  seats: SeatSignature[],
  now: number,
): Map<string, SeatMove> {
  const moves = new Map<string, SeatMove>();
  for (const seat of seats) {
    const before = previous.get(seat.key);
    const since = before?.signature === seat.signature ? before.since : now;
    moves.set(seat.key, { since, signature: seat.signature });
  }
  return moves;
}

/** 아직 시간이 안 지난 이름표. 경계에서는 아직 보인다 - 깜빡이는 것처럼 보이지 않게. */
export function visibleLabels(
  moves: Map<string, SeatMove>,
  now: number,
  ttlMs: number,
): Set<string> {
  const visible = new Set<string>();
  for (const [key, move] of moves) {
    if (now - move.since <= ttlMs) {
      visible.add(key);
    }
  }
  return visible;
}
