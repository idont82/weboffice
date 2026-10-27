/**
 * 서버 REST 래퍼. 오류 변환만 한다.
 *
 * 인증은 SSO 세션 쿠키로 이뤄지므로 토큰을 다루지 않는다(스펙 3절).
 * 쿠키가 자동으로 가도록 credentials: 'same-origin' 만 붙인다.
 * 화면 로직은 main.ts 가, 잠금 표시는 lockUi.ts 가 담당한다.
 */
import type { CellDto, MergeRangeDto } from './adapter';
import type { CurrentUser } from './currentUser';
import type { HistoryEntry, Version, VersionChange } from './historyPanel';
import type { DocParagraph } from './docBody';

/**
 * 서버가 시트를 열 때 주는 모양.
 *
 * <p>같은 서식이 셀마다 되풀이되면 시트 한 장이 몇 MB 가 된다. 서식은 사전에 한
 * 번만 싣고 셀은 번호만 든다(2026-08-20: 2.1MB -> 0.54MB).
 */
interface CellPage {
  styles: string[];
  cells: (Omit<CellDto, 'styleValue'> & { styleNo?: number })[];
}

/** 사전에서 서식을 꺼내 셀마다 다시 붙인다. 화면 쪽은 예전 모양 그대로 쓴다. */
function spreadStyles(page: CellPage): CellDto[] {
  return page.cells.map(({ styleNo, ...cell }) => (styleNo == null
    ? cell as CellDto
    : { ...cell, styleValue: page.styles[styleNo] } as CellDto));
}

/** 휴지통에 있는 문서 한 줄. 서버가 30일 안쪽만 준다. */
export interface TrashItem {
  workbookSeq: number;
  workbookName: string;
  docType: string | null;
  deletedAt: string | null;
}

export interface Range {
  fromRowNo: number;
  fromColumnNo: number;
  toRowNo: number;
  toColumnNo: number;
}

/**
 * 누가 어디를 보고 있는지. <b>사번이 없다.</b>
 *
 * 2026-08-06 에 잠금에서 표시로 바뀌었다. 겹쳐도 서버가 막지 않는다.
 */
export interface PresenceRow extends Range {
  lockSeq: number;
  sheetSeq: number;
  userId: string | null;
  displayName: string | null;
  rank: string | null;
  sessionKey: string | null;
}

/**
 * 서버의 DrawingDto 와 필드명이 정확히 같아야 한다. 어긋나면 컴파일은 통과하고
 * 런타임에 값이 조용히 빠진다.
 *
 * 오프셋 단위는 픽셀이다. Univer 의 ICellOverGridPosition 과 같은 단위라
 * 변환 없이 오간다.
 *
 * drawingExtra 는 JSON 문자열이다. 서버가 해석하지 않고 그대로 왕복시킨다 -
 * 자르기·회전·axisAlignSheetTransform 이 여기 들어간다. 쓰는 쪽이 JSON.parse 한다.
 */
export interface DrawingDto {
  drawingId: string;
  imageSeq: number;
  fromRowNo: number;
  fromRowOffset: number;
  fromColumnNo: number;
  fromColumnOffset: number;
  toRowNo: number;
  toRowOffset: number;
  toColumnNo: number;
  toColumnOffset: number;
  anchorType: string;
  zOrder: number;
  drawingExtra: string | null;
}

/**
 * 손댄 행 높이·열 너비 한 줄.
 *
 * 기본 크기인 줄은 담지 않는다. 시트 하나에 행이 백만 개일 수 있어 전부 적으면
 * 표가 터진다 - 지울 때는 sizePx 가 없다.
 */
export interface SheetSize {
  axisType: 'ROW' | 'COLUMN';
  indexNo: number;
  sizePx?: number;
}

export interface UploadedImage {
  imageSeq: number;
  url: string;
  mimeType: string;
}

export interface SheetSummary {
  sheetSeq: number;
  sheetName: string;
  sheetNo: number;
  maxRowNo: number;
  maxColumnNo: number;
  /** 격자선(눈금선)을 보일지. 서버가 시트마다 들고 있다. */
  showGridlines: boolean;
  /** 틀 고정. 위에서 몇 행을 붙박아 둘지. 0 이면 없다. */
  freezeRowNo: number;
  /** 틀 고정. 왼쪽에서 몇 열을 붙박아 둘지. 0 이면 없다. */
  freezeColumnNo: number;
  /** 시트 탭 색(#rrggbb). 없으면 색을 안 칠한 것이다. */
  tabColor?: string | null;
}

