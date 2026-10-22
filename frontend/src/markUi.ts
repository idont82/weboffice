/**
 * 별표와 색 딱지를 그리는 조각들.
 *
 * <p>documentList 에서 떼어 냈다. 목록을 그리는 일과 표시를 다루는 일이 한
 * 파일에 있으면 둘 다 읽기 어려워진다.
 *
 * <p>셈은 fileMarks 가 한다. 여기는 DOM 만 만든다.
 */
import {
  COLORS, COLOR_HEX, type ColorLabel, type MarkFilter, colorName, toggleColor,
} from './fileMarks';

/** 표시를 바꿔 달라는 부탁. 별표와 색을 함께 넘긴다 - 한 행이라 함께 저장된다. */
export type OnMark = (workbookSeq: number, starred: boolean, color: string | null) => void;

/** 색 이름을 바꿔 달라는 부탁. */
export type OnRenameColor = (color: string, name: string) => void;

/** 표시가 달린 목록 한 줄. 그리는 데 필요한 것만 본다. */
export interface MarkedRow {
  workbookSeq: number;
  starred: boolean;
  color: string | null;
}

const STAR_FILLED = '<svg viewBox="0 0 16 16" width="15" height="15" aria-hidden="true">'
  + '<path d="M8 1.6l1.9 3.9 4.3.6-3.1 3 .7 4.3L8 11.4 4.2 13.4l.7-4.3-3.1-3 4.3-.6z"'
  + ' fill="currentColor"/></svg>';

const STAR_EMPTY = '<svg viewBox="0 0 16 16" width="15" height="15" aria-hidden="true">'
  + '<path d="M8 1.6l1.9 3.9 4.3.6-3.1 3 .7 4.3L8 11.4 4.2 13.4l.7-4.3-3.1-3 4.3-.6z"'
  + ' fill="none" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round"/></svg>';

/**
 * 별 단추.
 *
 * <p>목록 한 줄과 카드가 같은 단추를 쓴다. 누르면 곧바로 모양이 바뀐다 - 서버를
 * 기다렸다 바꾸면 눌렀는지 아닌지 알 수 없다.
 */
export function starButton(row: MarkedRow, onMark: OnMark): HTMLElement {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'mark-star';
  button.dataset.workbookSeq = String(row.workbookSeq);
  paintStar(button, row.starred);
  button.addEventListener('click', (event) => {
    // 줄 전체가 「열기」 단추다. 별을 눌렀다고 문서가 열리면 안 된다.
    event.stopPropagation();
    const wanted = button.getAttribute('aria-pressed') !== 'true';
    paintStar(button, wanted);
    onMark(row.workbookSeq, wanted, row.color);
  });
  return button;
}

function paintStar(button: HTMLElement, starred: boolean) {
  button.setAttribute('aria-pressed', String(starred));
  button.title = starred ? '즐겨찾기에서 빼기' : '즐겨찾기에 넣기';
  button.setAttribute('aria-label', button.title);
  button.innerHTML = starred ? STAR_FILLED : STAR_EMPTY;
}

/**
 * 색 단추. 누르면 일곱 색과 「색 없음」이 뜬다.
 *
 * <p>메뉴는 몸통에 붙인다. 줄 안에 두면 줄의 넘침 감추기에 잘린다.
 */
export function colorButton(row: MarkedRow, labels: ColorLabel[],
  onMark: OnMark): HTMLElement {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'mark-color';
  button.dataset.workbookSeq = String(row.workbookSeq);
  paintDot(button, row.color, labels);
  button.addEventListener('click', (event) => {
    event.stopPropagation();
    openColorMenu(button, row, labels, (color) => {
      paintDot(button, color, labels);
      onMark(row.workbookSeq, row.starred, color);
    });
  });
  return button;
}

function paintDot(button: HTMLElement, color: string | null, labels: ColorLabel[]) {
  button.dataset.color = color ?? '';
  button.style.setProperty('--dot', color == null ? 'transparent' : hexOf(color));
  button.title = color == null ? '색 칠하기' : colorName(labels, color);
  button.setAttribute('aria-label', button.title);
}

function hexOf(color: string): string {
  return COLOR_HEX[color as (typeof COLORS)[number]] ?? '#8b8d98';
}

/** 지금 떠 있는 메뉴. 하나만 띄운다 - 여럿이 겹치면 어느 줄 것인지 모른다. */
let openMenu: HTMLElement | null = null;

export function closeColorMenu() {
  openMenu?.remove();
  openMenu = null;
}

function openColorMenu(anchor: HTMLElement, row: MarkedRow, labels: ColorLabel[],
  onPick: (color: string | null) => void) {
  closeColorMenu();
  const menu = document.createElement('div');
  menu.className = 'color-menu';
  menu.setAttribute('role', 'menu');

  for (const color of COLORS) {
    menu.append(colorItem(color, colorName(labels, color), row.color === color, () => {
      closeColorMenu();
      onPick(color);
    }));
  }
  menu.append(colorItem(null, '색 없음', row.color == null, () => {
    closeColorMenu();
    onPick(null);
  }));

  placeUnder(menu, anchor);
}

