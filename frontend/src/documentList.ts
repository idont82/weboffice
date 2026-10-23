/**
 * 시작 화면.
 *
 * 엑셀 시작 화면의 얼개를 따른다 - 위에 「새로 시작」 타일 줄, 아래에 최근 문서
 * 카드. 문서가 하나도 없는 사람도 시작할 길이 먼저 보인다.
 *
 * 제목은 사용자가 정한 값이라 innerHTML 로 넣지 않는다. 남이 만든 문서 제목이
 * 내 화면에서 스크립트가 된다.
 */
import { ALL_ICON, DOC_ICON, SHEET_ICON, SLIDE_ICON, iconOf } from './docIcons';
import type { TrashItem, WorkbookHit, WorkbookSummary } from './api';
import {
  type ColorLabel, type MarkFilter, NO_FILTER, isFiltering, matching, splitStarred,
} from './fileMarks';
import {
  type OnMark, type OnRenameColor, colorButton, markChips, starButton,
} from './markUi';
import { PURGE_CHOICES, countToPurge } from './trashPurge';

export interface DocumentListOptions {
  container: HTMLElement;
  workbooks: { mine: WorkbookSummary[]; shared: WorkbookSummary[] };
  onOpen: (workbookSeq: number, workbookName: string) => void;
  onUpload: () => void;
  /** 워드·한글 파일 올리기. 시트(xlsx)와 고르는 창이 달라 따로 받는다. */
  onUploadDoc: () => void;
  /** 발표 자료(pptx) 올리기. 고쳐 쓰지 못하고 보기만 한다(2026-08-13 결정). */
  onUploadSlide: () => void;
  /**
   * 어떤 형식이든 읽기 전용으로 열기.
   *
   * <p>문서함에 안 남기고 잠깐 본다 - 뷰어 길을 그대로 탄다(2026-08-20 결정).
   */
  onView?: () => void;
  /** 종류를 함께 넘긴다. 시트와 글은 만드는 길이 같고 종류만 다르다. */
  onCreate: (docType: 'SHEET' | 'DOC') => void;
  /** 지우기는 소유자만. 공유받은 파일에는 버튼 자체를 두지 않는다. */
  onDelete: (workbookSeq: number, workbookName: string) => void;
  /** 휴지통에 있는 내 문서. 비어 있으면 묶음을 그리지 않는다. */
  trash?: TrashItem[];
  /** 휴지통에서 되살리기. */
  onRestore?: (workbookSeq: number, workbookName: string) => void;
  /**
   * 휴지통 비우기. 되돌릴 수 없다.
   *
   * <p>주지 않으면 비우기 단추를 안 그린다 - 눌러도 아무 일도 안 일어나는 단추를
   * 두지 않는다.
   */
  onPurgeTrash?: (keepDays: number) => void;
  /**
   * 이름이나 내용으로 찾기.
   *
   * <p>주지 않으면 검색칸을 그리지 않는다 - 찾을 길이 없는 화면에 빈 칸만 두지 않는다.
   */
  onSearch?: (words: string) => Promise<{ mine: WorkbookHit[]; shared: WorkbookHit[] }>;
  /**
   * 별표·색을 바꿔 달라는 부탁.
   *
   * <p>주지 않으면 별도 색도 안 그린다 - 누를 수는 있는데 아무 일도 안 일어나는
   * 단추를 두지 않는다.
   */
  onMark?: OnMark;
  /** 색에 붙인 내 이름. 없으면 기본 색 이름으로 부른다. */
  colorLabels?: ColorLabel[];
  /** 색 이름 바꾸기. 주지 않으면 이름 바꾸기 단추를 안 그린다. */
  onRenameColor?: OnRenameColor;
}

/** 표시를 그리는 데 필요한 것 한 벌. 없으면(null) 별도 색도 안 그린다. */
interface MarkTools {
  labels: ColorLabel[];
  onMark: OnMark;
}

/** 글자마다 두드리지 않는다. 멈춘 뒤에 한 번 찾는다. */
const SEARCH_WAIT_MS = 300;

/** 목록이냐 격자냐. 고른 것은 브라우저에 남는다. */
type View = 'list' | 'grid';

const VIEW_KEY = 'wo.documentView';

