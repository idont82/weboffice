/**
 * 오픈소스 현황 화면.
 *
 * <p>관리자가 묻는 것은 세 가지다 - 무엇을 쓰는가, 무엇에 쓰는가, 배포물에 들어가는
 * 것은 어느 것인가. 그래서 쓰임을 이름 옆에 같이 적고, 배포물에 드는 것과 빌드에서만
 * 쓰는 것을 섞지 않는다.
 *
 * <p>값은 우리가 적은 목록에서 온다. 그래도 textContent 로만 넣는다 - 관리자 화면에서
 * 문자열을 HTML 로 넣는 습관을 남기면 다음 사람이 남이 준 값에도 그렇게 한다.
 */

export interface OssItem {
  coords: string[];
  name: string;
  version: string | null;
  license: string;
  url: string;
  side: 'BACKEND' | 'FRONTEND' | 'BUILD';
  kind: 'OSS' | 'COMMERCIAL' | 'INTERNAL';
  dist: 'SHIPPED' | 'DEV_ONLY';
  what: string;
}

export interface OssOverview {
  open: number;
  closed: number;
  shipped: number;
  devOnly: number;
  licenses: Array<{ license: string; count: number }>;
}

export interface OssPayload {
  items: OssItem[];
  overview: OssOverview;
  /** 고지문을 받아 오는 길. 없으면 단추를 안 그린다. */
  onNotice?: () => Promise<string>;
}

/** 묶음 하나. 어느 줄이 이 묶음에 드는지는 {@link holds} 가 가린다. */
interface Group {
  key: 'SHIPPED' | 'DEV_ONLY' | 'CLOSED';
  title: string;
  hint: string;
  holds: (item: OssItem) => boolean;
}

/**
 * 갈래 셋.
 *
 * <p>차례가 뜻이다 - 먼저 나가는 것, 다음 빌드에만 쓰는 것, 마지막이 오픈소스가
 * 아닌 것. 한 표에 섞으면 무엇이 고지 대상인지 흐려진다.
 */
export const GROUPS: Group[] = [
  {
    key: 'SHIPPED',
    title: '배포물에 들어가는 오픈소스',
    hint: '고지문에 실어야 하는 것들이다.',
    holds: (item) => item.kind === 'OSS' && item.dist === 'SHIPPED',
  },
  {
    key: 'DEV_ONLY',
    title: '빌드 · 시험에서만 쓰는 오픈소스',
    hint: '배포물에 안 들어가 고지 대상이 아니다.',
    holds: (item) => item.kind === 'OSS' && item.dist === 'DEV_ONLY',
  },
  {
    key: 'CLOSED',
    title: '오픈소스가 아닌 것',
    hint: '상용 · 사내 것이다. 계약으로 쓴다.',
    holds: (item) => item.kind !== 'OSS',
  },
];

export function renderOss(box: HTMLElement, payload: OssPayload): void {
  box.replaceChildren();
  box.append(summary(payload.overview));
  if (payload.onNotice) {
    box.append(noticeBox(payload.onNotice));
  }
  for (const group of GROUPS) {
    const rows = payload.items.filter(group.holds);
    if (rows.length === 0) {
      // 빈 묶음을 그리면 "없는 것" 과 "못 읽어 온 것" 이 같아 보인다.
      continue;
    }
    box.append(section(group, rows));
  }
}

function summary(overview: OssOverview): HTMLElement {
  const line = document.createElement('div');
  line.className = 'oss-summary';
  const pairs: Array<[string, string]> = [
    ['오픈소스', String(overview.open)],
    ['오픈소스 아닌 것', String(overview.closed)],
    ['배포물에 들어감', String(overview.shipped)],
    ['빌드 · 시험만', String(overview.devOnly)],
  ];
  for (const [label, value] of pairs) {
    line.append(cell(label, value));
  }
  line.append(licenseCell(overview.licenses));
  return line;
}

