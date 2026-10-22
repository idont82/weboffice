/**
 * 클립보드로 주고받기.
 *
 * <p>표는 탭으로 칸을, 줄바꿈으로 줄을 나눈 글(TSV)로 오간다 - 엑셀·메일·대화창이
 * 모두 아는 모양이다. 값 안에 탭이나 줄바꿈이 들면 따옴표로 감싼다. 안 감싸면 한
 * 칸이 두 칸으로 갈라져 붙는다.
 */

const TAB = String.fromCharCode(9);
const NL = String.fromCharCode(10);
const QUOTE = String.fromCharCode(34);

/** 표를 붙여넣기 좋은 글로. */
export function tsvOf(values: unknown[][]): string {
  return values.map((row) => row.map(cellText).join(TAB)).join(NL);
}

function cellText(value: unknown): string {
  const text = value == null ? '' : String(value);
  if (!text.includes(TAB) && !text.includes(NL) && !text.includes(QUOTE)) {
    return text;
  }
  return QUOTE + text.split(QUOTE).join(QUOTE + QUOTE) + QUOTE;
}

/**
 * 표를 HTML 로.
 *
 * <p>글만 넣으면 받는 쪽에서 한 칸이 된다. HTML 을 함께 넣어야 엑셀·워드에 표로
 * 붙는다. 값은 사람이 적은 것이라 꺾쇠를 글자로 바꾼다.
 */
export function htmlTableOf(values: unknown[][]): string {
  const rows = values.map((row) => `<tr>${
    row.map((one) => `<td>${escaped(one)}</td>`).join('')}</tr>`).join('');
  return `<table border="1" style="border-collapse:collapse">${rows}</table>`;
}

function escaped(value: unknown): string {
  return (value == null ? '' : String(value))
    .split('&').join('&amp;')
    .split('<').join('&lt;')
    .split('>').join('&gt;');
}

/**
 * 붙여넣은 글을 표로 읽는다.
 *
 * <p>따옴표 안의 탭·줄바꿈은 값의 일부다 - 엑셀이 넣어 주는 모양 그대로 읽는다.
 */
export function readTable(text: string): string[][] {
  if (text === '') {
    return [];
  }
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = '';
  let quoted = false;
  for (let at = 0; at < text.length; at += 1) {
    const letter = text[at];
    if (quoted) {
      if (letter === QUOTE && text[at + 1] === QUOTE) {
        cell += QUOTE;
        at += 1;
      } else if (letter === QUOTE) {
        quoted = false;
      } else {
        cell += letter;
      }
      continue;
    }
    if (letter === QUOTE && cell === '') {
      quoted = true;
    } else if (letter === TAB) {
      row.push(cell);
      cell = '';
    } else if (letter === NL) {
      row.push(cell);
      rows.push(row);
      row = [];
      cell = '';
    } else if (letter !== String.fromCharCode(13)) {
      cell += letter;
    }
  }
  if (cell !== '' || row.length > 0) {
    row.push(cell);
    rows.push(row);
  }
  return rows;
}

/** 잘라 낼 자리. 캔버스 안 픽셀이다. */
export interface CropBox {
  x: number;
  y: number;
  width: number;
  height: number;
}

/**
 * 화면 자리를 캔버스 안 자리로.
 *
 * <p>캔버스 픽셀이 화면 픽셀보다 촘촘할 수 있어(고해상도 화면) 비율을 곱한다.
 * 캔버스 밖으로 나간 만큼은 잘라 낸다 - 밖을 그리면 검은 띠가 남는다.
 *
 * @param canvasRect 캔버스가 화면에서 차지한 자리
 * @param canvasWidth 캔버스 안 가로 픽셀 수
 * @param wanted 잘라 낼 화면 자리
 * @return 잘라 낼 자리. 아예 밖이면 null
 */
export function cropBoxOf(canvasRect: DOMRect, canvasWidth: number,
  wanted: DOMRect): CropBox | null {
  const scale = canvasWidth / canvasRect.width;
  const left = Math.max(0, (wanted.left - canvasRect.left) * scale);
  const top = Math.max(0, (wanted.top - canvasRect.top) * scale);
  const right = Math.min(canvasRect.width * scale,
    (wanted.left + wanted.width - canvasRect.left) * scale);
  const bottom = Math.min(canvasRect.height * scale,
    (wanted.top + wanted.height - canvasRect.top) * scale);
  if (right <= left || bottom <= top) {
    return null;
  }
  return {
    x: Math.round(left), y: Math.round(top),
    width: Math.round(right - left), height: Math.round(bottom - top),
  };
}

