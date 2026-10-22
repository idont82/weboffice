/**
 * 발표 자료 보기.
 *
 * <p>고쳐 쓰지 못한다. 슬라이드에 무엇이 적혀 있는지 보여 주기만 한다 - 쓸 만한
 * 오픈소스 슬라이드 편집기가 Apache/MIT 계열에 없고, Univer 슬라이드는 만드는
 * 중이라 표도 발표 모드도 없다(2026-08-13 조사).
 *
 * <p>조각은 원본 자리에 절대 위치로 앉힌다. 흐름에 두면 자리가 무너지는데, 발표
 * 자료는 자리가 곧 뜻이다.
 *
 * <p>글은 모두 textContent 로 넣는다. 남이 만든 파일의 글이 내 화면에서 스크립트가
 * 되면 안 된다.
 */

import { shrinkRatio } from './shrinkText';

export interface SlideShape {
  left: number;
  top: number;
  width: number;
  height: number;
  /** 채운 색. 안 칠했으면 null. */
  fill: string | null;
  /** 테두리 색. 없으면 null. */
  line: string | null;
  /** 테두리 굵기(px). */
  lineWidth: number;
  /** 도형 모양 이름. 모르는 이름은 네모로 그린다. */
  shape: string;
  /** 시계 방향으로 돌린 각도. */
  rotation: number;
  /** 번지는 색. 단색이면 빈 목록이다. */
  gradient?: { color: string; at: number }[];
  /** 색이 번지는 방향(도). 파워포인트 셈이다 - 0 이 왼쪽에서 오른쪽이다. */
  gradientAngle?: number;
  /** 도형에 깔아 둔 그림의 주소. 없으면 없다. */
  image?: string | null;
  /** 둥근 네모의 모서리. 짧은 변의 비율이다(0.15 면 15%). */
  corner?: number;
  /** 손으로 그린 도형의 꼭짓점. 도형 크기를 1로 본 비율이다. */
  outline?: { x: number; y: number }[];
}

export interface SlideText {
  text: string;
  left: number;
  top: number;
  width: number;
  height: number;
  /** pt. 적혀 있지 않으면 0 이다. */
  fontSize: number;
  bold: boolean;
  /** 글자 색. 안 적혀 있으면 없다. */
  color?: string | null;
  /** 줄마다의 크기·굵기·색. 없으면 상자 전체를 한 모양으로 그린다. */
  lines?: SlideLine[];
  /** 넘치면 글자를 줄여 상자에 맞추라고 적힌 상자인가. */
  shrinkToFit?: boolean;
  /** 글꼴 이름. 안 적혀 있으면 없다. */
  font?: string | null;
  /** 상자 안 세로 맞춤(center·bottom). 위쪽이면 없다. */
  anchor?: string | null;
  /** 상자를 돌려 놓은 각도(도). */
  rotation?: number;
  /** 가로 맞춤(center·right). 왼쪽이면 없다. */
  align?: string | null;
  /** 상자 안쪽 여백(px). */
  padding?: { left: number; top: number; right: number; bottom: number };
}

export interface SlideLine {
  text: string;
  /** pt. 적혀 있지 않으면 0 이다. */
  fontSize: number;
  bold: boolean;
  color?: string | null;
  /** 글꼴 이름. 안 적혀 있으면 없다. */
  font?: string | null;
  /** 글머리 기호. 없으면 없다. */
  bullet?: string | null;
  /** 왼쪽 들여쓰기(px). */
  indent?: number;
  /** 기호를 내어 쓰는 너비(px). */
  hanging?: number;
  /** 가로 맞춤(center·right). 왼쪽이면 없다. */
  align?: string | null;
}

export interface SlideTable {
  cells: SlideCell[][];
  left: number;
  top: number;
  width: number;
  height: number;
}

export interface SlideCell {
  text: string;
  /** 바탕색. 안 칠했으면 없다. */
  fill?: string | null;
  /** 글자 색. 안 적혀 있으면 없다. */
  color?: string | null;
  /** 가로 맞춤(center·right). 왼쪽이면 없다. */
  align?: string | null;
  /** pt. 안 적혀 있으면 0 이다. */
  fontSize?: number;
  bold?: boolean;
  /** 글꼴 이름. 안 적혀 있으면 없다. */
  font?: string | null;
}

export interface SlidePicture {
  url: string;
  left: number;
  top: number;
  width: number;
  height: number;
}

