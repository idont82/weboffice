/**
 * 보기 전용 화면 세우기.
 *
 * <p>{@link ./main} 이 문서를 세우는 길에서 읽는 데 필요한 것만 추렸다. 저장·잠금·
 * 실시간·메뉴·툴바를 붙이지 않는다 - 보기 화면에는 고칠 길이 없고, 고칠 길이 없는
 * 화면에 그 왕복은 값어치가 없다(설계 6절).
 *
 * <p>Univer 를 세우는 코드라 자동 시험을 붙이기 어렵다. 값을 옮기는 부분은
 * {@link ./viewerSnapshot} 으로 갈라 그쪽만 시험한다.
 */
import { DocumentFlavor, IPermissionService, LocaleType, merge } from '@univerjs/core';
import { showUnit } from './showUnit';
import { withFirstPageEdge } from './firstPageEdge';
import type { Univer } from '@univerjs/core';
import type { FUniver } from '@univerjs/core/lib/facade';
import { greenTheme } from '@univerjs/themes';
import { IDrawingManagerService, UniverDrawingPlugin } from '@univerjs/drawing';
import { UniverDrawingUIPlugin } from '@univerjs/drawing-ui';
import { UniverDocsDrawingPlugin } from '@univerjs/docs-drawing';
import { UniverDocsDrawingUIPlugin } from '@univerjs/docs-drawing-ui';
import { WorkbookEditablePermission } from '@univerjs/sheets';
import { HoverManagerService } from '@univerjs/sheets-ui';
import { UniverSheetsCorePreset } from '@univerjs/preset-sheets-core';
import sheetsCoreKoKR from '@univerjs/preset-sheets-core/lib/locales/ko-KR';
import '@univerjs/preset-sheets-core/lib/index.css';
import { UniverSheetsDrawingPreset } from '@univerjs/preset-sheets-drawing';
import sheetsDrawingKoKR from '@univerjs/preset-sheets-drawing/lib/locales/ko-KR';
import '@univerjs/preset-sheets-drawing/lib/index.css';
import { UniverDocsCorePreset } from '@univerjs/preset-docs-core';
import docsCoreKoKR from '@univerjs/preset-docs-core/locales/ko-KR';
import '@univerjs/preset-docs-core/lib/index.css';

import { toUniverCellData, type CellDto, type MergeRangeDto } from './adapter';
import { createUniverFromPresets } from './bootstrap';
import type { DocParagraph } from './docBody';
import { edgeBody, type DocEdge } from './docEdge';
import { docLists } from './docLists';
import { fillTabLeaders, type Measure } from './tabLeader';
import { fontLineRatio, withLineHeights } from './docLineHeight';
import { shrinkBeforePageBreak } from './docTrimBreak';
import { IRenderManagerService } from '@univerjs/engine-render';
import { pageAt } from './viewerPages';
import { fitFloating, toBodyWithTables, type DocTable } from './docTable';
import { PAGE } from './ruler';
import { renderSlides } from './slideView';
import type { SheetSize } from './api';
import { toRowColumnData } from './sheetRows';
import {
  isEditCommand, toDeckView, toDocImages, toSheetDrawingResource, toSheetGrid,
  type DocPictureDto, type SheetPictureDto, type SlideDeckDto, type ViewerMeta,
} from './viewerSnapshot';

/** 보기 문서는 한 번에 하나다. 문서함에 들어가지 않아 순번이 없다 - 고정 id 를 쓴다. */
const UNIT_ID = 'wo-view';

/** 편집 화면과 같은 값이다. 보기 화면만 다르면 같은 문서가 달라 보인다. */
const DEFAULT_COLUMN_WIDTH = 64;
const DEFAULT_ROW_HEIGHT = 20;

/** 머릿글·꼬릿글이 종이 가장자리에서 떨어지는 거리(px). 워드 기본은 0.5인치다. */
const EDGE_MARGIN = 48;