/**
 * 문서 리소스 한 덩어리.
 *
 * <p>Univer 의 기능 가운데 셀 값이 아닌 것(필터 등)이 통째로 담기는 JSON 문자열이다.
 * 안을 들여다보지 않는다 - 모양을 우리가 정하지 않았다.
 */
import type { SlideDeckView } from './slideView';
import { CLIENT_HEADER, clientId } from './clientId';
import type { ColorLabel } from './fileMarks';
import type { RestorePoint } from './restorePoints';

/** 휴지통 비우기 진행. 되돌릴 수 없는 일이 얼마나 갔는지 화면에 보인다. */
export interface TrashPurgeProgress {
  running: boolean;
  done: number;
  total: number;
  message: string | null;
}

/** 내가 보던 자리. 시트마다의 배율은 시트 번호를 열쇠로 담는다. */
export interface SheetViewMemory {
  lastSheetSeq: number | null;
  zooms: Record<string, number>;
}

export interface WorkbookResource {
  name: string;
  data: string;
}

/** 문서 목록 한 줄. 서버가 사번을 빼고 내려준다 - 이름만 들어 있다. */
export interface WorkbookSummary {
  workbookSeq: number;
  workbookName: string;
  ownerName: string | null;
  role: string;
  memberCount: number;
  shared: boolean;
  /** 마지막으로 손댄 때. 옛 문서는 기록이 없어 null 일 수 있다. */
  modifiedAt: string | null;
  /** 카드에 보일 첫 시트 앞부분. 줄마다 칸 수가 같다. 빈 문서는 빈 배열이다. */
  preview: string[][];
  /** 시트냐 글이냐. 화면이 목록 아이콘과 열 화면을 이것으로 가른다. */
  /** 발표 자료(SLIDE)는 보기 전용이다. 고쳐 쓰는 화면이 없다. */
  docType: 'SHEET' | 'DOC' | 'SLIDE';
  /** 내가 즐겨찾기로 달아 두었는지. 남의 별표는 오지 않는다. */
  starred: boolean;
  /** 내가 칠한 색. 안 칠했으면 null. fileMarks 의 COLORS 중 하나다. */
  color: string | null;
}

/** 찾기 결과 한 줄. 목록 한 줄에 어디서 맞았는지를 더한 것이다. */
export interface WorkbookHit extends WorkbookSummary {
  /** NAME(이름) · CELL(시트 칸) · PARA(글 문단) */
  hit: 'NAME' | 'CELL' | 'PARA';
  /** 맞은 자리 앞뒤 토막. 이름에서 맞았으면 null */
  snippet: string | null;
}

export interface WorkbookMember {
  userId: string | null;
  displayName: string | null;
  rank: string | null;
  deptName: string | null;
  role: string;
}

export interface UserSummary {
  userId: string;
  displayName: string | null;
  rank: string | null;
  deptName: string | null;
}