/**
 * 지금 보기.
 *
 * <p>기본은 목록이다. 한눈에 더 많이 들어오고 수정일·공유 상태를 나란히 견줄 수
 * 있다. 격자는 골라서 쓰는 것이다.
 *
 * <p>localStorage 가 막힌 환경(사생활 보호 모드)에서도 화면은 떠야 한다.
 */
function savedView(): View {
  try {
    return localStorage.getItem(VIEW_KEY) === 'grid' ? 'grid' : 'list';
  } catch {
    return 'list';
  }
}

function saveView(view: View) {
  try {
    localStorage.setItem(VIEW_KEY, view);
  } catch {
    // 못 남겨도 이번 화면에서는 바뀐 대로 보인다.
  }
}

export function renderDocumentList(
  { container, workbooks, onOpen, onUpload, onUploadDoc, onUploadSlide, onCreate,
    onDelete, trash = [], onRestore, onView, onSearch, onMark, colorLabels = [],
    onRenameColor, onPurgeTrash }: DocumentListOptions,
) {
  let view = savedView();
  /** 별표·색 거르개. 다시 그려도 남는다 - 걸어 둔 것이 사라지면 놀란다. */
  let filter: MarkFilter = NO_FILTER;
  const marks: MarkTools | null = onMark ? { labels: colorLabels, onMark } : null;
  let kind: Kind = 'ALL';
  /** 찾은 것. null 이면 평소 목록이다. */
  let found: { mine: WorkbookHit[]; shared: WorkbookHit[] } | null = null;
  let words = '';
  let waiting = 0;

  const draw = () => {
    // 비우지 않으면 새로 고칠 때마다 같은 문서가 여러 줄이 된다.
    container.replaceChildren();
    container.append(heading('Web Office',
      '파일을 새로 만들거나 최근 파일을 이어서 씁니다'));
    if (onSearch) {
      container.append(searchBox());
    }
    if (found != null) {
      // 찾는 동안에는 평소 목록을 감춘다. 둘이 같이 있으면 무엇이 결과인지 모른다.
      if (marks) {
        container.append(markChips(filter, colorLabels, (next) => {
          filter = next;
          draw();
        }, onRenameColor));
      }
      container.append(hitSection('찾은 파일',
        matching([...found.mine, ...found.shared], filter), onOpen, marks));
      return;
    }
    const mine = matching(only(workbooks.mine, kind), filter);
    const shared = matching(only(workbooks.shared, kind), filter);

    container.append(startTiles(onCreate, onUpload, onUploadDoc, onView ?? onUploadSlide));

    // 거르개는 시작 타일 아래, 파일 묶음 바로 위에 둔다. 위에 두면 「새로 시작」
    // 줄과 떨어져 무엇에 걸리는 거르개인지 흐려진다(2026-08-31 지적).
    if (marks) {
      container.append(markChips(filter, colorLabels, (next) => {
        filter = next;
        draw();
      }, onRenameColor));
    }

    // 즐겨찾기는 맨 위 한 묶음으로 모은다 - 내 것과 공유받은 것을 오가며 찾지
    // 않게 하는 것이 별표의 뜻이다. 이미 별표로 거르는 중이면 묶지 않는다.
    const starredMine = isFiltering(filter) ? { starred: [], rest: mine } : splitStarred(mine);
    const starredShared = isFiltering(filter)
      ? { starred: [], rest: shared } : splitStarred(shared);
    const starred = [...starredMine.starred, ...starredShared.starred];
    if (starred.length > 0) {
      container.append(section('즐겨찾기', starred, '', onOpen, null, view, null, marks));
    }

    container.append(
      section('내 파일함', starredMine.rest, emptyWord(kind), onOpen, onDelete,
        view, tools, marks),
      section('공유받은 파일', starredShared.rest, '공유받은 파일이 없습니다',
        onOpen, null, view, null, marks),
    );
    // 휴지통은 있을 때만 보인다. 늘 붙어 있으면 시작 화면이 지저분하다.
    if (trash.length > 0) {
      container.append(trashSection(trash, onRestore, onPurgeTrash));
    }
  };

  /** 검색칸. 적은 값은 다시 그려도 그대로 남고, 글자 자리도 지킨다. */
  const searchBox = () => {
    const box = document.createElement('div');
    box.className = 'doc-search';
    const input = document.createElement('input');
    input.id = 'docSearch';
    input.type = 'search';
    input.placeholder = '파일 이름이나 내용으로 찾기';
    input.value = words;
    input.addEventListener('input', () => {
      words = input.value;
      window.clearTimeout(waiting);
      waiting = window.setTimeout(() => void find(), SEARCH_WAIT_MS);
    });
    box.append(input);
    return box;
  };

  const find = async () => {
    const wanted = words.trim();
    if (wanted === '' || onSearch == null) {
      found = null;
      draw();
      focusSearch();
      return;
    }
    found = await onSearch(wanted);
    draw();
    focusSearch();
  };

  /** 다시 그리면 칸이 새로 만들어진다. 적던 자리로 되돌려 놓아야 이어서 적는다. */
  const focusSearch = () => {
    const input = container.querySelector<HTMLInputElement>('#docSearch');
    if (input != null && document.activeElement !== input) {
      input.focus();
      input.setSelectionRange(input.value.length, input.value.length);
    }
  };

  /** 종류 거르개와 보기 단추를 한 줄에 둔다. 둘 다 목록 전체에 걸린다. */
  const tools = () => {
    const box = document.createElement('div');
    box.className = 'list-tools';
    box.append(kindFilter(kind, (next) => {
      if (next === kind) return;
      kind = next;
      draw();
    }), switcher());
    return box;
  };

  // 단추 줄은 첫 묶음에만 둔다. 보기는 화면 전체에 걸리는 것이라 둘이면 어느 쪽이
  // 무엇을 바꾸는지 알 수 없다.
  const switcher = () => viewSwitcher(view, (next) => {
    if (next === view) return;
    view = next;
    saveView(next);
    draw();
  });

  draw();
}