export interface SlideView {
  slideNo: number;
  texts: SlideText[];
  tables: SlideTable[];
  pictures: SlidePicture[];
  notes: string | null;
  /** 색을 칠한 도형. 글자 밑에 깔린다. */
  shapes?: SlideShape[];
  /** 슬라이드 바탕색. 없으면 흰 종이다. */
  background?: string | null;
  /** 바탕에 깐 그림의 주소. 없으면 없다. */
  backgroundImage?: string | null;
}

export interface SlideDeckView {
  width: number;
  height: number;
  slides: SlideView[];
  /** 첫 장에 매길 쪽 번호. 표지를 0쪽으로 두는 자료가 있다. */
  firstSlideNo?: number;
}

/**
 * 적힌 글꼴이 이 PC 에 없을 때 대신 쓸 글꼴. 좁은 것부터 늘어놓는다.
 *
 * <p>system-ui(윈도우에서 Segoe UI)는 오피스 기본 글꼴보다 넓어 줄이 한 칸 일찍
 * 접힌다. 한글 글꼴이 없으면 한글 기호(〮 같은)가 두부로 보인다(2026-08-21 지적).
 */
const FALLBACK_FONTS = 'Aptos, Calibri, "Malgun Gothic", system-ui, sans-serif';

/** 크기를 안 적은 글의 크기(pt). 파워포인트 기본 본문 크기다. */
const DEFAULT_FONT_SIZE = 18;

export function renderSlides(box: HTMLElement, deck: SlideDeckView, scale: number): void {
  box.replaceChildren();
  for (const slide of deck.slides) {
    box.append(...page(deck, slide, scale));
  }
  fitOverflowing(box);
}

/**
 * 상자를 넘친 글을 줄여 맞춘다.
 *
 * <p>파워포인트가 열 때 하는 일이다. 안 하면 긴 글이 상자 밖으로 흘러 아래 것을
 * 덮는다(2026-08-21 지적). 두 번 재는 것은 한 번 줄이면 줄 수가 달라지기 때문이다.
 */
export function fitOverflowing(box: HTMLElement): void {
  for (const one of box.querySelectorAll<HTMLElement>('.slide-text[data-fit="1"]')) {
    for (let round = 0; round < 2; round += 1) {
      const ratio = shrinkRatio(one.scrollHeight, one.clientHeight);
      if (ratio >= 1) {
        break;
      }
      resize(one, ratio);
    }
  }
}

/** 상자와 그 안 줄의 글자 크기에 배율을 먹인다. */
function resize(box: HTMLElement, ratio: number): void {
  const boxes: HTMLElement[] = [box, ...box.querySelectorAll<HTMLElement>('p')];
  for (const one of boxes) {
    const now = Number.parseFloat(one.style.fontSize);
    if (Number.isFinite(now) && now > 0) {
      one.style.fontSize = `${now * ratio}pt`;
    }
  }
}

function page(deck: SlideDeckView, slide: SlideView, scale: number): HTMLElement[] {
  const number = document.createElement('p');
  number.className = 'slide-no';
  number.style.width = `${deck.width * scale}px`;
  // 표지를 0쪽으로 두는 자료가 있다. 자료에 적힌 첫 장 번호부터 센다(2026-08-21 지적).
  const first = deck.firstSlideNo ?? 1;
  number.textContent = `${first + slide.slideNo} / ${deck.slides.length}`;

  const paper = document.createElement('section');
  paper.className = 'slide-page';
  paper.style.width = `${deck.width * scale}px`;
  paper.style.height = `${deck.height * scale}px`;

  // 바탕과 도형이 먼저다. 발표 자료는 색 띠와 사각형이 화면 대부분이라 이것을
  // 빼면 흰 종이에 검은 글씨만 남는다(2026-08-21 지적).
  if (slide.background != null && slide.background !== '') {
    paper.style.background = slide.background;
  }
  // 표지와 머리 띠를 통째로 그림 한 장으로 깐 발표 자료가 흔하다. 안 깔면 흰
  // 종이에 글씨만 남는다(2026-08-21 지적).
  if (slide.backgroundImage != null && slide.backgroundImage !== '') {
    paper.style.backgroundImage = `url(${slide.backgroundImage})`;
    // 파워포인트는 장 크기에 맞춰 늘린다. cover 로 하면 가장자리가 잘린다.
    paper.style.backgroundSize = '100% 100%';
    paper.style.backgroundRepeat = 'no-repeat';
  }
  for (const shape of slide.shapes ?? []) {
    paper.append(painted(shape, scale));
  }
  for (const picture of slide.pictures) {
    paper.append(image(picture, scale));
  }
  for (const table of slide.tables) {
    paper.append(grid(table, scale));
  }
  for (const text of slide.texts) {
    paper.append(words(text, scale));
  }

  const made = [number, paper];
  if (slide.notes != null && slide.notes.trim() !== '') {
    const notes = document.createElement('p');
    notes.className = 'slide-notes';
    notes.style.width = `${deck.width * scale}px`;
    notes.textContent = slide.notes;
    made.push(notes);
  }
  return made;
}

