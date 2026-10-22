import { type RestorePoint, pointWord, sizeWord, timeline } from './restorePoints';

/**
 * 오른쪽 기록 패널.
 *
 * <p>칸 하나의 수정 기록(구글 "수정 기록 표시")과 문서 버전 기록이 같은 자리를
 * 쓴다. 둘 다 "언제 · 누가 · 무엇이 무엇으로" 라는 같은 모양이다.
 *
 * <p>제목과 값은 사람이 지은 글이다. innerHTML 로 넣지 않는다 - 남이 지은 시트
 * 이름이 내 화면에서 스크립트가 된다.
 */

export interface HistoryEntry {
  /** INSERT · UPDATE · DELETE */
  changeType: string;
  /** 고치기 전 값. 새로 넣은 칸이면 null. */
  before: string | null;
  /** 고친 뒤 값. 지운 칸이면 null. */
  after: string | null;
  changedAt: string | null;
  changedBy: string | null;
}

export interface HistoryPanelOptions {
  title: string;
  entries: HistoryEntry[];
  onClose: () => void;
  /** 목록 위에 둘 한 줄 설명. 없으면 두지 않는다. */
  notice?: string;
  /** 다시 읽기. 주면 제목 옆에 단추가 붙는다. */
  onRefresh?: () => void;
  /** 비었을 때 할 말. null 이면 아무 말도 안 한다 - 목록을 밖에서 붙일 때 쓴다. */
  emptyWord?: string | null;
}

/** 값이 없을 때 적는 글. 빈칸으로 두면 무엇이 지워졌는지 알 수 없다. */
const EMPTY_WORD = '(빈 칸)';

export function renderHistoryPanel(box: HTMLElement,
                                   { title, entries, onClose, notice, emptyWord,
                                     onRefresh }: HistoryPanelOptions): void {
  box.replaceChildren();

  const head = document.createElement('div');
  head.className = 'history-head';
  const heading = document.createElement('h3');
  heading.textContent = title;
  const close = document.createElement('button');
  close.type = 'button';
  close.className = 'history-close';
  close.setAttribute('aria-label', '기록 닫기');
  close.textContent = '×';
  close.addEventListener('click', onClose);
  head.append(heading);
  if (onRefresh) {
    // 기록을 열어 둔 채로 고치는 일이 잦다. 다시 열지 않고도 새로 읽을 수 있어야 한다.
    const again = document.createElement('button');
    again.type = 'button';
    again.className = 'history-refresh';
    again.title = '다시 읽기';
    again.setAttribute('aria-label', '다시 읽기');
    again.textContent = '↻';
    again.addEventListener('click', onRefresh);
    head.append(again);
  }
  head.append(close);
  box.append(head);

  if (notice) {
    const line = document.createElement('p');
    line.className = 'history-notice';
    line.textContent = notice;
    box.append(line);
  }

  if (entries.length === 0) {
    if (emptyWord !== null) {
      const empty = document.createElement('p');
      empty.className = 'history-empty';
      empty.textContent = emptyWord ?? '기록이 없습니다';
      box.append(empty);
    }
    return;
  }

  const list = document.createElement('ul');
  list.className = 'history-list';
  for (const one of entries) {
    list.append(entryLine(one));
  }
  box.append(list);
}

function entryLine(one: HistoryEntry): HTMLElement {
  const line = document.createElement('li');
  line.className = 'history-entry';

  const when = document.createElement('div');
  when.className = 'history-when';
  when.textContent = [whenWord(one.changedAt), one.changedBy].filter(Boolean).join(' · ');
  line.append(when);

  const values = document.createElement('div');
  values.className = 'history-values';
  // 새로 넣은 칸에는 앞 값이 없다. 화살표만 덩그러니 두면 무엇이 지워진 것처럼 보인다.
  if (one.changeType !== 'INSERT') {
    const before = document.createElement('span');
    before.className = 'history-before';
    before.textContent = one.before ?? EMPTY_WORD;
    const arrow = document.createElement('span');
    arrow.className = 'history-arrow';
    arrow.textContent = '→';
    values.append(before, arrow);
  }
  const after = document.createElement('span');
  after.className = 'history-after';
  after.textContent = one.after ?? EMPTY_WORD;
  values.append(after);
  line.append(values);
  return line;
}

/** 언제. 오늘 것은 시각만, 그 전 것은 날짜까지 적는다. */
export function whenWord(at: string | null): string {
  if (!at) {
    return '';
  }
  const when = new Date(at);
  if (Number.isNaN(when.getTime())) {
    return '';
  }
  const clock = `${when.getHours()}:${String(when.getMinutes()).padStart(2, '0')}`;
  const today = new Date();
  const sameDay = when.getFullYear() === today.getFullYear()
    && when.getMonth() === today.getMonth() && when.getDate() === today.getDate();
  return sameDay ? clock : `${when.getMonth() + 1}월 ${when.getDate()}일 ${clock}`;
}