/**
 * 휴지통 묶음.
 *
 * <p>지운 지 30일까지만 담긴다(서버가 거른다). 되살리기 말고는 아무것도 못 한다 -
 * 지운 문서를 여는 길은 두지 않는다.
 */
/**
 * 비우기 줄.
 *
 * <p>되돌릴 수 없는 일이라 두 가지를 지킨다 - 누르기 전에 몇 개가 사라지는지
 * 보여 주고, 두 번 눌러야 지운다. 브라우저 알림창은 안 쓴다(삭제 단추와 같은 규칙).
 */
function purgeBar(rows: TrashItem[],
                  onPurgeTrash: (keepDays: number) => void): HTMLElement {
  const bar = document.createElement('div');
  bar.className = 'trash-purge';

  for (const choice of PURGE_CHOICES) {
    const count = countToPurge(rows, choice.keepDays);
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'trash-purge-button';
    button.dataset.keepDays = String(choice.keepDays);
    button.textContent = `${choice.label} (${count}개)`;
    // 지울 것이 없으면 누를 수 없다. 눌러도 아무 일이 없으면 고장으로 보인다.
    button.disabled = count === 0;

    let asked = false;
    button.addEventListener('click', () => {
      if (!asked) {
        asked = true;
        button.textContent = `${count}개를 영영 지울까요?`;
        button.classList.add('asking');
        return;
      }
      onPurgeTrash(choice.keepDays);
    });
    bar.append(button);
  }
  return bar;
}

function trashSection(rows: TrashItem[],
                      onRestore: DocumentListOptions['onRestore'],
                      onPurgeTrash?: DocumentListOptions['onPurgeTrash']): HTMLElement {
  const box = document.createElement('section');
  // 접어 둔다. 지운 문서가 수백 건이면 시작 화면이 그것으로 덮인다.
  const fold = document.createElement('details');
  fold.className = 'trash-fold';
  const head = document.createElement('summary');
  head.textContent = `휴지통 (${rows.length})`;
  fold.append(head);
  box.append(fold);

  const notice = document.createElement('p');
  notice.className = 'empty';
  notice.textContent = '지운 지 30일이 지나면 사라집니다';
  fold.append(notice);

  if (onPurgeTrash) {
    fold.append(purgeBar(rows, onPurgeTrash));
  }

  const list = document.createElement('ul');
  list.className = 'trash-list';
  for (const row of rows) {
    const line = document.createElement('li');
    const name = document.createElement('span');
    name.className = 'trash-name';
    name.textContent = row.workbookName;
    const when = document.createElement('span');
    when.className = 'trash-when';
    when.textContent = whenWord(row.deletedAt);
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'trash-restore';
    button.dataset.restore = String(row.workbookSeq);
    button.textContent = '되살리기';
    button.addEventListener('click', () => {
      onRestore?.(row.workbookSeq, row.workbookName);
    });
    line.append(name, when, button);
    list.append(line);
  }
  fold.append(list);
  return box;
}