/**
 * 도형 모양을 낸다.
 *
 * <p>동그라미와 둥근 네모는 모서리로, 나머지는 잘라내기(clip-path)로 그린다.
 * 모르는 모양은 네모로 둔다 - 파워포인트에는 수백 가지가 있고, 어설프게 흉내 낸
 * 모양은 네모보다 더 어긋나 보인다.
 */
const CUTS: Record<string, string> = {
  triangle: 'polygon(50% 0%, 100% 100%, 0% 100%)',
  rtTriangle: 'polygon(0% 0%, 0% 100%, 100% 100%)',
  diamond: 'polygon(50% 0%, 100% 50%, 50% 100%, 0% 50%)',
  pentagon: 'polygon(50% 0%, 100% 38%, 82% 100%, 18% 100%, 0% 38%)',
  hexagon: 'polygon(25% 0%, 75% 0%, 100% 50%, 75% 100%, 25% 100%, 0% 50%)',
  star5: 'polygon(50% 0%, 61% 35%, 98% 35%, 68% 57%, 79% 91%, 50% 70%, 21% 91%,'
    + ' 32% 57%, 2% 35%, 39% 35%)',
  rightArrow: 'polygon(0% 25%, 60% 25%, 60% 0%, 100% 50%, 60% 100%, 60% 75%, 0% 75%)',
  leftArrow: 'polygon(40% 0%, 40% 25%, 100% 25%, 100% 75%, 40% 75%, 40% 100%, 0% 50%)',
  upArrow: 'polygon(50% 0%, 100% 40%, 75% 40%, 75% 100%, 25% 100%, 25% 40%, 0% 40%)',
  downArrow: 'polygon(25% 0%, 75% 0%, 75% 60%, 100% 60%, 50% 100%, 0% 60%, 25% 60%)',
  chevron: 'polygon(0% 0%, 75% 0%, 100% 50%, 75% 100%, 0% 100%, 25% 50%)',
  parallelogram: 'polygon(25% 0%, 100% 0%, 75% 100%, 0% 100%)',
  trapezoid: 'polygon(20% 0%, 80% 0%, 100% 100%, 0% 100%)',
  plus: 'polygon(35% 0%, 65% 0%, 65% 35%, 100% 35%, 100% 65%, 65% 65%, 65% 100%,'
    + ' 35% 100%, 35% 65%, 0% 65%, 0% 35%, 35% 35%)',
};

/** 모서리 비율을 못 받았을 때 쓰는 값. 파워포인트 기본과 같다. */
const ROUND_RATIO = 0.167;

function outline(box: HTMLElement, shape: SlideShape): void {
  // 손으로 그린 도형은 꼭짓점이 적혀 있다. 이름표(rect)보다 이쪽이 참이다
  // (2026-08-21 지적).
  if (shape.outline != null && shape.outline.length >= 3) {
    const corners = shape.outline
      .map((corner) => `${corner.x * 100}% ${corner.y * 100}%`)
      .join(', ');
    box.style.clipPath = `polygon(${corners})`;
    return;
  }
  if (shape.shape === 'ellipse') {
    box.style.borderRadius = '50%';
    return;
  }
  if (shape.shape === 'roundRect') {
    // 파워포인트는 모서리를 짧은 변의 비율로 적는다. 못 박으면 원본과 곡률이
    // 다르다(2026-08-21 지적).
    const short = Math.min(shape.width, shape.height);
    const ratio = shape.corner != null && shape.corner > 0 ? shape.corner : ROUND_RATIO;
    box.style.borderRadius = `${Math.round(short * ratio)}px`;
    return;
  }
  const cut = CUTS[shape.shape];
  if (cut != null) {
    box.style.clipPath = cut;
  }
}