/** 문서의 한 버전. 서버가 편집 기록을 덩어리로 묶어 준다. */
export interface Version {
  versionId: number;
  fromAt: string | null;
  toAt: string | null;
  changeCount: number;
  editors: string[];
}

export interface VersionPanelOptions {
  versions: Version[];
  onRestore: (versionId: number) => void;
  onClose: () => void;
  /**
   * 복구 지점. 버전과 한 줄기로 섞어 보인다.
   *
   * <p>주지 않으면 그리지 않는다 - 볼 수만 있는 화면(뷰어)에는 없는 기능이다.
   */
  points?: RestorePoint[];
  /** 지금 모습을 복구 지점으로 남긴다. */
  onTakePoint?: (label: string) => void;
  /** 그 지점으로 문서를 되돌린다. */
  onRestorePoint?: (snapshotSeq: number) => void;
  /** 그 지점 내용으로 새 문서를 만든다. */
  onCopyPoint?: (snapshotSeq: number) => void;
  /** 다시 읽기. 주면 제목 옆에 단추가 붙는다. */
  onRefresh?: () => void;
  /** 그 버전에서 바뀐 자리를 물어본다. 펼칠 때 한 번만 부른다. */
  onOpen?: (versionId: number) => Promise<VersionChange[]>;
}

/** 버전 하나가 바꾼 자리. "시트1 · C2" 처럼 사람이 읽는 자리와 값이 온다. */
export interface VersionChange {
  where: string;
  before: string | null;
  after: string | null;
  changedAt: string | null;
  changedBy: string | null;
}

/**
 * 버전 기록.
 *
 * <p>맨 위는 지금 상태다. 그 자리에는 되돌리기를 두지 않는다 - 아무 일도 아닌
 * 단추가 있으면 눌러 보게 된다.
 */
export function renderVersionPanel(box: HTMLElement,
                                   { versions, onRestore, onClose, onOpen, onRefresh,
                                     points = [], onTakePoint, onRestorePoint,
                                     onCopyPoint }: VersionPanelOptions): void {
  const rows = timeline(versions, points);
  renderHistoryPanel(box, {
    title: '버전 기록',
    entries: [],
    notice: rows.length === 0 ? undefined
      : '고른 버전의 값으로 되돌립니다. 복구 지점은 문서를 통째로 되돌립니다',
    emptyWord: rows.length === 0 ? undefined : null,
    onClose,
    onRefresh,
  });
  if (onTakePoint) {
    box.append(takePointBar(onTakePoint));
  }
  if (rows.length === 0) {
    return;
  }

  // "지금 상태" 는 맨 위 버전 줄에만 붙인다. 복구 지점이 맨 위에 와도 그것은
  // 지금 모습이 아니라 담아 둔 모습이다.
  const newest = rows.find((row) => row.kind === 'version');

  const list = document.createElement('ul');
  list.className = 'history-list';
  for (const row of rows) {
    if (row.kind === 'version') {
      list.append(versionLine(row.version, row === newest, onRestore, onOpen));
    } else {
      list.append(pointLine(row.point, onRestorePoint, onCopyPoint));
    }
  }
  box.append(list);
}

/**
 * 「복구 지점 만들기」 줄.
 *
 * <p>이름을 붙일 수 있게 칸을 함께 둔다. 나중에 목록에서 고를 때 "9월 1일 14시"
 * 보다 "보내기 전" 이 알아보기 쉽다.
 */
function takePointBar(onTakePoint: (label: string) => void): HTMLElement {
  const bar = document.createElement('div');
  bar.className = 'point-take';

  const name = document.createElement('input');
  name.type = 'text';
  name.id = 'pointLabel';
  name.maxLength = 50;
  name.placeholder = '이름(없어도 됩니다)';

  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'point-take-button';
  button.textContent = '복구 지점 만들기';
  button.addEventListener('click', () => {
    onTakePoint(name.value.trim());
    name.value = '';
  });
  name.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      button.click();
    }
  });

  bar.append(name, button);
  return bar;
}

/**
 * 복구 지점 한 줄.
 *
 * <p>두 길을 준다. 제자리 복원은 지금 문서를 되돌리고, 새 문서로 복원은 원본을
 * 안 건드린다 - 무엇이 달라졌는지 나란히 놓고 볼 때 쓴다.
 */