/**
 * 표를 클립보드에 넣는다.
 *
 * <p>글(TSV)과 표(HTML)를 함께 넣는다 - 받는 쪽이 아는 것을 고른다. 엑셀·워드는
 * 표로, 대화창·메모장은 글로 붙는다.
 *
 * <p>http 로 열면 navigator.clipboard 자체가 없다(2026-08-26 실측). 그때는 옛
 * execCommand 로 글만 넣는다.
 */
export async function writeTable(values: unknown[][],
  where: Navigator = navigator): Promise<void> {
  const text = tsvOf(values);
  const clipboard = (where as { clipboard?: {
    write?: (items: unknown[]) => Promise<void>;
    writeText?: (text: string) => Promise<void>;
  } }).clipboard;
  const Item = (globalThis as { ClipboardItem?: new (parts: Record<string, Blob>) => unknown })
    .ClipboardItem;
  if (clipboard?.write != null && Item != null && typeof Blob !== 'undefined') {
    await clipboard.write([new Item({
      'text/plain': new Blob([text], { type: 'text/plain' }),
      'text/html': new Blob([htmlTableOf(values)], { type: 'text/html' }),
    })]);
    return;
  }
  if (clipboard?.writeText != null) {
    await clipboard.writeText(text);
    return;
  }
  writeOldWay(text);
}

/**
 * 그림을 클립보드에 넣을 수 있는가.
 *
 * <p>http 로 열면 navigator.clipboard 자체가 없다(2026-08-26 실측). 글은 옛
 * 방법으로 넣을 수 있지만 그림은 길이 없어 내려받기로 돌린다.
 */
export function canCopyImage(where: Navigator = navigator): boolean {
  const clipboard = (where as { clipboard?: { write?: unknown } }).clipboard;
  return clipboard?.write != null
    && (globalThis as { ClipboardItem?: unknown }).ClipboardItem != null;
}

/** 클립보드를 못 쓸 때. 파일로 내려받아 대화창에 끌어다 놓게 한다. */
export function saveImage(image: Blob, name: string): void {
  const link = document.createElement('a');
  link.href = URL.createObjectURL(image);
  link.download = name;
  link.click();
  // 바로 지우면 내려받기가 끊긴다. 한 박자 뒤에 놓아 준다.
  window.setTimeout(() => URL.revokeObjectURL(link.href), 10_000);
}

/** 그림을 클립보드에 넣는다. 옛 방법으로는 그림을 넣을 수 없다. */
export async function writeImage(image: Blob, where: Navigator = navigator): Promise<void> {
  const clipboard = (where as { clipboard?: {
    write?: (items: unknown[]) => Promise<void> } }).clipboard;
  const Item = (globalThis as { ClipboardItem?: new (parts: Record<string, Blob>) => unknown })
    .ClipboardItem;
  if (clipboard?.write == null || Item == null) {
    throw new Error('이 브라우저에서는 그림 복사를 쓸 수 없습니다(https 로 열어야 합니다)');
  }
  await clipboard.write([new Item({ [image.type]: image })]);
}

/** 클립보드 API 가 없을 때. 숨긴 칸에 적어 두고 복사한다. */
function writeOldWay(text: string): void {
  const box = document.createElement('textarea');
  box.value = text;
  box.setAttribute('readonly', '');
  box.style.cssText = 'position:fixed;left:-9999px;top:0';
  document.body.appendChild(box);
  box.select();
  try {
    document.execCommand('copy');
  } finally {
    box.remove();
  }
}

/**
 * 붙여넣은 것에서 그림 파일을 고른다.
 *
 * <p>브라우저마다 {@code files} 에 담기도 하고 {@code items} 에만 담기도 한다.
 * 둘 다 본다. 그림이 없으면 null 이다 - 글 붙여넣기는 격자가 알아서 한다.
 */
export function imageOf(data: DataTransfer | null): File | null {
  if (data == null) {
    return null;
  }
  const file = [...(data.files ?? [])].find((one) => one.type.startsWith('image/'));
  if (file != null) {
    return file;
  }
  for (const item of [...(data.items ?? [])]) {
    if (item.kind === 'file' && item.type.startsWith('image/')) {
      const got = item.getAsFile();
      if (got != null) {
        return got;
      }
    }
  }
  return null;
}