export function createApi() {
  /**
   * 모든 요청에 세션 쿠키를 싣는다.
   *
   * 조회에만 붙이고 쓰기에 빠뜨리는 실수가 흔한데, 그러면 화면은 열리고
   * 저장만 401 이 난다. 요청을 만드는 곳을 여기 하나로 모아 막는다.
   */
  const send = (extra: RequestInit = {}): RequestInit => ({
    credentials: 'same-origin',
    ...extra,
    // 어느 창이 보냈는지 알린다. 서버가 실시간 알림에 그대로 붙여 주어,
    // 자기 창은 자기 변경을 건너뛴다(clientId.ts).
    headers: { ...(extra.headers as Record<string, string> | undefined),
      [CLIENT_HEADER]: clientId() },
  });

  const json = (body: unknown): RequestInit => send({
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });

  async function ensureOk(response: Response): Promise<Response> {
    if (response.ok) return response;
    if (response.status === 401) {
      throw new Error('인증이 필요합니다. 로그인 화면으로 이동합니다.');
    }
    if (response.status === 403) {
      throw new Error('이 문서에 대한 권한이 없습니다.');
    }
    const body = await response.json().catch(() => ({ message: '요청이 실패했습니다.' }));
    throw new Error(body.message ?? '요청이 실패했습니다.');
  }

  return {
    async importWorkbook(file: File) {
      const form = new FormData();
      form.append('file', file);
      const response = await fetch('/api/workbooks/import',
        send({ method: 'POST', body: form }));
      return (await ensureOk(response)).json() as Promise<{
        workbookSeq: number; sheets: SheetSummary[];
      }>;
    },

    exportUrl(workbookSeq: number) {
      return `/api/workbooks/${workbookSeq}/export`;
    },

    async readCells(sheetSeq: number, range: Range): Promise<CellDto[]> {
      const query = new URLSearchParams({
        fromRowNo: String(range.fromRowNo),
        fromColumnNo: String(range.fromColumnNo),
        toRowNo: String(range.toRowNo),
        toColumnNo: String(range.toColumnNo),
      });
      const response = await fetch(`/api/sheets/${sheetSeq}/cells?${query}`, send());
      const page = await (await ensureOk(response)).json() as CellPage;
      return spreadStyles(page);
    },

    async saveDelta(sheetSeq: number, changed: CellDto[], deleted: CellDto[]): Promise<number> {
      const response = await fetch(`/api/sheets/${sheetSeq}/cells`,
        { method: 'PATCH', ...json({ changed, deleted }) });
      const body = await (await ensureOk(response)).json();
      return body.savedCount;
    },

    /** 내 자리를 알린다. 겹쳐도 거절당하지 않는다 - 잠금이 아니라 표시다. */
    async acquireLock(sheetSeq: number, range: Range,
                      sessionKey: string): Promise<PresenceRow> {
      const response = await fetch('/api/locks',
        { method: 'POST', ...json({ sheetSeq, ...range, sessionKey }) });
      return (await ensureOk(response)).json();
    },

    /** 문서를 연 직후, 이미 와 있는 사람들. */
    async listPresence(sheetSeq: number): Promise<PresenceRow[]> {
      const query = new URLSearchParams({ sheetSeq: String(sheetSeq) });
      return (await ensureOk(await fetch(`/api/locks?${query}`, send()))).json();
    },

    async releaseLock(lockSeq: number): Promise<void> {
      await ensureOk(await fetch(`/api/locks/${lockSeq}`, send({ method: 'DELETE' })));
    },

    /**
     * 페이지를 떠나며 자리를 반납한다.
     *
     * <p>fetch 는 페이지가 사라지면 중간에 죽는다. beacon 은 브라우저가 대신 끝까지
     * 보낸다 - 대신 POST 만 되고 응답을 볼 수 없다. 그래서 서버에 POST 경로를 따로 뒀다.
     *
     * @returns 보냈으면 true. beacon 이 없거나 거절하면 부르는 쪽이 fetch 로 물러선다.
     */
    releaseLockOnUnload(lockSeq: number): boolean {
      return navigator.sendBeacon?.(`/api/locks/${lockSeq}/release`) ?? false;
    },

    async heartbeat(lockSeq: number): Promise<void> {
      await ensureOk(await fetch(`/api/locks/${lockSeq}/heartbeat`, send({ method: 'POST' })));
    },

    /**
     * 지금 로그인한 사람. 인증이 없으면 401 이 나므로 화면은 이미 로그인으로
     * 리다이렉트된 뒤다 - 여기까지 왔다면 세션이 있다는 뜻이다.
     */
    async me(): Promise<CurrentUser> {
      const response = await fetch('/api/me', send());
      return (await ensureOk(response)).json();
    },

    async uploadImage(workbookSeq: number, file: File): Promise<UploadedImage> {
      const form = new FormData();
      form.append('file', file);
      // Content-Type 을 손대지 않는다. 직접 넣으면 boundary 가 빠져 서버가
      // multipart 를 파싱하지 못한다. importWorkbook 도 같은 이유로 비워 둔다.
      const response = await fetch(`/api/workbooks/${workbookSeq}/images`,
        send({ method: 'POST', body: form }));
      return (await ensureOk(response)).json();
    },

    async readSizes(sheetSeq: number): Promise<SheetSize[]> {
      return (await ensureOk(await fetch(`/api/sheets/${sheetSeq}/sizes`, send()))).json();
    },

    async saveSizes(sheetSeq: number, changed: SheetSize[],
                    deleted: SheetSize[]): Promise<number> {
      const response = await fetch(`/api/sheets/${sheetSeq}/sizes`,
        { method: 'PUT', ...json({ changed, deleted }) });
      const body = await (await ensureOk(response)).json();
      return body.savedCount;
    },

    async addSheet(workbookSeq: number, sheetName?: string): Promise<SheetSummary> {
      const response = await fetch(`/api/workbooks/${workbookSeq}/sheets`,
        { method: 'POST', ...json({ sheetName }) });
      return (await ensureOk(response)).json();
    },

    /**
     * 시트 복사.
     *
     * <p>서버가 값·서식·병합·크기·그림·틀고정을 함께 옮긴다. 이름을 안 주면
     * "원래이름 (사본)" 이 된다.
     */
    async copySheet(sheetSeq: number, sheetName?: string): Promise<SheetSummary> {
      const response = await fetch(`/api/sheets/${sheetSeq}/copy`,
        { method: 'POST', ...json({ sheetName }) });
      return (await ensureOk(response)).json();
    },

    async reorderSheets(workbookSeq: number, sheetSeqs: number[]): Promise<void> {
      await ensureOk(await fetch(`/api/workbooks/${workbookSeq}/sheets/order`,
        { method: 'PUT', ...json({ sheetSeqs }) }));
    },

    async renameSheet(sheetSeq: number, sheetName: string): Promise<void> {
      await ensureOk(await fetch(`/api/sheets/${sheetSeq}`,
        { method: 'PATCH', ...json({ sheetName }) }));
    },

    async setGridlines(sheetSeq: number, showGridlines: boolean): Promise<void> {
      await ensureOk(await fetch(`/api/sheets/${sheetSeq}`,
        { method: 'PATCH', ...json({ showGridlines }) }));
    },

    /** 시트 탭 색. 빈 문자열이면 색을 지운다 - null 은 "안 보냈다" 는 뜻이다. */
    async setTabColor(sheetSeq: number, tabColor: string): Promise<void> {
      await ensureOk(await fetch(`/api/sheets/${sheetSeq}`,
        { method: 'PATCH', ...json({ tabColor }) }));
    },

    async importDoc(file: File): Promise<{ workbookSeq: number; workbookName: string }> {
      const form = new FormData();
      form.append('file', file);
      return (await ensureOk(await fetch('/api/docs/import',
        { method: 'POST', body: form, credentials: 'same-origin' }))).json();
    },

    docExportUrl(workbookSeq: number) {
      return `/api/docs/${workbookSeq}/export`;
    },

    /** 한글 문서로 내려받는 주소. 받는 쪽이 한글이면 docx 보다 덜 어긋난다. */
    docExportHwpxUrl(workbookSeq: number) {
      return `/api/docs/${workbookSeq}/export/hwpx`;
    },

    async readParagraphs(workbookSeq: number): Promise<DocParagraph[]> {
      return (await ensureOk(await fetch(`/api/docs/${workbookSeq}/paragraphs`, send()))).json();
    },

    async saveParagraphs(workbookSeq: number, changed: DocParagraph[],
                         deleted: number[]): Promise<void> {
      await ensureOk(await fetch(`/api/docs/${workbookSeq}/paragraphs`,
        { method: 'PATCH', ...json({ changed, deleted }) }));
    },

    async readResources(workbookSeq: number): Promise<WorkbookResource[]> {
      return (await ensureOk(await fetch(`/api/workbooks/${workbookSeq}/resources`, send()))).json();
    },

    /** 발표 자료(pptx)를 올린다. 보기 전용이라 고쳐 쓰는 길은 없다. */
    async importSlides(file: File): Promise<{ workbookSeq: number; workbookName: string;
      slideCount: number; }> {
      const form = new FormData();
      form.append('file', file);
      return (await ensureOk(await fetch('/api/slides/import',
        { method: 'POST', body: form, credentials: 'same-origin' }))).json();
    },

    async readSlides(workbookSeq: number): Promise<SlideDeckView> {
      return (await ensureOk(await fetch(`/api/slides/${workbookSeq}`, send()))).json();
    },

    /**
     * 이 문서에서 내가 보던 자리. 계정을 따라간다.
     *
     * <p>개인 것이라 남의 것은 오지 않는다. 서버가 세션의 사람으로 가른다.
     */
    async readView(workbookSeq: number): Promise<SheetViewMemory> {
      return (await ensureOk(await fetch(`/api/workbooks/${workbookSeq}/view`, send()))).json();
    },

    /** 배율을 안 보내면 앞서 담은 배율을 그대로 둔다. */
    async saveView(workbookSeq: number, sheetSeq: number, zoom?: number): Promise<void> {
      await ensureOk(await fetch(`/api/workbooks/${workbookSeq}/view`,
        { method: 'PUT', ...json({ sheetSeq, zoom: zoom ?? null }) }));
    },

    async saveResource(workbookSeq: number, name: string, data: string): Promise<void> {
      await ensureOk(await fetch(`/api/workbooks/${workbookSeq}/resources/${name}`,
        { method: 'PUT', ...json({ data }) }));
    },

    /**
     * 걸러 보기(필터)를 건 자리를 담는다. 빈 값이면 푼다.
     *
     * <p>화면에서만 걸러 보면 내려받은 파일에 안 남는다(2026-08-26).
     */
    async setFilterRange(sheetSeq: number, filterRange: string): Promise<void> {
      await ensureOk(await fetch(`/api/sheets/${sheetSeq}`,
        { method: 'PATCH', ...json({ filterRange }) }));
    },

    async setFreeze(sheetSeq: number, freezeRowNo: number,
                    freezeColumnNo: number): Promise<void> {
      await ensureOk(await fetch(`/api/sheets/${sheetSeq}`,
        { method: 'PATCH', ...json({ freezeRowNo, freezeColumnNo }) }));
    },

    async removeSheet(sheetSeq: number): Promise<void> {
      await ensureOk(await fetch(`/api/sheets/${sheetSeq}`, send({ method: 'DELETE' })));
    },

    async readMerges(sheetSeq: number): Promise<MergeRangeDto[]> {
      return (await ensureOk(await fetch(`/api/sheets/${sheetSeq}/merges`, send()))).json();
    },

    /**
     * 병합은 델타가 아니라 통째로 보낸다. 하나를 풀면 그 자리에 다른 병합이 생기는
     * 식이라 "바뀐 것" 을 가려내는 쪽이 오히려 어긋나기 쉽다.
     */
    async saveMerges(sheetSeq: number, merges: MergeRangeDto[]): Promise<number> {
      const response = await fetch(`/api/sheets/${sheetSeq}/merges`,
        { method: 'PUT', ...json({ merges }) });
      const body = await (await ensureOk(response)).json();
      return body.savedCount;
    },

    async readDrawings(sheetSeq: number): Promise<DrawingDto[]> {
      const response = await fetch(`/api/sheets/${sheetSeq}/drawings`, send());
      return (await ensureOk(response)).json();
    },

    async saveDrawings(sheetSeq: number, upserted: DrawingDto[],
                       deletedIds: string[]): Promise<number> {
      const response = await fetch(`/api/sheets/${sheetSeq}/drawings`,
        { method: 'PUT', ...json({ upserted, deletedIds }) });
      const body = await (await ensureOk(response)).json();
      return body.savedCount;
    },

    async listWorkbooks(): Promise<{ mine: WorkbookSummary[]; shared: WorkbookSummary[] }> {
      return (await ensureOk(await fetch('/api/workbooks', send()))).json();
    },

    /**
     * 별표와 색을 함께 보낸다.
     *
     * <p>둘이 한 행이라 따로 보내면 다른 쪽이 지워진다 - 화면이 지금 상태를
     * 알고 있으니 통째로 보낸다.
     */
    async markWorkbook(workbookSeq: number, starred: boolean,
      color: string | null): Promise<void> {
      await ensureOk(await fetch(`/api/workbooks/${workbookSeq}/mark`,
        { method: 'PUT', ...json({ starred, color }) }));
    },

    /** 색 일곱 개와 그 이름. 이름을 안 지은 색은 기본 이름으로 온다. */
    async listColorLabels(): Promise<ColorLabel[]> {
      return (await ensureOk(await fetch('/api/workbook-colors', send()))).json();
    },

    /** 색에 이름을 붙인다. 비우면 기본 이름으로 돌아간다. */
    async renameColorLabel(color: string, name: string): Promise<void> {
      await ensureOk(await fetch(`/api/workbook-colors/${encodeURIComponent(color)}`,
        { method: 'PUT', ...json({ name }) }));
    },

    /** 이름이나 내용으로 찾는다. 찾는 말이 비면 서버를 두드리지 않는다. */
    async searchWorkbooks(words: string):
    Promise<{ mine: WorkbookHit[]; shared: WorkbookHit[] }> {
      const found = await ensureOk(await fetch(
        `/api/workbooks/search?q=${encodeURIComponent(words)}`, send()));
      return found.json();
    },

    /** 빈 문서. 시트가 하나 딸려 오므로 곧바로 열 수 있다. */
    async createWorkbook(workbookName?: string, docType: 'SHEET' | 'DOC' = 'SHEET'): Promise<{
      workbookSeq: number; workbookName: string; sheets: SheetSummary[];
      docType: 'SHEET' | 'DOC';
    }> {
      return (await ensureOk(await fetch('/api/workbooks',
        { method: 'POST', ...json({ workbookName: workbookName ?? null, docType }) }))).json();
    },

    /** 문서 한 건. 공개 링크로 들어오면 목록에 없으므로 목록으로는 제목을 못 얻는다. */
    async getWorkbook(workbookSeq: number): Promise<WorkbookSummary> {
      return (await ensureOk(await fetch(`/api/workbooks/${workbookSeq}`, send()))).json();
    },

    /** 칸 하나의 수정 기록. 최근 것이 앞이다. */
    async cellHistory(sheetSeq: number, rowNo: number, columnNo: number)
      : Promise<HistoryEntry[]> {
      return (await ensureOk(await fetch(
        `/api/sheets/${sheetSeq}/cells/${rowNo}/${columnNo}/history`, send()))).json();
    },

    /** 문서 버전 기록. 최근 것이 앞이다. */
    async listVersions(workbookSeq: number): Promise<Version[]> {
      return (await ensureOk(await fetch(
        `/api/workbooks/${workbookSeq}/versions`, send()))).json();
    },

    /** 그 버전에서 무엇이 바뀌었는지. 목록을 펼칠 때 부른다. */
    async versionChanges(workbookSeq: number, versionId: number): Promise<VersionChange[]> {
      return (await ensureOk(await fetch(
        `/api/workbooks/${workbookSeq}/versions/${versionId}/changes`, send()))).json();
    },

    /** 그 버전의 값으로 되돌린다. 되돌린 것도 기록에 남는다. */
    async restoreVersion(workbookSeq: number, versionId: number): Promise<number> {
      const body = await (await ensureOk(await fetch(
        `/api/workbooks/${workbookSeq}/versions/${versionId}/restore`,
        send({ method: 'POST' })))).json();
      return body.restoredCount;
    },

    /**
     * 휴지통 비우기를 시작한다. <b>되돌릴 수 없다.</b>
     *
     * <p>곧바로 돌아온다 - 다 지울 때까지 기다리지 않는다. 진행은
     * {@code trashPurgeProgress} 로 물어본다.
     *
     * @param keepDays 최근 이 날 수 안에 버린 것은 남긴다. 0 이면 전부
     * @return 지우기로 한 문서 수
     */
    async emptyTrash(keepDays: number): Promise<number> {
      const body = await (await ensureOk(await fetch(
        `/api/workbooks/trash/purge?keepDays=${keepDays}`, send({ method: 'DELETE' })))).json();
      return body.startedCount;
    },

    /** 비우기가 어디까지 갔는가. 시작한 뒤 이것을 물어보며 기다린다. */
    async trashPurgeProgress(): Promise<TrashPurgeProgress> {
      return (await ensureOk(await fetch('/api/workbooks/trash/purge', send()))).json();
    },

    /** 복구 지점 목록. 최근 것이 앞이다. */
    async listRestorePoints(workbookSeq: number): Promise<RestorePoint[]> {
      return (await ensureOk(await fetch(
        `/api/workbooks/${workbookSeq}/snapshots`, send()))).json();
    },

    /**
     * 지금 모습을 복구 지점으로 남긴다.
     *
     * @param reason 서버가 쓰는 이유(BEFORE_RESTORE 등)는 보내도 무시된다
     */
    async takeRestorePoint(workbookSeq: number, label = '',
      reason = 'MANUAL'): Promise<number> {
      const body = await (await ensureOk(await fetch(
        `/api/workbooks/${workbookSeq}/snapshots`,
        { method: 'POST', ...json({ label, reason }) }))).json();
      return body.snapshotSeq;
    },

    /** 그 시점으로 문서를 통째로 되돌린다. 되돌리기 직전 모습은 서버가 따로 남긴다. */
    async restoreToPoint(snapshotSeq: number): Promise<void> {
      await ensureOk(await fetch(`/api/snapshots/${snapshotSeq}/restore`,
        send({ method: 'POST' })));
    },

    /** 그 시점 내용으로 새 문서를 만든다. 원본은 안 건드린다. */
    async copyRestorePoint(snapshotSeq: number): Promise<number> {
      const body = await (await ensureOk(await fetch(
        `/api/snapshots/${snapshotSeq}/copy`, send({ method: 'POST' })))).json();
      return body.workbookSeq;
    },

    /**
     * 파일을 읽기 전용으로 볼 주소를 받는다.
     *
     * <p>파일함에 담지 않는다. 뷰어가 한 시간 동안만 들고 있다가 버린다.
     */
    async viewMine(file: File): Promise<string> {
      const form = new FormData();
      form.append('file', file);
      form.append('fileName', file.name);
      const response = await fetch('/api/view/mine',
        send({ method: 'POST', body: form }));
      const body = await (await ensureOk(response)).json();
      return body.viewUrl as string;
    },

    /** 휴지통. 지운 지 30일 안쪽인 내 문서만 온다. */
    async listTrash(): Promise<TrashItem[]> {
      return (await ensureOk(await fetch('/api/workbooks/trash', send()))).json();
    },

    /** 휴지통에서 되살리기. */
    async restoreWorkbook(workbookSeq: number): Promise<void> {
      await ensureOk(await fetch(`/api/workbooks/${workbookSeq}/restore`,
        send({ method: 'POST' })));
    },

    async deleteWorkbook(workbookSeq: number): Promise<void> {
      await ensureOk(await fetch(`/api/workbooks/${workbookSeq}`,
        send({ method: 'DELETE' })));
    },

    async renameWorkbook(workbookSeq: number, workbookName: string): Promise<void> {
      await ensureOk(await fetch(`/api/workbooks/${workbookSeq}`,
        { method: 'PATCH', ...json({ workbookName }) }));
    },

    async searchUsers(keyword: string): Promise<UserSummary[]> {
      const query = new URLSearchParams({ q: keyword });
      return (await ensureOk(await fetch(`/api/users?${query}`, send()))).json();
    },

    async listMembers(workbookSeq: number): Promise<WorkbookMember[]> {
      return (await ensureOk(
        await fetch(`/api/workbooks/${workbookSeq}/members`, send()))).json();
    },

    async putMember(workbookSeq: number, userId: string, role: string): Promise<void> {
      await ensureOk(await fetch(`/api/workbooks/${workbookSeq}/members`,
        { method: 'PUT', ...json({ userId, role }) }));
    },

    async removeMember(workbookSeq: number, userId: string): Promise<void> {
      // USER_ID 에 . 이나 / 가 들어가면 경로가 깨진다.
      await ensureOk(await fetch(
        `/api/workbooks/${workbookSeq}/members/${encodeURIComponent(userId)}`,
        send({ method: 'DELETE' })));
    },

    async issueShareLink(workbookSeq: number): Promise<string> {
      const body = await (await ensureOk(await fetch(
        `/api/workbooks/${workbookSeq}/share-link`, send({ method: 'POST' })))).json();
      return body.url;
    },

    async revokeShareLink(workbookSeq: number): Promise<void> {
      await ensureOk(await fetch(`/api/workbooks/${workbookSeq}/share-link`,
        send({ method: 'DELETE' })));
    },
  };
}

export type Api = ReturnType<typeof createApi>;
export type { CellDto, CurrentUser, MergeRangeDto };