/** 지운 때. 목록의 수정 일시와 같은 모양으로 적는다. */
function whenWord(at: string | null): string {
  if (!at) {
    return '';
  }
  const when = new Date(at);
  return Number.isNaN(when.getTime()) ? '' : `${when.getMonth() + 1}월 ${when.getDate()}일 지움`;
}

/** 어느 종류만 볼까. 시트와 글 문서가 한 목록에 섞여 있다(2026-08-13 지적). */
type Kind = 'ALL' | 'SHEET' | 'DOC' | 'SLIDE';

/** 고른 종류만 남긴다. */
function only(rows: WorkbookSummary[], kind: Kind): WorkbookSummary[] {
  return kind === 'ALL' ? rows : rows.filter((row) => row.docType === kind);
}

/** 걸러서 하나도 없을 때 할 말. 무엇을 걸렀는지 함께 말한다. */
function emptyWord(kind: Kind): string {
  if (kind === 'SHEET') {
    return '시트가 없습니다';
  }
  if (kind === 'DOC') {
    return '문서가 없습니다';
  }
  if (kind === 'SLIDE') {
    return '슬라이드가 없습니다';
  }
  return '파일이 없습니다';
}

/**
 * 전체·시트·문서·슬라이드 단추 넷.
 *
 * <p>종류 이름은 상표를 안 쓴다 - 엑셀·워드·파워포인트는 남의 제품 이름이라 우리
 * 기능을 그 이름으로 부르면 그 회사 것처럼 읽힌다. 파일 형식은 확장자로 적는다
 * (2026-08-20 결정).
 */
function kindFilter(kind: Kind, onPick: (kind: Kind) => void): HTMLElement {
  const box = document.createElement('div');
  box.className = 'kind-filter';
  box.append(
    kindButton('ALL', '전체', kind, onPick),
    kindButton('SHEET', '시트', kind, onPick),
    kindButton('DOC', '문서', kind, onPick),
    kindButton('SLIDE', '슬라이드', kind, onPick),
  );
  return box;
}

function kindButton(kind: Kind, label: string, picked: Kind,
                    onPick: (kind: Kind) => void): HTMLElement {
  const button = document.createElement('button');
  button.type = 'button';
  button.dataset.kind = kind;
  // 그림은 우리가 만든 것이라 innerHTML 로 넣는다. 이름표는 글자로 넣는다 -
  // 그쪽이 사람이 정한 값이 섞일 자리다.
  const mark = document.createElement('span');
  mark.className = 'kind-mark';
  mark.innerHTML = kindIcon(kind);
  button.append(mark, document.createTextNode(label));
  button.setAttribute('aria-pressed', String(kind === picked));
  button.addEventListener('click', () => onPick(kind));
  return button;
}

/** 거르개에 붙는 그림. 글자만 있으면 한눈에 안 들어온다(2026-08-13 지적). */
function kindIcon(kind: Kind): string {
  if (kind === 'SHEET') {
    return SHEET_ICON;
  }
  if (kind === 'DOC') {
    return DOC_ICON;
  }
  if (kind === 'SLIDE') {
    return SLIDE_ICON;
  }
  return ALL_ICON;
}

/** 목록·격자 단추 둘. 지금 보기가 눌린 채로 보인다. */
function viewSwitcher(view: View, onPick: (view: View) => void): HTMLElement {
  const box = document.createElement('div');
  box.className = 'view-switch';
  box.append(
    viewButton('list', '목록으로 보기', view === 'list', onPick),
    viewButton('grid', '격자로 보기', view === 'grid', onPick),
  );
  return box;
}

function viewButton(view: View, label: string, active: boolean,
                    onPick: (view: View) => void): HTMLElement {
  const button = document.createElement('button');
  button.type = 'button';
  button.dataset.view = view;
  button.title = label;
  button.setAttribute('aria-label', label);
  button.setAttribute('aria-pressed', String(active));
  button.innerHTML = view === 'list' ? LIST_ICON : GRID_ICON;
  button.addEventListener('click', () => onPick(view));
  return button;
}