/** 슬라이드 좌우에 남기는 여백(px). 편집 화면과 같다. */
const SLIDE_GUTTER = 48;

// ---------------------------------------------------------------- 받는 모양

/** 서버 {@code WorkbookDataDto}. */
interface WorkbookDto {
  workbookName: string;
  sheets: {
    sheetName: string;
    cells: CellDto[] | null;
    mergeRanges: MergeRangeDto[] | null;
    pictures: SheetPictureDto[] | null;
    defaultRowHeightPx: number;
    sizes: SheetSize[] | null;
  }[] | null;
}

/** 서버 {@code DocContent}. */
interface DocContentDto {
  paragraphs: DocParagraph[] | null;
  tables: DocTable[] | null;
  pictures: DocPictureDto[] | null;
  page: {
    width: number; height: number;
    marginLeft: number; marginRight: number; marginTop: number; marginBottom: number;
  } | null;
  header: DocEdge | null;
  footer: DocEdge | null;
  titlePage: boolean;
  firstHeader: DocEdge | null;
  firstFooter: DocEdge | null;
  /** 문서 기본 서식. 글꼴을 안 적은 글은 이 글꼴로 그린다. */
  defaults: { font: string | null; fontSize: number } | null;
}

// ---------------------------------------------------------------- 들머리

/**
 * @return 세운 Univer 손잡이. 발표 자료는 Univer 를 안 쓰므로 null 이다 -
 *     AI 도우미가 값을 읽으려면 이 손잡이가 있어야 한다.
 */
export function showSnapshot(appEl: HTMLElement, meta: ViewerMeta, data: unknown):
FUniver | null {
  if (meta.docType === 'SLIDE') {
    // 발표 자료는 Univer 를 안 쓴다. 고쳐 쓸 수 있는 슬라이드 편집기가 없어
    // 편집 화면도 제 손으로 그린다(2026-08-13 결정).
    showSlides(appEl, data as SlideDeckDto);
    return null;
  }

  const { univer, univerAPI } = startUniver(appEl, meta.docType);
  if (meta.docType === 'DOC') {
    showDoc(univer, univerAPI, data as DocContentDto, appEl);
  } else {
    showSheet(univer, univerAPI, meta.fileName, data as WorkbookDto);
  }
  return univerAPI;
}

// ---------------------------------------------------------------- Univer

function startUniver(appEl: HTMLElement, docType: 'SHEET' | 'DOC'):
{ univer: Univer; univerAPI: FUniver } {
  // 리본도 오른쪽 단추도 달지 않는다. 눌러도 되는 것이 없는 화면에 단추만 흐리게
  // 늘어놓으면 무엇을 보는 화면인지 흐려진다. header 를 끄면 툴바와 수식 입력줄이
  // 함께 사라진다 - 둘이 그 안에 있다.
  //
  // 아래쪽(footer)은 남긴다. 시트 탭이 거기 있어 끄면 두 번째 시트를 볼 길이 없다.
  const off = { header: false, contextMenu: false };

  const created = createUniverFromPresets(
    {
      locale: LocaleType.KO_KR,
      locales: {
        [LocaleType.KO_KR]: merge({}, sheetsCoreKoKR, docsCoreKoKR, sheetsDrawingKoKR),
      },
      // 편집 화면과 같은 초록 계열로 맞춘다.
      theme: greenTheme,
    },
    docType === 'DOC' ? [
      // 글 문서에는 글 편집기만 올린다. 시트 플러그인을 함께 올리면 그쪽 편집
      // 가드가 끼어든다(편집 화면에서 겪은 일이다).
      UniverDocsCorePreset({ container: appEl, ...off }),
      // 그림을 그리는 데 넷이 필요하다: 살림꾼, 그림 화면, 글 문서 살림꾼,
      // 글 문서 그림 화면. 마지막 것이 빠지면 얼개는 서는데 아무것도 안 그려진다.
      { plugins: [UniverDrawingPlugin, UniverDrawingUIPlugin,
        UniverDocsDrawingPlugin, UniverDocsDrawingUIPlugin] },
    ] : [
      UniverSheetsCorePreset({ container: appEl, ...off }),
      // 시트 안의 셀 편집기가 글 엔진을 쓴다. 시트에도 글 프리셋이 필요하다.
      UniverDocsCorePreset({ container: appEl }),
      // 그림. 알맹이가 base64 로 문서에 실려 오므로 올리는 길은 필요 없다.
      UniverSheetsDrawingPreset(),
    ] as { plugins: unknown[] }[],
  );

  if (docType === 'DOC') {
    // 시트 facade 확장이 글 문서에도 붙는다(프리셋을 불러오는 것만으로 붙는다).
    // 그 확장이 HoverManagerService 를 찾는데 글 문서에는 없어 예외가 나고,
    // 그 뒤에 걸려야 할 구독들이 걸리지 않는다(편집 화면 2026-08-11 실측).
    created.univer.__getInjector().add([HoverManagerService]);
  }
  return created;
}

