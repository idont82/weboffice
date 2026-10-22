/**
 * 열람자 워터마크.
 *
 * <p>DRM 이 걸려 있던 문서에만 얹는다. 화면 촬영을 막지는 못한다 - 찍으면 누가
 * 찍었는지 남는다는 압력이 목적이다(설계 6절).
 *
 * <p>글은 다른 시스템이 준 값이다. textContent 로 넣는다 - innerHTML 로 넣으면
 * 남이 준 문자열이 내 화면에서 스크립트가 된다.
 */

/** 화면을 가득 채우는 데 필요한 개수. 성기면 그 사이로 찍으면 그만이다. */
const ROWS = 6;
const COLUMNS = 3;

/** 글씨를 눕히는 각도. 음수면 오른쪽 위로 올라간다. */
const TILT = -30;

export function watermark(box: HTMLElement, word: string): void {
  box.querySelector('.wo-watermark')?.remove();

  const layer = document.createElement('div');
  layer.className = 'wo-watermark';
  layer.style.position = 'absolute';
  layer.style.inset = '0';
  layer.style.zIndex = '2000';
  layer.style.overflow = 'hidden';
  // 마우스를 가로채지 않는다. 가로채면 문서를 스크롤·선택할 수 없다.
  layer.style.pointerEvents = 'none';

  for (let row = 0; row < ROWS; row++) {
    for (let column = 0; column < COLUMNS; column++) {
      const mark = document.createElement('span');
      mark.className = 'wo-watermark-word';
      mark.textContent = word;
      mark.style.position = 'absolute';
      mark.style.left = `${(column * 100) / COLUMNS + 4}%`;
      mark.style.top = `${(row * 100) / ROWS + 3}%`;
      mark.style.transform = `rotate(${TILT}deg)`;
      mark.style.color = 'rgba(120, 120, 120, .18)';
      mark.style.fontSize = '15px';
      mark.style.whiteSpace = 'nowrap';
      mark.style.userSelect = 'none';
      layer.append(mark);
    }
  }
  box.append(layer);
}