/** 줄 세 개. 목록 보기. */
const LIST_ICON = '<svg viewBox="0 0 16 16" width="16" height="16" fill="none" '
  + 'stroke="currentColor" stroke-width="1.4" stroke-linecap="round" aria-hidden="true">'
  + '<path d="M2.5 4h11M2.5 8h11M2.5 12h11"/></svg>';

/** 네모 네 개. 격자 보기. */
const GRID_ICON = '<svg viewBox="0 0 16 16" width="16" height="16" fill="none" '
  + 'stroke="currentColor" stroke-width="1.4" aria-hidden="true">'
  + '<rect x="2.5" y="2.5" width="4.5" height="4.5" rx="1"/>'
  + '<rect x="9" y="2.5" width="4.5" height="4.5" rx="1"/>'
  + '<rect x="2.5" y="9" width="4.5" height="4.5" rx="1"/>'
  + '<rect x="9" y="9" width="4.5" height="4.5" rx="1"/></svg>';

function heading(title: string, note: string): HTMLElement {
  const box = document.createElement('header');
  box.className = 'start-head';
  const h1 = document.createElement('h1');
  h1.textContent = title;
  const p = document.createElement('p');
  p.textContent = note;
  box.append(h1, p);
  return box;
}

/** 「새 시트」·「시트 가져오기」 같은 시작 타일. 문서가 없는 사람이 처음 보는 자리다. */
function startTiles(onCreate: DocumentListOptions['onCreate'],
                    onUpload: () => void, onUploadDoc: () => void,
                    onView: () => void): HTMLElement {
  const row = document.createElement('div');
  row.className = 'start-tiles';
  row.append(
    // 이름은 '시트'·'문서'·'슬라이드' 로 짧게 두고, 무엇인지는 마우스를 올렸을 때
    // 푼다. 이름 자체를 길게 하면 타일 폭이 무너진다. 남의 제품 이름(엑셀·워드·
    // 파워포인트)은 안 쓴다 - 형식은 확장자로 적는다(2026-08-20 결정).
    tile({ action: 'create', mark: SHEET_ICON, title: '새 시트', note: '빈 시트로 시작',
           hint: '칸에 값을 넣고 셈하는 문서(xlsx)',
           onClick: () => onCreate('SHEET') }),
    tile({ action: 'doc', mark: DOC_ICON, title: '새 문서', note: '빈 문서로 시작',
           hint: '글을 쓰는 문서(docx)',
           onClick: () => onCreate('DOC') }),
    tile({ action: 'upload', mark: UPLOAD_ICON, title: '시트 가져오기',
           note: 'xlsx 파일 올리기',
           hint: '가지고 있는 xlsx 파일을 올려서 연다',
           onClick: onUpload }),
    // 글 문서 올리기는 편집 화면 메뉴 안에만 있었다. 아직 문서가 없는 사람은
    // 그 화면에 갈 일이 없다.
    tile({ action: 'upload-doc', mark: DOC_UPLOAD_ICON, title: '문서 가져오기',
           note: 'docx · hwp 파일 올리기',
           hint: 'docx 와 hwp(hwpx) 파일을 올려서 연다. hwp 는 글자·서식만 옮겨진다',
           onClick: onUploadDoc }),
    // 슬라이드만 보던 자리를 넓혔다. 어떤 형식이든 읽기 전용으로 연다 - 문서함에
    // 남기지 않고 잠깐 보는 길이다(2026-08-20 결정). 슬라이드는 원래 고쳐 쓸 수
    // 없었으니 이 길로 합쳐진다.
    tile({ action: 'view', mark: SLIDE_ICON, title: '파일 보기',
           note: '읽기 전용으로 열기',
           hint: 'xlsx · docx · hwp · pptx 를 올려서 보기만 한다.'
             + ' 파일함에 남지 않고 한 시간 뒤 사라진다',
           onClick: onView }),
  );
  return row;
}

interface TileOptions {
  action: string;
  /** 아이콘 SVG. 문서 목록과 같은 그림을 쓴다 - 다르면 같은 종류로 안 보인다. */
  mark: string;
  title: string;
  note: string;
  /** 마우스를 올렸을 때 나오는 풀이. 쓰던 프로그램 이름으로 적는다. */
  hint: string;
  /** 없으면 아직 못 쓰는 종류다. 눌리지 않게 둔다. */
  onClick?: () => void;
}