// ---------------------------------------------------------------- 시트

function showSheet(univer: Univer, univerAPI: FUniver, fileName: string,
  workbook: WorkbookDto): void {
  const sheets: Record<string, unknown> = {};
  const order: string[] = [];
  const drawings: { subUnitId: string; pictures: SheetPictureDto[] | null }[] = [];

  (workbook.sheets ?? []).forEach((sheet, at) => {
    const id = `${UNIT_ID}-sheet-${at}`;
    const grid = toSheetGrid(sheet.cells);
    order.push(id);
    drawings.push({ subUnitId: id, pictures: sheet.pictures });
    sheets[id] = {
      id,
      name: sheet.sheetName,
      cellData: toUniverCellData(sheet.cells ?? []),
      mergeData: (sheet.mergeRanges ?? []).map(toUniverRange),
      ...grid,
      defaultColumnWidth: DEFAULT_COLUMN_WIDTH,
      // 원본이 정한 기본 높이를 그대로 쓴다. 우리 기본으로 그리면 손대지 않은
      // 줄이 원본보다 짧아진다.
      defaultRowHeight: sheet.defaultRowHeightPx || DEFAULT_ROW_HEIGHT,
      showGridlines: 1,
      ...toRowColumnData(sheet.sizes ?? [], grid.rowCount),
    };
  });

  if (order.length === 0) {
    // 시트가 없는 xlsx 는 없지만, 없으면 화면이 통째로 비어 무엇이 잘못됐는지
    // 알 수 없다. 빈 시트 하나를 세운다.
    const id = `${UNIT_ID}-sheet-0`;
    order.push(id);
    sheets[id] = { id, name: '시트1', cellData: {}, ...toSheetGrid([]) };
  }

  univerAPI.createWorkbook({
    id: UNIT_ID,
    name: fileName,
    sheetOrder: order,
    sheets: sheets as never,
    // 그림은 시트 자료가 아니라 리소스로 들어간다. data 는 JSON 문자열이다.
    resources: [toSheetDrawingResource(UNIT_ID, drawings)],
  } as never);

  // 편집 권한을 내린다. 그러면 편집 명령이 안 먹는다 - 저장이 없는 화면에서
  // 고칠 수 있게 두면 고친 것이 조용히 사라진다(편집 화면 2026-08-07 실측).
  const permission = univer.__getInjector().get(IPermissionService);
  permission.updatePermissionPoint(new WorkbookEditablePermission(UNIT_ID).id, false);
  // 권한만으로는 안 막힌다 - 칸을 두 번 눌러 글자를 넣을 수 있었다(2026-08-27 지적).
  blockEditing(univerAPI);
}

function toUniverRange(range: MergeRangeDto) {
  return {
    startRow: range.fromRowNo, startColumn: range.fromColumnNo,
    endRow: range.toRowNo, endColumn: range.toColumnNo,
  };
}