function colorItem(color: string | null, name: string, picked: boolean,
  onPick: () => void): HTMLElement {
  const item = document.createElement('button');
  item.type = 'button';
  item.className = 'color-item';
  item.dataset.color = color ?? '';
  item.setAttribute('aria-checked', String(picked));
  const dot = document.createElement('span');
  dot.className = 'color-dot';
  dot.style.background = color == null ? 'transparent' : hexOf(color);
  const label = document.createElement('span');
  label.textContent = name;
  item.append(dot, label);
  item.addEventListener('click', (event) => {
    event.stopPropagation();
    onPick();
  });
  return item;
}

/**
 * 거르개 칩 줄.
 *
 * <p>지금 걸린 것이 늘 보인다. 감춰 두면 왜 파일이 안 보이는지 알 수 없다.
 */
export function markChips(filter: MarkFilter, labels: ColorLabel[],
  onChange: (filter: MarkFilter) => void,
  onRenameColor?: OnRenameColor): HTMLElement {
  const bar = document.createElement('div');
  bar.className = 'mark-chips';

  const star = document.createElement('button');
  star.type = 'button';
  star.className = 'mark-chip is-star';
  star.dataset.chip = 'STARRED';
  star.setAttribute('aria-pressed', String(filter.starred));
  const starMark = document.createElement('span');
  starMark.className = 'chip-star';
  starMark.innerHTML = filter.starred ? STAR_FILLED : STAR_EMPTY;
  star.append(starMark, document.createTextNode('즐겨찾기'));
  star.addEventListener('click', () => onChange(
    { starred: !filter.starred, colors: filter.colors },
  ));
  bar.append(star);

  for (const color of COLORS) {
    const chip = document.createElement('button');
    chip.type = 'button';
    chip.className = 'mark-chip';
    chip.dataset.chip = color;
    chip.setAttribute('aria-pressed', String(filter.colors.includes(color)));
    const dot = document.createElement('span');
    dot.className = 'color-dot';
    dot.style.background = COLOR_HEX[color];
    chip.append(dot, document.createTextNode(colorName(labels, color)));
    chip.addEventListener('click', () => onChange(toggleColor(filter, color)));
    bar.append(chip);
  }

  if (onRenameColor) {
    bar.append(renameButton(labels, onRenameColor));
  }
  return bar;
}

/**
 * 색 이름 바꾸기.
 *
 * <p>이름을 칩 자리에서 고치게 하면 거르려고 누른 것인지 고치려고 누른 것인지
 * 갈리지 않는다. 따로 연다.
 */
function renameButton(labels: ColorLabel[], onRenameColor: OnRenameColor): HTMLElement {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'mark-chip is-rename';
  button.dataset.chip = 'RENAME';
  button.textContent = '색 이름';
  button.addEventListener('click', (event) => {
    event.stopPropagation();
    openRenamePanel(button, labels, onRenameColor);
  });
  return button;
}

function openRenamePanel(anchor: HTMLElement, labels: ColorLabel[],
  onRenameColor: OnRenameColor) {
  closeColorMenu();
  const panel = document.createElement('div');
  panel.className = 'color-menu is-rename';

  for (const color of COLORS) {
    const line = document.createElement('label');
    line.className = 'color-rename';
    const dot = document.createElement('span');
    dot.className = 'color-dot';
    dot.style.background = COLOR_HEX[color];
    const input = document.createElement('input');
    input.type = 'text';
    input.maxLength = 20;
    input.value = colorName(labels, color);
    input.dataset.color = color;
    // 적는 동안 서버를 두드리지 않는다. 칸을 벗어나거나 엔터를 칠 때 한 번 보낸다.
    input.addEventListener('change', () => onRenameColor(color, input.value));
    line.append(dot, input);
    panel.append(line);
  }

  // 안쪽을 눌러도 닫히지 않아야 한다. 이름을 적다가 사라지면 못 고친다.
  panel.addEventListener('click', (event) => event.stopPropagation());
  placeUnder(panel, anchor);
}

/** 누른 자리 아래에 띄운다. 바깥을 누르면 닫는다. */
function placeUnder(menu: HTMLElement, anchor: HTMLElement) {
  const box = anchor.getBoundingClientRect();
  menu.style.left = `${Math.round(box.left)}px`;
  menu.style.top = `${Math.round(box.bottom + 4)}px`;
  document.body.append(menu);
  openMenu = menu;
  // 이번 누름은 이미 처리 중이라 다음 누름부터 듣는다.
  window.setTimeout(() => {
    document.addEventListener('click', closeColorMenu, { once: true });
  }, 0);
}
