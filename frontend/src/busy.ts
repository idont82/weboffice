/**
 * "일하는 중" 알림.
 *
 * <p>글만 바뀌면 눈에 안 띈다 - 파일을 올리는 동안 멈춘 것인지 도는 것인지 알 수
 * 없다(2026-08-13 지적). 도는 표시를 글 앞에 붙인다.
 */

/** 도는 표시의 반. 화면(CSS)에서 이 이름으로 돌린다. */
const SPINNER_CLASS = 'wo-spinner';

export function renderStatus(box: HTMLElement, message: string, busy = false): void {
  box.textContent = '';
  if (busy) {
    const spinner = document.createElement('span');
    spinner.className = SPINNER_CLASS;
    // 읽어 주는 기계에는 글만 들려준다. 도는 그림은 눈으로만 보는 것이다.
    spinner.setAttribute('aria-hidden', 'true');
    box.appendChild(spinner);
  }
  if (message !== '') {
    box.appendChild(document.createTextNode(message));
  }
}

/** 화면 가운데 알림의 반. 화면(CSS)에서 이 이름으로 꾸민다. */
const OVERLAY_CLASS = 'wo-busy';

/**
 * 화면 가운데에 "일하는 중" 을 띄운다.
 *
 * <p>목록 화면에는 상단 알림 줄이 없다. 파일을 올리는 동안 아무 표시가 없어 멈춘
 * 줄 안다(2026-08-13 지적).
 */
export function showBusy(root: HTMLElement, message: string): void {
  let layer = root.querySelector<HTMLElement>(`.${OVERLAY_CLASS}`);
  if (layer == null) {
    layer = document.createElement('div');
    layer.className = OVERLAY_CLASS;
    layer.setAttribute('role', 'status');
    root.appendChild(layer);
  }
  const box = document.createElement('div');
  box.className = 'wo-busy-box';
  renderStatus(box, message, true);
  layer.textContent = '';
  layer.appendChild(box);
}

/** 알림을 걷는다. 안 띄운 채로 불러도 탈이 없다. */
export function hideBusy(root: HTMLElement): void {
  root.querySelector(`.${OVERLAY_CLASS}`)?.remove();
}