// ---------------------------------------------------------------- 글 문서

function showDoc(univer: Univer, univerAPI: FUniver, content: DocContentDto,
  appEl: HTMLElement): void {
  const style = pageStyle(content.page);
  // 표는 원본 종이의 본문 폭에 맞춘다. 우리 기본(A4)으로 재면 레터 문서의 표가
  // 좁게 나온다(편집 화면 2026-08-13 실측).
  const bodyWidthPx = style.pageSize.width - style.marginLeft - style.marginRight;
  // 목차 점선은 우리가 채운다. Univer 는 탭 정지를 몰라 번호가 제목 뒤에 붙는다.
  // 보기 전용 화면에서만 한다 - 편집 화면에서 채우면 사람이 쓴 적 없는 점선이
  // 문서에 저장된다.
  // 글꼴을 안 적은 글은 문서 기본 글꼴로 그린다. 모르면 Univer 기본으로 그려
  // 줄이 접히는 자리가 원본과 달라진다(2026-08-25 지적).
  const defaultFont = content.defaults?.font ?? null;
  const rows = fillTabLeaders(content.paragraphs ?? [],
    canvasMeasureFor(defaultFont, content.defaults?.fontSize));
  // 줄 높이는 그 글꼴의 실제 행간으로 다시 잰다. 서버가 담아 둔 픽셀 값은 글꼴을
  // 모른 채 셈한 것이라 쪽이 밀린다(2026-08-25 지적).
  const measured = withLineHeights(rows, defaultFont, fontLineRatio,
    content.defaults?.fontSize);
  // 쪽 나눔 앞 빈 줄은 워드에서 쪽 끝에 숨는다. 우리 줄 높이가 딱 같지 않아 그
  // 줄이 다음 쪽으로 밀리면 글자 하나 없는 쪽이 생긴다(2026-08-25 실측). 지우지
  // 않고 높이만 눌러 앞 쪽에 남긴다.
  const placed = new Set<number>([
    ...(content.tables ?? []).map((table) => table.afterParagraphNo),
    ...(content.pictures ?? []).map((picture) => picture.afterParagraphNo),
  ]);
  const trimmed = shrinkBeforePageBreak(measured, placed);
  // 종이보다 큰 표지 배경은 종이 안에 넣는다. 워드는 밖을 잘라 그린다.
  const fitted = fitFloating(toDocImages(content.pictures),
    style.pageSize.width, style.pageSize.height, style.marginLeft, style.marginTop);
  const body = toBodyWithTables(trimmed, content.tables ?? [],
    fitted, UNIT_ID, bodyWidthPx, '', defaultFont);

  const headers = edgeUnit(content.header, 'header', bodyWidthPx, defaultFont);
  const footers = edgeUnit(content.footer, 'footer', bodyWidthPx, defaultFont);
  // 첫 쪽만 다른 문서가 있다. 빈 머릿글을 세워 첫 쪽을 비운다.
  const firstHeaders = content.titlePage
    ? edgeUnit(content.firstHeader ?? EMPTY_EDGE, 'first-header', bodyWidthPx, defaultFont)
    : null;
  const firstFooters = content.titlePage
    ? edgeUnit(content.firstFooter ?? EMPTY_EDGE, 'first-footer', bodyWidthPx, defaultFont)
    : null;

  univerAPI.createDocument(withFirstPageEdge({
    id: UNIT_ID,
    body: body as never,
    documentStyle: {
      ...style,
      // 문서 기본 글자. 안 주면 화면이 제 기본(11pt)을 쓴다(2026-08-26 지적).
      textStyle: {
        fs: content.defaults?.fontSize ?? 10,
        ...(defaultFont ? { ff: defaultFont } : {}),
      },
      ...(headers ? { defaultHeaderId: headers.id } : {}),
      ...(footers ? { defaultFooterId: footers.id } : {}),
      ...(content.titlePage ? { useFirstPageHeaderFooter: 1 } : {}),
      ...(firstHeaders ? { firstPageHeaderId: firstHeaders.id } : {}),
      ...(firstFooters ? { firstPageFooterId: firstFooters.id } : {}),
    },
    // 표를 넣으려면 담을 자리가 미리 있어야 한다. 없으면 명령이 조용히 실패한다.
    tableSource: (body.tableSource ?? {}) as never,
    // 그림도 같다. 본문에 자리만 있고 얼개가 없으면 아무것도 안 그려진다.
    // 머릿글 그림 얼개는 벌 안에도, 문서 전체에도 둔다. 화면이 두 곳을 다 보는데
    // 한 곳이라도 비면 그림을 못 찾아 문서가 통째로 안 그려진다(2026-08-25 실측).
    drawings: {
      ...(body.drawings ?? {}),
      ...(headers?.drawings ?? {}), ...(footers?.drawings ?? {}),
      ...(firstHeaders?.drawings ?? {}), ...(firstFooters?.drawings ?? {}),
    } as never,
    drawingsOrder: [
      ...(body.drawingsOrder ?? []),
      ...(headers?.drawingsOrder ?? []), ...(footers?.drawingsOrder ?? []),
      ...(firstHeaders?.drawingsOrder ?? []), ...(firstFooters?.drawingsOrder ?? []),
    ] as never,
    lists: docLists() as never,
    headers: { ...(headers?.map ?? {}), ...(firstHeaders?.map ?? {}) } as never,
    footers: { ...(footers?.map ?? {}), ...(firstFooters?.map ?? {}) } as never,
  }) as never);

  // 유닛을 캔버스에 붙인다. 까닭은 showUnit 에 적었다 - 안 붙이면 쪽 수만 세고
  // 화면은 빈 채로 남는다(2026-10-16 실측: 149쪽 문서가 하얗게 떴다).
  void showUnit(univer, UNIT_ID);

  // 실측용 창구. 쪽마다 첫 글을 꺼내 원본과 견준다(2026-08-25).
  (window as unknown as Record<string, unknown>).__woSkeleton = () => {
    const render = univer.__getInjector().get(IRenderManagerService).getRenderUnitById(UNIT_ID);
    const paper = render?.mainComponent as unknown as {
      getSkeleton?: () => { getSkeletonData?: () => { pages?: unknown[] } };
    } | undefined;
    return paper?.getSkeleton?.()?.getSkeletonData?.()?.pages ?? [];
  };
  showDocImages(univer, univerAPI);
  showPageCount(univer, appEl);
  blockEditing(univerAPI);
}

