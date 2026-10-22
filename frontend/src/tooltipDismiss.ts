/**
 * 눌린 뒤에도 남는 말풍선 지우기.
 *
 * <p>툴바 단추에 마우스를 올리면 "글자 색상" 같은 말풍선이 뜬다. 색을 고르고 나면
 * 팔레트는 닫히는데 마우스는 그대로 단추 위에 있어 말풍선만 남는다 - 마우스를
 * 한 번 치웠다 와야 사라진다(2026-08-11 지적).
 *
 * <p>단추에 "마우스가 떠났다" 고 대신 알린다. 진짜 마우스는 그대로 있으므로 다시
 * 움직이면 말풍선이 또 뜬다 - 그것이 맞는 동작이다.
 */

/** 툴바 상자. 없으면(글 문서가 아니면) 아무 일도 하지 않는다. */
export type ToolbarSource = () => HTMLElement | null;

export function dismissTooltipAfterClick(toolbar: ToolbarSource): void {
  // 마지막으로 누른 툴바 단추. 팔레트는 툴바 밖(body)에 뜨므로 색을 고르는
  // 클릭만 보면 어느 단추가 열었는지 알 수 없다.
  let opener: HTMLElement | null = null;

  document.addEventListener('click', (event) => {
    const box = toolbar();
    const target = event.target as HTMLElement | null;
    if (box != null && target != null && box.contains(target)) {
      // 툴바 단추는 button 태그가 아니다. data-u-command 를 단 div 가 말풍선을
      // 여는 자리다 - button 만 찾다가 엉뚱한 자식에게 알려 절반은 그대로 남았다
      // (2026-08-11 지적: 열에 다섯은 말풍선이 안 사라진다).
      opener = target.closest<HTMLElement>(
        '[data-u-command], [class*="univer-toolbar-button"],'
        + ' [class*="univer-toolbar-selector"], button') ?? target;
    }

    // 말풍선을 닫는 것은 눌린 결과가 다 끝난 뒤라야 한다.
    setTimeout(() => {
      if (opener == null) {
        return;
      }
      // 여러 이름으로 알린다. 어느 것을 듣는지는 저쪽(Radix) 사정이다.
      opener.dispatchEvent(new PointerEvent('pointerleave', { bubbles: false }));
      opener.dispatchEvent(new PointerEvent('pointerout', { bubbles: true }));
      opener.dispatchEvent(new MouseEvent('mouseleave', { bubbles: false }));
      opener.dispatchEvent(new MouseEvent('mouseout', { bubbles: true }));
    }, 0);
  }, true);
}
