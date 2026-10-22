import { describe, expect, it, vi } from 'vitest';

import { dismissTooltipAfterClick } from './tooltipDismiss';

function setup() {
  document.body.innerHTML = '';
  const toolbar = document.createElement('div');
  toolbar.id = 'toolbar';
  // 실제 툴바 단추는 button 이 아니라 data-u-command 를 단 div 다(2026-08-11 실측).
  const button = document.createElement('div');
  button.setAttribute('data-u-command', 'doc.command.set-inline-format-text-color');
  const icon = document.createElement('span');
  button.append(icon);
  toolbar.append(button);

  // 색 팔레트는 툴바 밖(body)에 붙는다. 실제 화면이 그렇다.
  const popup = document.createElement('div');
  const swatch = document.createElement('span');
  popup.append(swatch);
  document.body.append(toolbar, popup);
  return { toolbar, button, icon, swatch };
}

describe('dismissTooltipAfterClick', () => {
  it('단추를 누르면 그 단추에 떠났다고 알린다', () => {
    // 누른 뒤에도 마우스가 단추 위에 있으면 말풍선이 그대로 남는다
    // (2026-08-11 지적: "글자 색상" 말풍선이 안 사라진다).
    const { button, icon } = setup();
    const left = vi.fn();
    button.addEventListener('pointerleave', left);
    dismissTooltipAfterClick(() => document.getElementById('toolbar'));

    vi.useFakeTimers();
    icon.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    vi.runAllTimers();
    vi.useRealTimers();

    expect(left).toHaveBeenCalled();
  });

  it('팔레트에서 색을 고른 뒤에도 그 단추에 알린다', () => {
    // 팔레트는 툴바 밖에 뜬다. 색을 고르는 클릭만 보면 어느 단추가 열었는지 모른다.
    const { button, icon, swatch } = setup();
    const left = vi.fn();
    button.addEventListener('pointerleave', left);
    dismissTooltipAfterClick(() => document.getElementById('toolbar'));

    vi.useFakeTimers();
    icon.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    vi.runAllTimers();
    left.mockClear();
    swatch.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    vi.runAllTimers();
    vi.useRealTimers();

    expect(left).toHaveBeenCalled();
  });

  it('툴바를 누른 적이 없으면 아무 데도 알리지 않는다', () => {
    const { swatch } = setup();
    const left = vi.fn();
    document.addEventListener('pointerleave', left, true);
    dismissTooltipAfterClick(() => document.getElementById('toolbar'));

    vi.useFakeTimers();
    swatch.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    vi.runAllTimers();
    vi.useRealTimers();

    expect(left).not.toHaveBeenCalled();
  });
});