/** 아무것도 없는 머릿글 한 벌. 첫 쪽을 비울 때 쓴다. */
const EMPTY_EDGE: DocEdge = { paragraphs: [], tables: [] };

/**
 * 머릿글·꼬릿글 한 벌을 Univer 모양으로 짓는다.
 *
 * <p>머릿글 표는 화면(Univer 0.25.1)이 그리지 않는다 - 자리는 잡히는데 아무것도
 * 안 나온다. 표 글은 줄로 펴서 적는다(편집 화면과 같은 길이다).
 */
function edgeUnit(edge: DocEdge | null, kind: string, bodyWidth: number,
  defaultFont: string | null = null): EdgeUnit | null {
  if (edge == null) {
    return null;
  }
  const id = `${UNIT_ID}-${kind}`;
  // 그림(로고)도 얼개에 실어야 그려진다(2026-08-25 지적: 머릿글 로고가 빠졌다).
  const body = edgeBody(edge, id, bodyWidth, defaultFont,
    toDocImages(edge.pictures as never));
  // 그림 얼개는 이 벌 안에 둔다. 머릿글은 제 모형(DocumentDataModel)을 따로
  // 세워 문서 전체 얼개를 안 본다(Univer 0.25.1 실측: 로고가 안 그려졌다).
  const drawings = body.drawings ?? {};
  const drawingsOrder = body.drawingsOrder ?? [];
  const one = kind.endsWith('header')
    ? { headerId: id, body, drawings, drawingsOrder }
    : { footerId: id, body, drawings, drawingsOrder };
  return { id, map: { [id]: one }, drawings, drawingsOrder };
}