function tile({ action, mark, title, note, hint, onClick }: TileOptions): HTMLElement {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = `tile tile-${action}`;
  button.dataset.action = action;
  button.title = hint;

  const icon = document.createElement('span');
  icon.className = 'tile-mark';
  icon.innerHTML = mark;

  const name = document.createElement('span');
  name.className = 'tile-name';
  name.textContent = title;

  const noteEl = document.createElement('span');
  noteEl.className = 'tile-note';
  noteEl.textContent = note;

  button.append(icon, name, noteEl);
  if (onClick) {
    button.addEventListener('click', onClick);
  } else {
    button.disabled = true;
  }
  return button;
}

/**
 * 찾은 문서 묶음.
 *
 * <p>내용에서 맞았으면 맞은 토막을 함께 보인다 - 이름만으로는 왜 이 문서가
 * 나왔는지 알 수 없다.
 */
function hitSection(title: string, rows: WorkbookHit[],
                    onOpen: DocumentListOptions['onOpen'],
                    marks: MarkTools | null = null): HTMLElement {
  const box = document.createElement('section');
  const head = document.createElement('div');
  head.className = 'section-head';
  const heading = document.createElement('h2');
  heading.textContent = `${title} ${rows.length}건`;
  head.append(heading);
  box.append(head);

  if (rows.length === 0) {
    const notice = document.createElement('p');
    notice.className = 'empty';
    notice.textContent = '찾은 문서가 없습니다';
    box.append(notice);
    return box;
  }

  const list = document.createElement('div');
  list.className = 'doc-rows';
  for (const row of rows) {
    const line = document.createElement('div');
    line.className = 'doc-hit';
    line.append(documentRow(row, onOpen, null, marks));
    if (row.snippet) {
      const where = document.createElement('p');
      where.className = 'doc-snippet';
      where.textContent = row.snippet;
      line.append(where);
    }
    list.append(line);
  }
  box.append(list);
  return box;
}

function section(title: string, rows: WorkbookSummary[], empty: string,
                 onOpen: DocumentListOptions['onOpen'],
                 onDelete: DocumentListOptions['onDelete'] | null,
                 view: View, switcher: (() => HTMLElement) | null,
                 marks: MarkTools | null = null): HTMLElement {
  const box = document.createElement('section');

  // 제목과 보기 단추를 한 줄에 둔다. 단추가 아래로 내려가면 무엇에 걸리는 것인지
  // 흐려진다.
  const head = document.createElement('div');
  head.className = 'section-head';
  const heading = document.createElement('h2');
  heading.textContent = title;
  head.append(heading);
  if (switcher) {
    head.append(switcher());
  }
  box.append(head);

  if (rows.length === 0) {
    const notice = document.createElement('p');
    notice.className = 'empty';
    notice.textContent = empty;
    box.append(notice);
    return box;
  }

  box.append(view === 'grid' ? cardGrid(rows, onOpen, onDelete, marks)
    : rowList(rows, onOpen, onDelete, marks));
  return box;
}

function cardGrid(rows: WorkbookSummary[], onOpen: DocumentListOptions['onOpen'],
                  onDelete: DocumentListOptions['onDelete'] | null,
                  marks: MarkTools | null = null): HTMLElement {
  const grid = document.createElement('div');
  grid.className = 'cards';
  for (const row of rows) {
    grid.append(card(row, onOpen, onDelete, marks));
  }
  return grid;
}

/**
 * 목록 보기.
 *
 * <p>열 이름을 붙인다. 값만 늘어놓으면 가운데 칸이 무엇인지 알 수 없다.
 * 지우기 열은 자리를 늘 잡아 둔다 - 공유받은 파일에서만 비면 줄이 어긋나 보인다.
 */
function rowList(rows: WorkbookSummary[], onOpen: DocumentListOptions['onOpen'],
                 onDelete: DocumentListOptions['onDelete'] | null,
                 marks: MarkTools | null = null): HTMLElement {
  const list = document.createElement('div');
  list.className = 'doc-rows';

  const head = document.createElement('div');
  head.className = 'doc-head';
  for (const label of ['이름', '공유', '수정', '']) {
    const cell = document.createElement('span');
    cell.textContent = label;
    head.append(cell);
  }
  list.append(head);

  for (const row of rows) {
    list.append(documentRow(row, onOpen, onDelete, marks));
  }
  return list;
}

