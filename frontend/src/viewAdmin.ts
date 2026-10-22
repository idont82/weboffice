/**
 * 뷰어 관리자 화면.
 *
 * <p>파일 이름·열람자 이름은 다른 시스템이 준 값이다. textContent 로만 넣는다 -
 * 남이 준 문자열이 관리자 화면에서 스크립트가 되면 가장 나쁜 자리다.
 */

export interface AdminEvent {
  eventType: string;
  callerSystem: string | null;
  fileName: string | null;
  byteSize: number | null;
  encryptedYn: string | null;
  viewerName: string | null;
  tookMs: number | null;
  resultCode: string | null;
  message: string | null;
  clientIp: string | null;
  changedAt: string | null;
}

export interface AdminStats {
  issued: number;
  opened: number;
  failed: number;
  avgConvertMs: number;
  cacheRows: number;
}

export interface AdminOptions {
  events: AdminEvent[];
  stats: AdminStats;
  onPick: (type: string | null) => void;
}

/** 위쪽 갈래 단추. null 은 "모두" 다. */
const TABS: Array<{ key: string | null; label: string }> = [
  { key: null, label: '모두' },
  { key: 'ISSUE', label: '요청' },
  { key: 'OPEN', label: '열람' },
  { key: 'ERROR', label: '오류' },
  { key: 'ADMIN', label: '관리자' },
];

export function renderAdmin(box: HTMLElement, { events, stats, onPick }: AdminOptions): void {
  box.replaceChildren();
  box.append(statsRow(stats), tabs(onPick), rows(events));
}

function statsRow(stats: AdminStats): HTMLElement {
  const line = document.createElement('div');
  line.className = 'admin-stats';
  const pairs: Array<[string, number]> = [
    ['요청', stats.issued],
    ['열람', stats.opened],
    ['오류', stats.failed],
    ['평균 변환(ms)', stats.avgConvertMs],
    ['캐시에 든 문서', stats.cacheRows],
  ];
  for (const [label, value] of pairs) {
    const cell = document.createElement('div');
    cell.className = 'admin-stat';
    const name = document.createElement('span');
    name.className = 'admin-stat-name';
    name.textContent = label;
    const number = document.createElement('strong');
    number.textContent = String(value);
    cell.append(name, number);
    line.append(cell);
  }
  return line;
}

function tabs(onPick: (type: string | null) => void): HTMLElement {
  const line = document.createElement('div');
  line.className = 'admin-tabs';
  for (const tab of TABS) {
    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = tab.label;
    button.dataset.type = tab.key ?? 'ALL';
    button.addEventListener('click', () => onPick(tab.key));
    line.append(button);
  }
  return line;
}

function rows(events: AdminEvent[]): HTMLElement {
  const list = document.createElement('div');
  list.className = 'admin-list';
  if (events.length === 0) {
    const empty = document.createElement('p');
    empty.className = 'admin-empty';
    empty.textContent = '이력이 없습니다';
    list.append(empty);
    return list;
  }
  for (const one of events) {
    const row = document.createElement('div');
    row.className = 'admin-row';
    const words = [
      whenWord(one.changedAt),
      one.eventType,
      one.callerSystem ?? '',
      one.fileName ?? '',
      one.encryptedYn === 'Y' ? '잠김' : '',
      one.viewerName ?? '',
      one.tookMs == null ? '' : `${one.tookMs}ms`,
      one.resultCode === 'OK' ? '' : (one.resultCode ?? ''),
      one.message ?? '',
      one.clientIp ?? '',
    ];
    for (const word of words) {
      const cell = document.createElement('span');
      cell.className = 'admin-cell';
      cell.textContent = word;
      row.append(cell);
    }
    list.append(row);
  }
  return list;
}

function whenWord(at: string | null): string {
  if (!at) {
    return '';
  }
  const when = new Date(at);
  return Number.isNaN(when.getTime()) ? '' : when.toLocaleString('ko-KR');
}