/** 머릿글·꼬릿글 한 벌. 그림은 문서 전체 얼개에 함께 실어야 그려진다. */
interface EdgeUnit {
  id: string;
  map: Record<string, unknown>;
  drawings: Record<string, unknown>;
  drawingsOrder: string[];
}

/**
 * 캔버스로 글자 폭을 재는 자를 문단마다 만든다.
 *
 * <p>문단의 첫 글자 서식(크기·글꼴)으로 잰다. 기본으로 재면 목차 줄이 넘쳐 두
 * 줄로 접힌다(2026-08-21 실측). 워드는 크기를 pt 로 적고 화면은 px 로 그린다.
 */
function canvasMeasureFor(defaultFont: string | null = null,
  defaultSize = 11): (row: { runs: string | null }) => Measure {
  const brush = document.createElement('canvas').getContext('2d');
  if (!brush) {
    return () => (text: string) => text.length * 7;
  }
  return (row) => {
    // 글꼴을 안 적은 문단은 문서 기본 글꼴로 잰다. 다른 글꼴로 재면 점을 너무
    // 많이 채워 목차 줄이 넘친다(2026-08-25 지적: 보기 화면에서만 목차가 두 쪽).
    let size = defaultSize;
    let family = defaultFont ?? 'Arial';
    try {
      const runs = row.runs
        ? JSON.parse(row.runs) as { ts?: { fs?: number; ff?: string } }[] : [];
      if (runs[0]?.ts?.fs) {
        size = runs[0].ts.fs;
      }
      if (runs[0]?.ts?.ff) {
        family = runs[0].ts.ff;
      }
    } catch {
      // 서식을 못 읽으면 기본으로 잰다. 점 몇 개 차이다.
    }
    const px = size * (96 / 72);
    return (text: string) => {
      brush.font = `${px}px ${family}, sans-serif`;
      return brush.measureText(text).width;
    };
  };
}

function pageStyle(page: DocContentDto['page']) {
  // 가져온 문서에 적힌 종이와 여백을 그대로 쓴다. 없으면 우리 기본(A4)이다.
  return {
    documentFlavor: DocumentFlavor.TRADITIONAL,
    pageSize: {
      width: page?.width || PAGE.width,
      height: page?.height || PAGE.height,
    },
    marginTop: page?.marginTop || PAGE.marginTop,
    marginBottom: page?.marginBottom || PAGE.marginBottom,
    marginLeft: page?.marginLeft || PAGE.marginLeft,
    marginRight: page?.marginRight || PAGE.marginRight,
    // 머릿글·꼬릿글이 앉을 자리. 0 이면 쪽마다 되풀이되는 글이 안 그려진다.
    marginHeader: EDGE_MARGIN,
    marginFooter: EDGE_MARGIN,
  };
}

/**
 * 가져온 그림을 화면에 건다.
 *
 * <p>문서를 세울 때 그림 얼개를 함께 넘겨도 화면은 그것을 바로 그리지 않는다 -
 * 그림 살림꾼에 알려 줘야 그린다(편집 화면 2026-08-12 실측).
 */
/**
 * 왼쪽 아래에 "지금 몇 쪽 / 모두 몇 쪽" 을 보인다.
 *
 * <p>쪽 수를 모르면 인쇄해서 올릴 문서인지 가늠할 수 없다(2026-08-21 지적).
 * 편집 화면에도 같은 것이 있다.
 */