function pointLine(point: RestorePoint,
                   onRestorePoint?: (snapshotSeq: number) => void,
                   onCopyPoint?: (snapshotSeq: number) => void): HTMLElement {
  const line = document.createElement('li');
  line.className = 'history-entry is-point';
  line.dataset.point = String(point.snapshotSeq);

  const when = document.createElement('div');
  when.className = 'history-when';
  const badge = document.createElement('span');
  badge.className = 'point-badge';
  badge.textContent = '복구 지점';
  const words = document.createElement('span');
  words.textContent = [whenWord(point.takenAt), pointWord(point), point.takenBy,
    sizeWord(point.bodySize)].filter(Boolean).join(' · ');
  when.append(badge, words);
  line.append(when);

  if (onRestorePoint) {
    const restore = document.createElement('button');
    restore.type = 'button';
    restore.className = 'history-restore';
    restore.dataset.restorePoint = String(point.snapshotSeq);
    restore.textContent = '이 시점으로 되돌리기';
    // 버전 되돌리기와 같은 규칙으로 두 번 누르게 한다. 이쪽은 문서를 통째로
    // 바꾸므로 더더욱 한 번에 가면 안 된다.
    let asked = false;
    restore.addEventListener('click', () => {
      if (!asked) {
        asked = true;
        restore.textContent = '문서를 통째로 되돌릴까요?';
        restore.classList.add('history-restore-asking');
        return;
      }
      onRestorePoint(point.snapshotSeq);
    });
    line.append(restore);
  }
  if (onCopyPoint) {
    const copy = document.createElement('button');
    copy.type = 'button';
    copy.className = 'history-open';
    copy.dataset.copyPoint = String(point.snapshotSeq);
    copy.textContent = '새 문서로 열기';
    copy.addEventListener('click', () => onCopyPoint(point.snapshotSeq));
    line.append(copy);
  }
  return line;
}

function versionLine(one: Version, current: boolean,
                     onRestore: (versionId: number) => void,
                     onOpen?: (versionId: number) => Promise<VersionChange[]>): HTMLElement {
  const line = document.createElement('li');
  line.className = 'history-entry';

  const when = document.createElement('div');
  when.className = 'history-when';
  when.textContent = [whenWord(one.toAt), one.editors.join(', ')].filter(Boolean).join(' · ');
  line.append(when);

  // 펼치면 그 판에서 무엇이 바뀌었는지 물어본다. 수만 보고는 어느 판인지 모른다.
  const what = document.createElement('button');
  what.type = 'button';
  what.className = 'history-open';
  what.dataset.open = String(one.versionId);
  // 수를 적지 않는다. 기록 건수와 실제 바뀐 자리 수가 달라(139 대 2) 펼치면
  // 숫자가 확 줄어 어느 것이 맞는 수인지 헷갈렸다(2026-08-20 지적).
  what.textContent = current ? '지금 상태 · 바뀐 자리 보기' : '바뀐 자리 보기';
  line.append(what);

  const changes = document.createElement('div');
  changes.className = 'history-changes';
  changes.hidden = true;
  line.append(changes);

  let asked = false;
  what.addEventListener('click', () => {
    if (asked) {
      changes.hidden = !changes.hidden;
      return;
    }
    asked = true;
    changes.hidden = false;
    changes.textContent = '읽는 중…';
    void (onOpen?.(one.versionId) ?? Promise.resolve([] as VersionChange[]))
      .then((found) => {
        showChanges(changes, found);
        what.textContent = `${current ? '지금 상태 · ' : ''}바뀐 자리 ${found.length}개`;
      })
      .catch(() => { changes.textContent = '기록을 읽지 못했습니다'; });
  });

  if (!current) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'history-restore';
    button.dataset.version = String(one.versionId);
    button.textContent = '이 버전으로 되돌리기';
    // 두 번 누르게 한다. 창을 띄우지 않는 까닭: 브라우저 알림창은 화면을 붙잡고,
    // 되돌리기는 취소할 수 있는 일이라 그렇게까지 막을 것은 아니다.
    let asked = false;
    button.addEventListener('click', () => {
      if (!asked) {
        asked = true;
        button.textContent = '정말 되돌릴까요?';
        button.classList.add('history-restore-asking');
        return;
      }
      onRestore(one.versionId);
    });
    line.append(button);
  }
  return line;
}

/** 펼친 자리에 값 변화를 적는다. 칸 기록과 같은 모양이다. */
function showChanges(box: HTMLElement, changes: VersionChange[]): void {
  box.replaceChildren();
  if (changes.length === 0) {
    box.textContent = '바뀐 자리를 찾지 못했습니다';
    return;
  }
  for (const one of changes) {
    const line = document.createElement('div');
    line.className = 'history-change';

    const where = document.createElement('span');
    where.className = 'history-where';
    where.textContent = one.where;

    const before = document.createElement('span');
    before.className = 'history-before';
    before.textContent = one.before ?? EMPTY_WORD;
    const arrow = document.createElement('span');
    arrow.className = 'history-arrow';
    arrow.textContent = '→';
    const after = document.createElement('span');
    after.className = 'history-after';
    after.textContent = one.after ?? EMPTY_WORD;

    line.append(where, before, arrow, after);
    box.append(line);
  }
}