/**
 * 라이선스 갈래.
 *
 * <p>한 줄로 이어 붙이면 "MPL-2.0 OR EPL-1.0" 같은 긴 이름이 섞여 어디서 갈라지는지
 * 안 보인다. 딱지 하나에 한 갈래씩 둔다.
 */
function licenseCell(licenses: OssOverview['licenses']): HTMLElement {
  const box = document.createElement('div');
  box.className = 'oss-stat oss-stat-wide';
  const name = document.createElement('span');
  name.className = 'oss-stat-name';
  name.textContent = '라이선스';
  box.append(name);

  if (licenses.length === 0) {
    const none = document.createElement('strong');
    none.textContent = '없음';
    box.append(none);
    return box;
  }

  const chips = document.createElement('div');
  chips.className = 'oss-chips';
  for (const one of licenses) {
    const chip = document.createElement('span');
    chip.className = 'oss-chip';
    chip.textContent = `${one.license} ${one.count}`;
    chips.append(chip);
  }
  box.append(chips);
  return box;
}

function cell(label: string, value: string): HTMLElement {
  const box = document.createElement('div');
  box.className = 'oss-stat';
  const name = document.createElement('span');
  name.className = 'oss-stat-name';
  name.textContent = label;
  const number = document.createElement('strong');
  number.textContent = value;
  box.append(name, number);
  return box;
}

/** 고지문. 눌러야 받아 온다 - 화면을 열 때마다 받으면 쓰지도 않는 글을 매번 만든다. */
function noticeBox(onNotice: () => Promise<string>): HTMLElement {
  const box = document.createElement('div');
  box.className = 'oss-notice';
  const button = document.createElement('button');
  button.type = 'button';
  button.textContent = '배포용 고지문 보기';
  const text = document.createElement('pre');
  text.hidden = true;
  button.addEventListener('click', () => {
    button.disabled = true;
    void onNotice()
      .then((got) => {
        text.textContent = got;
        text.hidden = false;
      })
      .catch(() => {
        text.textContent = '고지문을 읽지 못했습니다.';
        text.hidden = false;
      })
      .finally(() => {
        button.disabled = false;
      });
  });
  box.append(button, text);
  return box;
}

function section(group: Group, rows: OssItem[]): HTMLElement {
  const box = document.createElement('section');
  box.className = 'oss-group';
  box.dataset.group = group.key;

  const title = document.createElement('h2');
  title.textContent = `${group.title} (${rows.length})`;
  const hint = document.createElement('p');
  hint.className = 'oss-hint';
  hint.textContent = group.hint;
  box.append(title, hint);

  for (const row of rows) {
    box.append(line(row));
  }
  return box;
}

function line(item: OssItem): HTMLElement {
  const box = document.createElement('div');
  box.className = 'oss-row';

  const head = document.createElement('div');
  head.className = 'oss-head';
  const name = document.createElement('strong');
  name.textContent = item.name;
  const version = document.createElement('span');
  version.className = 'oss-version';
  // 버전을 빈 칸으로 두면 "못 읽어 왔다" 로 보인다.
  version.textContent = item.version ?? '상위 지정';
  head.append(name, version);

  if (item.coords.length > 1) {
    const many = document.createElement('span');
    many.className = 'oss-coords';
    many.textContent = `패키지 ${item.coords.length}개`;
    head.append(many);
  }

  const license = document.createElement('span');
  license.className = 'oss-license';
  license.textContent = item.license;
  head.append(license);

  if (item.url) {
    const link = document.createElement('a');
    link.href = item.url;
    link.textContent = '출처';
    link.target = '_blank';
    // 새 창이 우리 창을 건드리지 못하게 한다.
    link.rel = 'noopener noreferrer';
    head.append(link);
  }

  const what = document.createElement('div');
  what.className = 'oss-what';
  what.textContent = item.what;

  const where = document.createElement('div');
  where.className = 'oss-where';
  where.textContent = sideName(item.side);

  box.append(head, what, where);
  return box;
}

function sideName(side: OssItem['side']): string {
  if (side === 'BACKEND') {
    return '서버';
  }
  return side === 'FRONTEND' ? '화면' : '빌드';
}