function showPageCount(univer: Univer, appEl: HTMLElement): void {
  const label = document.createElement('div');
  label.id = 'pageCount';
  label.style.cssText = 'position:fixed;z-index:1105;padding:2px 8px;color:#1b5e3a;'
    + 'font:12px sans-serif;font-weight:600;pointer-events:none';
  label.hidden = true;
  document.body.appendChild(label);

  const paint = () => {
    const render = univer.__getInjector().get(IRenderManagerService).getRenderUnitById(UNIT_ID);
    const paper = render?.mainComponent as unknown as {
      pageMarginTop?: number;
      getSkeleton?: () => { getSkeletonData?: () => { pages?: { pageHeight: number }[] } };
    } | undefined;
    const pages = paper?.getSkeleton?.()?.getSkeletonData?.()?.pages ?? [];
    const canvas = appEl.querySelector('canvas');
    if (!render || !canvas || pages.length === 0) {
      label.hidden = true;
      return;
    }
    const viewport = render.scene.getViewports()[0] as unknown as
      { viewportScrollY?: number };
    const box = canvas.getBoundingClientRect();
    const scale = render.scene.scaleX || 1;
    const at = pageAt(pages.map((one) => one.pageHeight), paper?.pageMarginTop ?? 17,
      scale, (viewport?.viewportScrollY ?? 0) * scale, box.height);

    label.hidden = false;
    label.textContent = `${at} / ${pages.length}쪽`;
    label.style.left = `${box.left + 12}px`;
    label.style.top = `${box.top + box.height - 26}px`;
  };

  // 프레임마다 재지 않는다. 종이 자리를 물으려면 화면 크기를 재야 해서(강제
  // 레이아웃) 문단이 많은 문서가 얼어붙는다 - 편집 화면에서 겪은 일이다.
  setInterval(paint, 300);
  paint();
}

function showDocImages(univer: Univer, univerAPI: FUniver): void {
  const snapshot = univerAPI.getActiveDocument()?.getDocumentDataModel()?.getSnapshot() as unknown as {
    drawings?: Record<string, unknown>;
  } | undefined;
  const drawings = snapshot?.drawings ?? {};
  const ids = Object.keys(drawings);
  if (ids.length === 0) {
    return;
  }
  const manager = univer.__getInjector().get(IDrawingManagerService);
  manager.registerDrawingData(UNIT_ID, { [UNIT_ID]: { unitId: UNIT_ID, subUnitId: UNIT_ID,
    data: drawings as never, order: ids } } as never);
  manager.addNotification(ids.map((drawingId) => ({
    unitId: UNIT_ID, subUnitId: UNIT_ID, drawingId,
  })));
}

/**
 * 글을 고치지 못하게 한다.
 *
 * <p>시트는 권한 하나를 내리면 되지만 글 문서에는 그런 자리가 OSS 에 없다 -
 * 글자를 바꾸는 명령을 이름으로 가려 되돌린다. 자리 옮기기·스크롤은 그대로 둔다.
 */
function blockEditing(univerAPI: FUniver): void {
  univerAPI.addEvent(univerAPI.Event.BeforeCommandExecute, (event) => {
    if (isEditCommand(event.id)) {
      event.cancel = true;
    }
  });
}

// ---------------------------------------------------------------- 발표 자료

function showSlides(appEl: HTMLElement, data: SlideDeckDto): void {
  // 슬라이드는 종이처럼 그린다. 편집 화면의 #slideView 와 같은 모양이다.
  appEl.classList.add('slide-view');
  const deck = toDeckView(data);

  const draw = () => {
    // 좌우 여백을 빼고 남는 폭에 맞춘다. 원본보다 크게는 키우지 않는다.
    const room = Math.max(320, appEl.clientWidth - SLIDE_GUTTER);
    renderSlides(appEl, deck, Math.min(1, room / deck.width));
  };
  draw();
  // 창 크기가 바뀌면 배율도 다시 잡는다.
  window.addEventListener('resize', draw);
}