/** 색을 칠한 도형 하나. 글자 밑에 깔린다. */
function painted(shape: SlideShape, scale: number): HTMLElement {
  const box = document.createElement('div');
  box.className = 'slide-shape';
  // 맞닿은 두 색 띠 사이에 실오라기 같은 선이 보인다 - 브라우저가 소수 좌표를
  // 1/64 픽셀 격자에 붙이면서 아래 도형이 머리카락만큼 비어져 나오기 때문이다
  // (2026-08-24 지적). 도형은 화면 픽셀에 딱 맞춰 붙여 그 틈을 없앤다.
  snapped(box, shape, scale);
  const spread = shape.gradient ?? [];
  if (shape.image != null && shape.image !== '') {
    // 도형에 깔아 둔 그림. 도형 자리를 채우게 늘린다(파워포인트 기본이 그렇다).
    box.style.backgroundImage = `url(${shape.image})`;
    box.style.backgroundSize = 'cover';
    box.style.backgroundPosition = 'center';
  } else if (spread.length >= 2) {
    // CSS 는 0 도가 아래에서 위다. 파워포인트는 0 도가 왼쪽에서 오른쪽이라 90 을
    // 더해 맞춘다.
    const turn = (shape.gradientAngle ?? 0) + 90;
    const stops = spread
      .map((one) => `${one.color} ${Math.round(one.at * 100)}%`).join(', ');
    box.style.backgroundImage = `linear-gradient(${turn}deg, ${stops})`;
  } else if (shape.fill != null && shape.fill !== '') {
    box.style.background = shape.fill;
  }
  if (shape.line != null && shape.line !== '' && shape.lineWidth > 0) {
    box.style.border = `${Math.max(1, shape.lineWidth * scale)}px solid ${shape.line}`;
  }
  outline(box, shape);
  if (shape.rotation !== 0) {
    box.style.transform = `rotate(${shape.rotation}deg)`;
  }
  return box;
}

function words(text: SlideText, scale: number): HTMLElement {
  const box = document.createElement('div');
  box.className = 'slide-text';
  place(box, text, scale);
  // 글자도 함께 줄여야 원본과 같은 모습이다.
  box.style.fontSize = `${(text.fontSize > 0 ? text.fontSize : DEFAULT_FONT_SIZE) * scale}pt`;
  if (text.shrinkToFit) {
    box.dataset.fit = '1';
  }
  wearFont(box, text.font);
  // 파워포인트 글상자는 안쪽에 여백이 있다. 흘리면 글이 테두리에 붙는다
  // (2026-08-21 지적). 여백은 상자 크기 안에서 먹는다.
  const room = text.padding;
  if (room != null) {
    box.style.boxSizing = 'border-box';
    box.style.padding = `${room.top * scale}px ${room.right * scale}px`
      + ` ${room.bottom * scale}px ${room.left * scale}px`;
  }
  if (text.align != null && text.align !== '') {
    box.style.textAlign = text.align;
  }
  // 비스듬한 띠에 얹은 글이 흔하다. 각도를 흘리면 글만 반듯이 남아 띠 밖으로
  // 삐져나온다(2026-08-21 지적).
  if (text.rotation != null && text.rotation !== 0) {
    box.style.transform = `rotate(${text.rotation}deg)`;
  }
  // 가운데·아래 맞춤 상자는 글이 상자보다 크면 위아래로 고루 넘친다. 위 맞춤으로
  // 그리면 아래로만 흘러 밑에 깔린 것을 덮는다(2026-08-21 지적).
  if (text.anchor === 'center' || text.anchor === 'bottom') {
    box.style.display = 'flex';
    box.style.flexDirection = 'column';
    box.style.justifyContent = text.anchor === 'center' ? 'center' : 'flex-end';
  }
  if (text.bold) {
    box.style.fontWeight = '700';
  }
  if (text.color != null && text.color !== '') {
    box.style.color = text.color;
  }
  // 한 상자 안에서 줄마다 크기가 갈리면 줄을 따로 그린다. 통째로 그리면 제목 크기가
  // 설명 줄까지 번진다(2026-08-21 지적).
  if (text.lines != null && (text.lines.length > 1 || hasBullet(text.lines))) {
    box.append(...text.lines.map((line) => row(line, scale)));
    return box;
  }
  box.textContent = text.text;
  return box;
}

/**
 * 적힌 글꼴을 입힌다.
 *
 * <p>글꼴이 다르면 글 너비가 달라 줄이 엉뚱한 자리에서 접힌다. 그 글꼴이 이 PC 에
 * 없을 때를 대비해 뒤에 기본 글꼴을 붙인다(2026-08-21 지적).
 */
