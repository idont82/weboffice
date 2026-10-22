import { beforeEach, describe, expect, it, vi } from 'vitest';

import { renderDocumentList } from './documentList';
import type { TrashItem, WorkbookSummary } from './api';

function summary(fields: Partial<WorkbookSummary> = {}): WorkbookSummary {
  return {
    workbookSeq: 1, workbookName: '문서', ownerName: null,
    role: 'OWNER', memberCount: 0, shared: false, preview: [], docType: 'SHEET',
    modifiedAt: '2026-08-05T14:30:00', starred: false, color: null, ...fields,
  };
}

function render(mine: WorkbookSummary[], shared: WorkbookSummary[] = [],
                trash: TrashItem[] = []) {
  const container = document.createElement('div');
  const onOpen = vi.fn();
  const onUpload = vi.fn();
  const onCreate = vi.fn();
  const onDelete = vi.fn();
  const onRestore = vi.fn();
  renderDocumentList({
    container, workbooks: { mine, shared }, trash, onOpen, onUpload,
    onUploadDoc: vi.fn(), onUploadSlide: vi.fn(), onCreate, onDelete, onRestore,
  });
  return { container, onOpen, onUpload, onCreate, onDelete, onRestore };
}

describe('renderDocumentList', () => {
  beforeEach(() => {
    // 보기 선택이 localStorage 에 남는다. 앞 검사가 고른 것이 다음으로 새면 안 된다.
    localStorage.clear();
  });

  it('격자 카드에 미리보기 표를 그린다', () => {
    // 제목 첫 글자만으로는 어느 것이 무엇인지 알 수 없다. 내용이 조금 보여야 고른다.
    const { container } = render([summary({ preview: [['지점', '매출'], ['서울', '100']] })]);
    container.querySelector<HTMLElement>('[data-view="grid"]')!.click();

    const cells = container.querySelectorAll('.card-preview td');
    expect(cells.length).toBe(4);
    expect(cells[0].textContent).toBe('지점');
    expect(cells[3].textContent).toBe('100');
  });

  it('미리보기가 없으면 첫 글자를 크게 보인다', () => {
    // 빈 문서에 빈 표를 그리면 고장으로 보인다.
    const { container } = render([summary({ workbookName: '새 문서', preview: [] })]);
    container.querySelector<HTMLElement>('[data-view="grid"]')!.click();

    expect(container.querySelector('.card-preview')).toBeNull();
    expect(container.querySelector('.card-blank')?.textContent).toBe('새');
  });

  it('새 문서 타일이 눌린다', () => {
    // "준비 중" 딱지를 떼고 실제로 만든다. 시트와 글은 만드는 길이 같고 종류만 다르다.
    const container = document.createElement('div');
    const onCreate = vi.fn();
    renderDocumentList({
      container, workbooks: { mine: [], shared: [] },
      onOpen: vi.fn(), onUpload: vi.fn(), onUploadDoc: vi.fn(), onUploadSlide: vi.fn(),
      onCreate, onDelete: vi.fn(),
    });

    container.querySelector<HTMLElement>('[data-action="doc"]')!.click();

    expect(onCreate).toHaveBeenCalledWith('DOC');
  });

  it('새 시트 타일은 시트로 만든다', () => {
    const container = document.createElement('div');
    const onCreate = vi.fn();
    renderDocumentList({
      container, workbooks: { mine: [], shared: [] },
      onOpen: vi.fn(), onUpload: vi.fn(), onUploadDoc: vi.fn(), onUploadSlide: vi.fn(),
      onCreate, onDelete: vi.fn(),
    });

    container.querySelector<HTMLElement>('[data-action="create"]')!.click();

    expect(onCreate).toHaveBeenCalledWith('SHEET');
  });

  it('글 문서는 목록에서 다른 아이콘을 쓴다', () => {
    // 종류를 못 가리면 열어 보고서야 안다.
    const { container } = render([summary({ docType: 'DOC' })]);

    expect(container.querySelector('.doc-icon [data-kind="doc"]')).not.toBeNull();
  });

  it('처음에는 목록으로 보인다', () => {
    // 목록이 한눈에 더 많이 들어온다. 격자는 골라서 쓰는 것이다.
    const { container } = render([summary({ workbookName: '문서' })]);

    expect(container.querySelector('.doc-rows')).not.toBeNull();
    expect(container.querySelector('.cards')).toBeNull();
    expect(container.querySelector('[data-view="list"]')!.getAttribute('aria-pressed'))
      .toBe('true');
  });

  it('격자 단추를 누르면 격자로 바뀐다', () => {
    const { container } = render([summary({ workbookName: '문서' })]);

    container.querySelector<HTMLElement>('[data-view="grid"]')!.click();

    expect(container.querySelector('.cards')).not.toBeNull();
    expect(container.querySelector('.doc-rows')).toBeNull();
  });

  it('고른 보기를 기억한다', () => {
    // 열 때마다 다시 고르게 하면 고른 적이 없는 것과 같다.
    const first = render([summary({ workbookName: '문서' })]);
    first.container.querySelector<HTMLElement>('[data-view="grid"]')!.click();

    const second = render([summary({ workbookName: '문서' })]);

    expect(second.container.querySelector('.cards')).not.toBeNull();
  });

  it('목록에서도 열고 지운다', () => {
    const { container, onOpen, onDelete } = render(
      [summary({ workbookSeq: 9, workbookName: '문서' })]);

    container.querySelector<HTMLElement>('[data-workbook-seq="9"]')!.click();
    expect(onOpen).toHaveBeenCalledWith(9, '문서');

    const remove = container.querySelector<HTMLElement>('[data-delete="9"]')!;
    remove.click();
    remove.click();
    expect(onDelete).toHaveBeenCalledWith(9, '문서');
  });

  it('목록에는 열 이름을 붙인다', () => {
    // 값만 늘어놓으면 가운데 칸이 무엇인지 알 수 없다.
    const { container } = render([summary({ workbookName: '문서' })]);

    const head = container.querySelector('.doc-head')!;
    expect(head.textContent).toContain('이름');
    expect(head.textContent).toContain('공유');
    expect(head.textContent).toContain('수정');
  });

  it('내 문서와 공유받은 문서를 나눠 보여준다', () => {
    const { container } = render(
      [summary({ workbookName: '내 문서' })],
      // 공유받은 문서의 역할은 결코 OWNER 가 아니다. 서버가 소유 여부로 목록을
      // 가른다. 소유자 이름은 내 문서가 아닐 때만 의미가 있다.
      [summary({ workbookSeq: 2, workbookName: '남의 문서', ownerName: '홍길동',
                 role: 'EDITOR' })]);

    expect(container.textContent).toContain('내 문서');
    expect(container.textContent).toContain('남의 문서');
    expect(container.textContent).toContain('홍길동');
  });

  it('공유 인원과 공개 여부를 보여준다', () => {
    const { container } = render([summary({ memberCount: 3 })]);
    expect(container.textContent).toContain('공유 3명');

    const open = render([summary({ shared: true })]);
    expect(open.container.textContent).toContain('사내 공개');
  });

  it('눌러서 연다', () => {
    const { container, onOpen } = render([summary({ workbookSeq: 7, workbookName: '문서' })]);

    container.querySelector<HTMLElement>('[data-workbook-seq="7"]')!.click();

    expect(onOpen).toHaveBeenCalledWith(7, '문서');
  });

  it('새 시트와 올리기 타일을 준다', () => {
    // 문서가 하나도 없어도 시작할 길이 보여야 한다.
    const container = document.createElement('div');
    const onCreate = vi.fn();
    const onUpload = vi.fn();
    renderDocumentList({
      container, workbooks: { mine: [], shared: [] },
      onOpen: vi.fn(), onUpload, onUploadDoc: vi.fn(), onUploadSlide: vi.fn(), onCreate, onDelete: vi.fn(),
    });

    container.querySelector<HTMLElement>('[data-action="create"]')!.click();
    container.querySelector<HTMLElement>('[data-action="upload"]')!.click();

    expect(onCreate).toHaveBeenCalled();
    expect(onUpload).toHaveBeenCalled();
  });

  it('타일에 마우스를 올리면 무엇인지 알려준다', () => {
    // '시트'·'문서' 만 보고는 무엇을 여는 것인지 모른다. 무엇을 하는 자리인지와
    // 파일 형식을 함께 푼다. 남의 제품 이름(엑셀·워드)은 안 쓴다 - 우리 기능을
    // 그 이름으로 부르면 그 회사 것처럼 읽힌다(2026-08-20 결정).
    const { container } = render([]);
    const sheet = container.querySelector<HTMLElement>('[data-action="create"]')!;
    const doc = container.querySelector<HTMLElement>('[data-action="doc"]')!;

    expect(sheet.title).toContain('xlsx');
    expect(doc.title).toContain('docx');
    expect(sheet.title).not.toContain('엑셀');
    expect(doc.title).not.toContain('워드');
  });

  it('타일 아이콘은 목록 아이콘과 같은 그림이다', () => {
    // 글자표(문/프)는 문서 목록의 아이콘과 달라서 같은 종류로 안 보인다.
    const { container } = render([]);
    const sheetMark = container.querySelector('[data-action="create"] .tile-mark svg');
    const docMark = container.querySelector('[data-action="doc"] .tile-mark svg');

    expect(sheetMark?.getAttribute('data-kind')).toBe('sheet');
    expect(docMark?.getAttribute('data-kind')).toBe('doc');
  });

  it('파일 보기 타일은 어떤 형식이든 읽기 전용으로 연다', () => {
    // 슬라이드만 보던 자리를 넓힌다. 문서함에 안 남기고 잠깐 보는 길이다
    // (2026-08-20 결정).
    const container = document.createElement('div');
    const onView = vi.fn();
    renderDocumentList({
      container, workbooks: { mine: [], shared: [] }, onOpen: vi.fn(),
      onUpload: vi.fn(), onUploadDoc: vi.fn(), onUploadSlide: vi.fn(), onCreate: vi.fn(),
      onDelete: vi.fn(), onView,
    });

    expect(container.querySelector('[data-action="upload-slide"]')).toBeNull();
    const tile = container.querySelector<HTMLElement>('[data-action="view"]')!;
    expect(tile.textContent).toContain('파일 보기');
    expect(tile.title).toContain('pptx');
    expect(tile.title).toContain('xlsx');

    tile.click();
    expect(onView).toHaveBeenCalled();
  });

  it('docx·hwp 파일도 시작 화면에서 올린다', () => {
    // 문서를 올리는 길이 편집 화면 메뉴 안에만 있었다. 아직 파일이 없는
    // 사람은 그 화면에 갈 일이 없다.
    const onUploadDoc = vi.fn();
    const container = document.createElement('div');
    renderDocumentList({
      container, workbooks: { mine: [], shared: [] }, onOpen: vi.fn(),
      onUpload: vi.fn(), onUploadDoc, onUploadSlide: vi.fn(), onCreate: vi.fn(),
      onDelete: vi.fn(),
    });
    const tile = container.querySelector<HTMLElement>('[data-action="upload-doc"]')!;

    expect(tile.title).toContain('docx');
    expect(tile.title).toContain('hwp');
    tile.click();
    expect(onUploadDoc).toHaveBeenCalled();
  });

  it('격자 카드도 종류 아이콘을 쓴다', () => {
    // 이름 첫 글자를 쓰면 같은 '제' 로 시작하는 시트와 글이 똑같아 보인다.
    const { container } = render([summary({ docType: 'DOC' })]);
    container.querySelector<HTMLElement>('[data-view="grid"]')!.click();

    expect(container.querySelector('.card-thumb svg')?.getAttribute('data-kind'))
      .toBe('doc');
  });

  it('수정한 때를 보여준다', () => {
    const { container } = render([summary({ modifiedAt: '2026-08-05T14:30:00' })]);

    expect(container.textContent).toContain('2026-08-05');
  });

  it('수정한 때를 모르면 그 자리를 비운다', () => {
    // 옛 문서라 기록이 없다. "Invalid Date" 를 띄우느니 아무것도 안 적는다.
    const { container } = render([summary({ modifiedAt: null })]);

    expect(container.textContent).not.toContain('Invalid');
  });

  it('내 문서는 지울 수 있다', () => {
    const { container, onDelete } = render([summary({ workbookSeq: 7, workbookName: '버릴 문서' })]);
    const button = container.querySelector<HTMLElement>('[data-delete="7"]')!;

    // 한 번에 지우지 않는다. 되돌릴 수 없는 일이라 확인을 받는다.
    button.click();
    expect(onDelete).not.toHaveBeenCalled();
    expect(button.textContent).toContain('정말');

    button.click();
    expect(onDelete).toHaveBeenCalledWith(7, '버릴 문서');
  });

  it('공유받은 문서에는 삭제가 없다', () => {
    // 지우기는 소유자만 한다. 서버도 막지만 누를 수 있는 버튼을 두면 고장으로 읽는다.
    const { container } = render([], [summary({ workbookSeq: 9, role: 'EDITOR' })]);

    expect(container.querySelector('[data-delete="9"]')).toBeNull();
  });

  it('파일이 없으면 안내를 보여준다', () => {
    // 빈 화면만 나오면 고장인지 파일이 없는 것인지 모른다.
    const { container } = render([]);
    expect(container.textContent).toContain('파일이 없습니다');
  });

  it('제목을 그대로 글자로 넣는다', () => {
    // 제목은 사용자가 정한다. innerHTML 로 넣으면 남이 만든 문서 제목이
    // 내 화면에서 스크립트가 된다.
    const { container } = render([summary({ workbookName: '<img src=x onerror=alert(1)>' })]);

    expect(container.querySelector('img')).toBeNull();
    expect(container.textContent).toContain('<img src=x onerror=alert(1)>');
  });

  it('다시 그리면 이전 목록이 남지 않는다', () => {
    // 목록을 새로 고칠 때마다 줄이 쌓이면 같은 문서가 여러 번 보인다.
    const container = document.createElement('div');
    const options = {
      container, onOpen: vi.fn(), onUpload: vi.fn(), onUploadDoc: vi.fn(), onUploadSlide: vi.fn(),
      onCreate: vi.fn(), onDelete: vi.fn(),
    };
    renderDocumentList({ ...options, workbooks: { mine: [summary()], shared: [] } });
    renderDocumentList({ ...options, workbooks: { mine: [summary()], shared: [] } });

    expect(container.querySelectorAll('[data-workbook-seq="1"]')).toHaveLength(1);
  });

  it('미리보기가 없는 카드는 제목 첫 글자를 크게 깔아 준다', () => {
    // 제목만 늘어놓으면 어느 것이 무엇인지 한눈에 안 들어온다. 빈 칸을 그대로
    // 두느니 제목 첫 글자로 대신한다(구글도 빈 미리보기를 이렇게 둔다).
    const { container } = render([summary({ workbookName: '2분기 실적', preview: [] })]);
    container.querySelector<HTMLElement>('[data-view="grid"]')!.click();

    expect(container.querySelector('.card-blank')?.textContent).toBe('2');
  });
});

