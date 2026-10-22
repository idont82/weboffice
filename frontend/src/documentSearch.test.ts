import { beforeEach, describe, expect, it, vi } from 'vitest';

import { renderDocumentList } from './documentList';
import type { WorkbookHit, WorkbookSummary } from './api';

function summary(fields: Partial<WorkbookSummary> = {}): WorkbookSummary {
  return {
    workbookSeq: 1, workbookName: '문서', ownerName: null,
    role: 'OWNER', memberCount: 0, shared: false, preview: [], docType: 'SHEET',
    modifiedAt: '2026-08-05T14:30:00', starred: false, color: null, ...fields,
  };
}

function hit(fields: Partial<WorkbookHit> = {}): WorkbookHit {
  return { ...summary(), hit: 'NAME', snippet: null, ...fields };
}

function render(found: { mine: WorkbookHit[]; shared: WorkbookHit[] }) {
  const container = document.createElement('div');
  const onSearch = vi.fn(async () => found);
  const onOpen = vi.fn();
  renderDocumentList({
    container, workbooks: { mine: [summary({ workbookName: '평소 문서' })], shared: [] },
    onOpen, onUpload: vi.fn(), onUploadDoc: vi.fn(), onUploadSlide: vi.fn(),
    onCreate: vi.fn(), onDelete: vi.fn(), onSearch,
  });
  return { container, onSearch, onOpen };
}

/** 검색칸에 적고 기다린다. 화면은 잠깐 멈췄다 찾는다 - 글자마다 두드리지 않는다. */
async function 찾는다(container: HTMLElement, words: string) {
  const box = container.querySelector<HTMLInputElement>('#docSearch')!;
  box.value = words;
  box.dispatchEvent(new Event('input'));
  await vi.advanceTimersByTimeAsync(400);
}

describe('문서 찾기', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.useFakeTimers();
  });

  it('찾은 문서를 보여 준다', async () => {
    const { container, onSearch } = render({
      mine: [hit({ workbookSeq: 7, workbookName: '올해 예산안' })], shared: [],
    });

    await 찾는다(container, '예산');

    expect(onSearch).toHaveBeenCalledWith('예산');
    expect(container.textContent).toContain('올해 예산안');
    // 찾는 동안에는 평소 목록을 감춘다 - 둘이 같이 있으면 무엇이 결과인지 모른다.
    expect(container.textContent).not.toContain('평소 문서');
  });

  it('내용에서 맞으면 맞은 토막을 보여 준다', async () => {
    const { container } = render({
      mine: [hit({ workbookName: '회의록', hit: 'CELL', snippet: '…인프라보안팀 예산 협의…' })],
      shared: [],
    });

    await 찾는다(container, '예산');

    expect(container.textContent).toContain('인프라보안팀 예산 협의');
  });

  it('없으면 없다고 말한다', async () => {
    const { container } = render({ mine: [], shared: [] });

    await 찾는다(container, '없는말');

    expect(container.textContent).toContain('찾은 문서가 없습니다');
  });

  it('비우면 평소 목록으로 돌아온다', async () => {
    const { container, onSearch } = render({ mine: [], shared: [] });
    await 찾는다(container, '예산');

    await 찾는다(container, '');

    expect(container.textContent).toContain('평소 문서');
    // 빈 말로는 서버를 두드리지 않는다.
    expect(onSearch).toHaveBeenCalledTimes(1);
  });

  it('찾은 문서를 누르면 연다', async () => {
    const { container, onOpen } = render({
      mine: [hit({ workbookSeq: 9, workbookName: '올해 예산안' })], shared: [],
    });
    await 찾는다(container, '예산');

    container.querySelector<HTMLElement>('[data-workbook-seq="9"]')!.click();

    expect(onOpen).toHaveBeenCalledWith(9, '올해 예산안');
  });
});
