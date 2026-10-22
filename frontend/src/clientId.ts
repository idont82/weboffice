/**
 * 이 창을 가리키는 표시.
 *
 * <p>저장 요청에 실어 보내면 서버가 실시간 알림에 그대로 붙여 준다. 그러면 그
 * 알림이 <b>내 창이 만든 것</b>인지 알 수 있다 - 아이디로는 못 가린다. 같은 사람이
 * 두 창을 열어 두는 일이 흔하고, 그때 다른 창의 변경은 받아야 한다.
 *
 * <p>왜 필요한가: 자기 저장이 남의 변경으로 보이면 화면에 그 값을 다시 쓰고, 그
 * 쓰기가 되돌리기 이력에 쌓여 Ctrl+Z 가 엉뚱한 것을 되돌린다(2026-08-27 실측).
 */

/** 서버가 읽는 머리 이름. */
export const CLIENT_HEADER = 'X-WO-Client';

let mine: string | null = null;

/** 이 창 표시. 새로고침하면 새로 만든다 - 창이 살아 있는 동안만 같으면 된다. */
export function clientId(): string {
  if (mine == null) {
    mine = `wo-${Math.random().toString(36).slice(2, 10)}${Date.now().toString(36)}`;
  }
  return mine;
}

/** 이 알림이 다른 창에서 온 것인가. 모르면 그렇다고 본다(옛 서버). */
export function fromOtherClient(event: { actorClient?: string | null }): boolean {
  const from = event.actorClient;
  return from == null || from === '' || from !== clientId();
}