function documentRow(row: WorkbookSummary, onOpen: DocumentListOptions['onOpen'],
                     onDelete: DocumentListOptions['onDelete'] | null,
                     marks: MarkTools | null = null): HTMLElement {
  const line = document.createElement('div');
  line.className = 'doc-row';

  const open = document.createElement('button');
  open.type = 'button';
  open.className = 'doc-open';
  open.dataset.workbookSeq = String(row.workbookSeq);
  open.addEventListener('click', () => onOpen(row.workbookSeq, row.workbookName));

  const icon = document.createElement('span');
  icon.className = 'doc-icon';
  icon.innerHTML = iconOf(row.docType);

  const label = document.createElement('span');
  label.className = 'doc-label';
  label.textContent = row.workbookName;

  // 아이콘을 이름 칸 안에 둔다. 밖에 두면 칸이 하나 더 생겨 머리줄과 어긋난다
  // (2026-08-11 실측: 이름이 '공유' 자리로 밀렸다).
  const name = document.createElement('span');
  name.className = 'doc-name';
  name.append(icon, label);

  const share = document.createElement('span');
  share.className = 'doc-share';
  share.textContent = describeSharing(row);

  const when = document.createElement('span');
  when.className = 'doc-when';
  when.textContent = describeModified(row.modifiedAt);

  open.append(name, share, when);
  line.append(open);

  const tail = document.createElement('span');
  tail.className = 'doc-tail';
  if (marks) {
    // 별과 색을 이름 왼쪽이 아니라 오른쪽 끝에 둔다. 왼쪽에 두면 종류 그림과
    // 나란히 놓여 무엇이 문서 종류인지 흐려진다.
    tail.append(colorButton(row, marks.labels, marks.onMark),
      starButton(row, marks.onMark));
  }
  if (onDelete) {
    tail.append(deleteButton(row, onDelete));
  }
  line.append(tail);
  return line;
}

/** 시트 종이에 올리는 화살표. 만들기(초록)와 색을 나눠 어둡게 둔다. */
const UPLOAD_ICON = '<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true"'
  + ' data-kind="upload">'
  + '<rect x="1.5" y="1.5" width="13" height="13" rx="2.5" fill="#14532d"/>'
  + '<path d="M8 3.6 L11.2 7 H9.1 V10.2 H6.9 V7 H4.8 Z" fill="#ffffff"/>'
  + '<rect x="4.4" y="11.4" width="7.2" height="1.4" rx="0.7" fill="#ffffff"'
  + ' opacity="0.8"/></svg>';

/** 파란 종이에 올리는 화살표. 글 문서 올리기. 시트(초록)와 색으로 갈린다. */
const DOC_UPLOAD_ICON = '<svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true"'
  + ' data-kind="upload-doc">'
  + '<rect x="1.5" y="1.5" width="13" height="13" rx="2.5" fill="#123f7d"/>'
  + '<path d="M8 3.6 L11.2 7 H9.1 V10.2 H6.9 V7 H4.8 Z" fill="#ffffff"/>'
  + '<rect x="4.4" y="11.4" width="7.2" height="1.4" rx="0.7" fill="#ffffff"'
  + ' opacity="0.8"/></svg>';

function card(row: WorkbookSummary,
              onOpen: DocumentListOptions['onOpen'],
              onDelete: DocumentListOptions['onDelete'] | null,
              marks: MarkTools | null = null): HTMLElement {
  const box = document.createElement('div');
  box.className = 'card-box';

  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'card';
  button.dataset.workbookSeq = String(row.workbookSeq);
  button.addEventListener('click', () => onOpen(row.workbookSeq, row.workbookName));

  const name = document.createElement('span');
  name.className = 'card-name';
  name.textContent = row.workbookName;

  const note = document.createElement('span');
  note.className = 'card-note';
  note.textContent = describeSharing(row);

  const when = document.createElement('span');
  when.className = 'card-when';
  when.textContent = describeModified(row.modifiedAt);

  const head = document.createElement('span');
  head.className = 'card-head';
  const mark = document.createElement('span');
  mark.className = 'card-thumb';
  // 이름 첫 글자를 쓰면 같은 글자로 시작하는 시트와 글이 똑같아 보인다. 목록
  // 보기와도 같은 그림을 쓴다.
  mark.innerHTML = iconOf(row.docType);
  head.append(mark, name);

  button.append(head, preview(row), note, when);
  box.append(button);
  if (marks) {
    const tools = document.createElement('span');
    tools.className = 'card-marks';
    tools.append(colorButton(row, marks.labels, marks.onMark),
      starButton(row, marks.onMark));
    box.append(tools);
  }
  if (onDelete) {
    box.append(deleteButton(row, onDelete));
  }
  return box;
}