describe('파일 보기 타일', () => {
  it('보기 길이 없으면 옛 슬라이드 길로 물러선다', () => {
    // onView 를 안 주는 화면이 아직 있을 수 있다. 그때는 pptx 고르기가 열린다.
    const container = document.createElement('div');
    const onUploadSlide = vi.fn();
    renderDocumentList({
      container, workbooks: { mine: [], shared: [] },
      onOpen: vi.fn(), onUpload: vi.fn(), onUploadDoc: vi.fn(), onUploadSlide,
      onCreate: vi.fn(), onDelete: vi.fn(),
    });

    container.querySelector<HTMLElement>('[data-action="view"]')!.click();

    expect(onUploadSlide).toHaveBeenCalled();
  });
});

describe('종류 거르개', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('처음에는 전부 보인다', () => {
    const { container } = render([
      summary({ workbookSeq: 1, workbookName: '매출집계표', docType: 'SHEET' }),
      summary({ workbookSeq: 2, workbookName: '보안요건서', docType: 'DOC' }),
    ]);

    expect(container.textContent).toContain('매출집계표');
    expect(container.textContent).toContain('보안요건서');
  });

  it('스프레드시트만 고르면 글 문서를 감춘다', () => {
    // 시트 목록에 글 문서가 섞여 있다(2026-08-13 지적).
    const { container } = render([
      summary({ workbookSeq: 1, workbookName: '매출집계표', docType: 'SHEET' }),
      summary({ workbookSeq: 2, workbookName: '보안요건서', docType: 'DOC' }),
    ]);

    container.querySelector<HTMLElement>('[data-kind="SHEET"]')!.click();

    expect(container.textContent).toContain('매출집계표');
    expect(container.textContent).not.toContain('보안요건서');
  });

  it('워드 문서만 고르면 시트를 감춘다', () => {
    const { container } = render([
      summary({ workbookSeq: 1, workbookName: '매출집계표', docType: 'SHEET' }),
      summary({ workbookSeq: 2, workbookName: '보안요건서', docType: 'DOC' }),
    ]);

    container.querySelector<HTMLElement>('[data-kind="DOC"]')!.click();

    expect(container.textContent).not.toContain('매출집계표');
    expect(container.textContent).toContain('보안요건서');
  });

  it('거르개마다 그 종류 그림을 붙인다', () => {
    // 글자만 있으면 무엇을 고르는지 한눈에 안 들어온다(2026-08-13 지적).
    const { container } = render([summary()]);

    expect(container.querySelector('[data-kind="SHEET"] svg')).not.toBeNull();
    expect(container.querySelector('[data-kind="DOC"] svg')).not.toBeNull();
    expect(container.querySelector('[data-kind="ALL"] svg')).not.toBeNull();
  });

  it('고른 거르개가 눌린 채로 보인다', () => {
    const { container } = render([summary()]);

    container.querySelector<HTMLElement>('[data-kind="DOC"]')!.click();

    expect(container.querySelector('[data-kind="DOC"]')!
      .getAttribute('aria-pressed')).toBe('true');
    expect(container.querySelector('[data-kind="ALL"]')!
      .getAttribute('aria-pressed')).toBe('false');
  });

  it('걸러서 아무것도 없으면 그렇게 알린다', () => {
    const { container } = render([summary({ workbookName: '매출집계표', docType: 'SHEET' })]);

    container.querySelector<HTMLElement>('[data-kind="DOC"]')!.click();

    expect(container.textContent).toContain('없습니다');
  });
});

describe('휴지통', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('지운 문서와 되살리기 단추를 보인다', () => {
    // 지우기는 예전부터 되돌릴 수 있었는데 화면에 길이 없었다(2026-08-20 지적).
    const { container, onRestore } = render([], [], [
      { workbookSeq: 7, workbookName: '버린 문서', docType: 'SHEET',
        deletedAt: '2026-08-19T10:00:00' },
    ]);

    expect(container.textContent).toContain('버린 문서');
    const button = container.querySelector<HTMLElement>('[data-restore="7"]')!;
    button.click();

    expect(onRestore).toHaveBeenCalledWith(7, '버린 문서');
  });

  it('휴지통이 비면 묶음 자체를 두지 않는다', () => {
    // 늘 "휴지통이 비었습니다" 가 붙어 있으면 시작 화면이 지저분하다.
    const { container } = render([summary()]);

    expect(container.textContent).not.toContain('휴지통');
  });
});