function wearFont(box: HTMLElement, font?: string | null): void {
  if (font == null || font === '') {
    return;
  }
  // 그 글꼴이 이 PC 에 없을 때 대신 쓸 글꼴을 좁은 것부터 늘어놓는다. system-ui
  // (윈도우에서 Segoe UI)는 오피스 기본 글꼴보다 넓어 줄이 한 칸 일찍 접힌다.
  box.style.fontFamily = `"${font.replace(/"/g, '')}", ${FALLBACK_FONTS}`;
}

/** 글머리 기호가 붙은 줄이 하나라도 있는가. 한 줄짜리라도 기호는 그려야 한다. */
function hasBullet(lines: SlideLine[]): boolean {
  return lines.some((line) => line.bullet != null && line.bullet !== '');
}

function row(line: SlideLine, scale: number): HTMLElement {
  const one = document.createElement('p');
  one.textContent = line.text;
  one.style.margin = '0';
  if (line.bullet != null && line.bullet !== '') {
    // 기호와 글 사이는 안 갈라지는 빈칸이다. 줄이 접혀도 기호만 남지 않는다.
    one.textContent = `${line.bullet} ${line.text}`;
    one.style.paddingLeft = `${(line.indent ?? 0) * scale}px`;
    one.style.textIndent = `${-(line.hanging ?? 0) * scale}px`;
  }
  one.style.fontSize = `${(line.fontSize > 0 ? line.fontSize : DEFAULT_FONT_SIZE) * scale}pt`;
  if (line.bold) {
    one.style.fontWeight = '700';
  }
  if (line.color != null && line.color !== '') {
    one.style.color = line.color;
  }
  wearFont(one, line.font);
  if (line.align != null && line.align !== '') {
    one.style.textAlign = line.align;
  }
  return one;
}

function grid(table: SlideTable, scale: number): HTMLElement {
  const box = document.createElement('table');
  box.className = 'slide-table';
  place(box, table, scale);
  // 높이는 칸 글자가 정하게 둔다. 못 박으면 글이 잘린다.
  box.style.height = '';

  const body = document.createElement('tbody');
  for (const row of table.cells) {
    const line = document.createElement('tr');
    for (const cell of row) {
      line.append(cellBox(cell, scale));
    }
    body.append(line);
  }
  box.append(body);
  return box;
}

/**
 * 표의 칸 하나.
 *
 * <p>머리 줄 색·가운데 정렬·글자 크기가 없으면 흰 표에 같은 크기 왼쪽 정렬만 남아
 * 원본과 딴판이다(2026-08-21 지적).
 */
function cellBox(cell: SlideCell, scale: number): HTMLElement {
  const one = document.createElement('td');
  one.textContent = cell.text;
  if (cell.fill != null && cell.fill !== '') {
    one.style.background = cell.fill;
  }
  if (cell.color != null && cell.color !== '') {
    one.style.color = cell.color;
  }
  if (cell.align != null && cell.align !== '') {
    one.style.textAlign = cell.align;
  }
  if (cell.fontSize != null && cell.fontSize > 0) {
    one.style.fontSize = `${cell.fontSize * scale}pt`;
  }
  if (cell.bold) {
    one.style.fontWeight = '700';
  }
  wearFont(one, cell.font);
  return one;
}

function image(picture: SlidePicture, scale: number): HTMLElement {
  const box = document.createElement('img');
  box.className = 'slide-image';
  box.src = picture.url;
  box.alt = '';
  place(box, picture, scale);
  return box;
}

/**
 * 도형을 화면 픽셀 격자에 맞춰 앉힌다.
 *
 * <p>끝 자리(오른쪽·아래)를 기준으로 반올림한다. 맞닿은 두 도형이 같은 경계값을
 * 나눠 가지므로 사이에 틈이 생기지 않는다.
 */
function snapped(box: HTMLElement, shape: SlideShape, scale: number): void {
  const left = Math.round(shape.left * scale);
  const top = Math.round(shape.top * scale);
  box.style.left = `${left}px`;
  box.style.top = `${top}px`;
  box.style.width = `${Math.round((shape.left + shape.width) * scale) - left}px`;
  box.style.height = `${Math.round((shape.top + shape.height) * scale) - top}px`;
}

function place(box: HTMLElement, at: { left: number; top: number; width: number;
  height: number; }, scale: number): void {
  box.style.left = `${at.left * scale}px`;
  box.style.top = `${at.top * scale}px`;
  box.style.width = `${at.width * scale}px`;
  box.style.height = `${at.height * scale}px`;
}