/**
 * 카드 미리보기.
 *
 * <p>서버가 목록과 함께 첫 시트 앞부분을 담아 준다 - 카드마다 따로 조회하면 목록
 * 한 번에 조회가 문서 수만큼 붙는다.
 *
 * <p>내용이 없으면 표 대신 제목 첫 글자를 크게 보인다. 빈 표를 그리면 고장으로
 * 보인다.
 */
function preview(row: WorkbookSummary): HTMLElement {
  if (!row.preview || row.preview.length === 0) {
    const blank = document.createElement('span');
    blank.className = 'card-blank';
    blank.textContent = [...row.workbookName.trim()][0] ?? '?';
    return blank;
  }

  // 표를 상자로 감싼다. table 에 height 를 주면 최소값으로만 본다 - 내용이 넘치면
  // 늘어나 카드 키가 제각각이 된다(2026-08-11 실측: 109·116·139px).
  const box = document.createElement('div');
  box.className = 'card-preview';

  const table = document.createElement('table');
  const body = document.createElement('tbody');
  for (const cells of row.preview) {
    const line = document.createElement('tr');
    for (const text of cells) {
      const cell = document.createElement('td');
      // 제목과 마찬가지로 사용자가 넣은 값이다. textContent 로만 넣는다.
      cell.textContent = text;
      line.append(cell);
    }
    body.append(line);
  }
  table.append(body);
  box.append(table);
  return box;
}

/**
 * 두 번 눌러야 지운다.
 *
 * 되돌릴 수 없는 일이라 확인을 받는다. 확인 대화상자를 띄우지 않는 이유: 목록에서
 * 지우는 것은 잦은 일이고 그때마다 창이 뜨면 손이 멈춘다. 버튼이 스스로 물어본다.
 */
function deleteButton(row: WorkbookSummary,
                      onDelete: DocumentListOptions['onDelete']): HTMLElement {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'card-delete';
  button.dataset.delete = String(row.workbookSeq);
  button.textContent = '삭제';

  let asked = false;
  button.addEventListener('click', () => {
    if (!asked) {
      asked = true;
      button.textContent = '정말 지울까요?';
      button.classList.add('asking');
      return;
    }
    onDelete(row.workbookSeq, row.workbookName);
  });
  return button;
}

/**
 * 언제 손댔는지.
 *
 * 기록이 없으면 아무것도 적지 않는다. Date 는 잘못된 값에 "Invalid Date" 를 내놓는데,
 * 그것이 화면에 뜨면 사용자는 고장으로 읽는다.
 */
function describeModified(modifiedAt: string | null): string {
  if (!modifiedAt) return '';
  const when = new Date(modifiedAt);
  if (Number.isNaN(when.getTime())) return '';

  const pad = (value: number) => String(value).padStart(2, '0');
  const isToday = new Date().toDateString() === when.toDateString();
  // 오늘 것은 시각이 더 쓸모 있다. 어제 이전은 날짜만으로 충분하다.
  if (isToday) {
    return `오늘 ${pad(when.getHours())}:${pad(when.getMinutes())}`;
  }
  return `${when.getFullYear()}-${pad(when.getMonth() + 1)}-${pad(when.getDate())}`;
}

/** 소유자 이름과 공유 상태 중 그 줄에 의미 있는 것만 적는다. */
function describeSharing(row: WorkbookSummary): string {
  const parts: string[] = [];
  if (row.ownerName && row.role !== 'OWNER') parts.push(row.ownerName);
  if (row.memberCount > 0) parts.push(`공유 ${row.memberCount}명`);
  if (row.shared) parts.push('사내 공개');
  if (row.role === 'VIEWER') parts.push('읽기 전용');
  return parts.join(' · ');
}
