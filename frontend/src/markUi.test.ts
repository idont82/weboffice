import { beforeEach, describe, expect, it, vi } from 'vitest';

import { NO_FILTER } from './fileMarks';
import { closeColorMenu, colorButton, markChips, starButton } from './markUi';

function row(fields: Partial<{ workbookSeq: number; starred: boolean; color: string | null }>
= {}) {
  return { workbookSeq: 1, starred: false, color: null, ...fields };
}

beforeEach(() => {
  document.body.replaceChildren();
  closeColorMenu();
});

describe('starButton', () => {
  it('별표가 없으면 눌리지 않은 모양이다', () => {
    expect(starButton(row(), vi.fn()).getAttribute('aria-pressed')).toBe('false');
  });

  it('누르면 별표를 켜 달라고 부탁한다', () => {
    const onMark = vi.fn();
    const button = starButton(row({ color: 'RED' }), onMark);

    button.click();

    // 색을 함께 보낸다. 안 보내면 별을 누를 때마다 색이 지워진다.
    expect(onMark).toHaveBeenCalledWith(1, true, 'RED');
  });

  it('누르면 서버를 기다리지 않고 모양부터 바꾼다', () => {
    const button = starButton(row(), vi.fn());

    button.click();

    expect(button.getAttribute('aria-pressed')).toBe('true');
  });

  it('별표를 다시 누르면 뺀다', () => {
    const onMark = vi.fn();
    const button = starButton(row({ starred: true }), onMark);

    button.click();

    expect(onMark).toHaveBeenCalledWith(1, false, null);
  });

  it('누름이 줄 바깥으로 새지 않는다', () => {
    // 줄 전체가 「열기」 단추다. 별을 눌렀다고 문서가 열리면 안 된다.
    const onOpen = vi.fn();
    const line = document.createElement('div');
    line.addEventListener('click', onOpen);
    line.append(starButton(row(), vi.fn()));
    document.body.append(line);

    line.querySelector<HTMLElement>('.mark-star')!.click();

    expect(onOpen).not.toHaveBeenCalled();
  });
});

describe('colorButton', () => {
  it('색을 고르면 별표는 그대로 두고 색만 보낸다', () => {
    const onMark = vi.fn();
    const button = colorButton(row({ starred: true }), [], onMark);
    document.body.append(button);

    button.click();
    document.querySelector<HTMLElement>('.color-item[data-color="BLUE"]')!.click();

    expect(onMark).toHaveBeenCalledWith(1, true, 'BLUE');
  });

  it('「색 없음」을 고르면 색을 뗀다', () => {
    const onMark = vi.fn();
    const button = colorButton(row({ color: 'RED' }), [], onMark);
    document.body.append(button);

    button.click();
    document.querySelector<HTMLElement>('.color-item[data-color=""]')!.click();

    expect(onMark).toHaveBeenCalledWith(1, false, null);
  });

  it('메뉴는 한 번에 하나만 뜬다', () => {
    const first = colorButton(row({ workbookSeq: 1 }), [], vi.fn());
    const second = colorButton(row({ workbookSeq: 2 }), [], vi.fn());
    document.body.append(first, second);

    first.click();
    second.click();

    expect(document.querySelectorAll('.color-menu')).toHaveLength(1);
  });

  it('지은 이름으로 색을 부른다', () => {
    const button = colorButton(row(), [{ colorCode: 'RED', labelName: '급함' }], vi.fn());
    document.body.append(button);

    button.click();

    expect(document.querySelector('.color-item[data-color="RED"]')!.textContent)
      .toBe('급함');
  });
});

describe('markChips', () => {
  it('즐겨찾기 칩과 색 칩 일곱 개를 그린다', () => {
    const bar = markChips(NO_FILTER, [], vi.fn());

    expect(bar.querySelectorAll('.mark-chip')).toHaveLength(8);
  });

  it('걸린 칩은 눌린 모양이다', () => {
    // 지금 무엇으로 거르고 있는지 늘 보여야 한다.
    const bar = markChips({ starred: false, colors: ['RED'] }, [], vi.fn());

    expect(bar.querySelector('[data-chip="RED"]')!.getAttribute('aria-pressed'))
      .toBe('true');
  });

  it('색 칩을 누르면 그 색을 더한 거르개를 준다', () => {
    const onChange = vi.fn();
    const bar = markChips(NO_FILTER, [], onChange);

    bar.querySelector<HTMLElement>('[data-chip="BLUE"]')!.click();

    expect(onChange).toHaveBeenCalledWith({ starred: false, colors: ['BLUE'] });
  });

  it('즐겨찾기 칩을 눌러도 고른 색은 남는다', () => {
    const onChange = vi.fn();
    const bar = markChips({ starred: false, colors: ['RED'] }, [], onChange);

    bar.querySelector<HTMLElement>('[data-chip="STARRED"]')!.click();

    expect(onChange).toHaveBeenCalledWith({ starred: true, colors: ['RED'] });
  });

  it('이름 바꾸기를 안 주면 그 단추를 안 그린다', () => {
    const bar = markChips(NO_FILTER, [], vi.fn());

    expect(bar.querySelector('[data-chip="RENAME"]')).toBeNull();
  });

  it('색 이름을 고치면 색과 새 이름을 알린다', () => {
    const onRename = vi.fn();
    const bar = markChips(NO_FILTER, [], vi.fn(), onRename);
    document.body.append(bar);

    bar.querySelector<HTMLElement>('[data-chip="RENAME"]')!.click();
    const input = document.querySelector<HTMLInputElement>('input[data-color="RED"]')!;
    input.value = '급함';
    input.dispatchEvent(new Event('change'));

    expect(onRename).toHaveBeenCalledWith('RED', '급함');
  });
});
