import {
  CommandType, DocumentFlavor, HorizontalAlign, ICommandService, IPermissionService,
  IResourceManagerService,
  IUniverInstanceService, LocaleService, LocaleType, merge, RedoCommand, UndoCommand,
  UniverInstanceType, VerticalAlign, WrapStrategy,
  type IRange,
  type Univer,
} from '@univerjs/core';
import type { FUniver } from '@univerjs/core/lib/facade';
import { CustomCommandExecutionError, IImageIoService, JSONX } from '@univerjs/core';
import type { IIconProps } from '@univerjs/ui';
import { IconManager, ILayoutService, ILocalFileService, IMenuManagerService,
  IShortcutService, KeyCode,
  MenuItemType, MetaKeys, RibbonStartGroup } from '@univerjs/ui';
import { greenTheme } from '@univerjs/themes';
import { UniverFindReplacePlugin } from '@univerjs/find-replace';
import findReplaceKoKR from '@univerjs/find-replace/locale/ko-KR';
import '@univerjs/find-replace/lib/index.css';
import { UniverSheetsFindReplacePlugin } from '@univerjs/sheets-find-replace';
import { UniverSheetsSortPlugin } from '@univerjs/sheets-sort';
import { UniverSheetsSortUIPlugin } from '@univerjs/sheets-sort-ui';
import sheetsSortKoKR from '@univerjs/sheets-sort-ui/locale/ko-KR';
import '@univerjs/sheets-sort-ui/lib/index.css';
import { UniverSheetsFilterPlugin } from '@univerjs/sheets-filter';
import { UniverSheetsFilterUIPlugin } from '@univerjs/sheets-filter-ui';
import sheetsFilterKoKR from '@univerjs/sheets-filter-ui/locale/ko-KR';
import '@univerjs/sheets-filter-ui/lib/index.css';
import { UniverSheetsConditionalFormattingPlugin } from '@univerjs/sheets-conditional-formatting';
import {
  UniverSheetsConditionalFormattingUIPlugin,
} from '@univerjs/sheets-conditional-formatting-ui';
import conditionalKoKR from '@univerjs/sheets-conditional-formatting-ui/locale/ko-KR';
import '@univerjs/sheets-conditional-formatting-ui/lib/index.css';
import { UniverDataValidationPlugin } from '@univerjs/data-validation';
import { UniverSheetsDataValidationPlugin } from '@univerjs/sheets-data-validation';
import { UniverSheetsDataValidationUIPlugin } from '@univerjs/sheets-data-validation-ui';
// 로케일이 셋으로 나뉘어 있다. UI 것만 넣으면 규칙 이름이 번역 키 그대로 뜬다
// (2026-08-11 실측: "sheets-data-validation.decimal.title").
import validationKoKR from '@univerjs/sheets-data-validation-ui/locale/ko-KR';
import validationBaseKoKR from '@univerjs/sheets-data-validation/locale/ko-KR';
import dataValidationKoKR from '@univerjs/data-validation/locale/ko-KR';
import '@univerjs/sheets-data-validation-ui/lib/index.css';
import { UniverSheetsHyperLinkPlugin } from '@univerjs/sheets-hyper-link';
import { UniverSheetsHyperLinkUIPlugin } from '@univerjs/sheets-hyper-link-ui';
import hyperLinkKoKR from '@univerjs/sheets-hyper-link-ui/locale/ko-KR';
import '@univerjs/sheets-hyper-link-ui/lib/index.css';
import { UniverSheetsNotePlugin } from '@univerjs/sheets-note';
import { UniverSheetsNoteUIPlugin } from '@univerjs/sheets-note-ui';
import noteKoKR from '@univerjs/sheets-note-ui/locale/ko-KR';
import '@univerjs/sheets-note-ui/lib/index.css';
import { UniverThreadCommentPlugin } from '@univerjs/thread-comment';
import { UniverThreadCommentUIPlugin } from '@univerjs/thread-comment-ui';
import threadCommentKoKR from '@univerjs/thread-comment-ui/locale/ko-KR';
import '@univerjs/thread-comment-ui/lib/index.css';
import { UniverSheetsThreadCommentPlugin } from '@univerjs/sheets-thread-comment';
import { UniverSheetsThreadCommentUIPlugin } from '@univerjs/sheets-thread-comment-ui';
import sheetsCommentKoKR from '@univerjs/sheets-thread-comment-ui/locale/ko-KR';
import '@univerjs/sheets-thread-comment-ui/lib/index.css';
import { UniverSheetsTablePlugin } from '@univerjs/sheets-table';
import tableKoKR from '@univerjs/sheets-table/locale/ko-KR';
import { UniverSheetsTableUIPlugin } from '@univerjs/sheets-table-ui';
import tableUiKoKR from '@univerjs/sheets-table-ui/locale/ko-KR';
import '@univerjs/sheets-table-ui/lib/index.css';
import { IDrawingManagerService, UniverDrawingPlugin } from '@univerjs/drawing';
import { UniverDocsDrawingPlugin } from '@univerjs/docs-drawing';
import { UniverDocsDrawingUIPlugin } from '@univerjs/docs-drawing-ui';
import { UniverDrawingUIPlugin } from '@univerjs/drawing-ui';
import sheetsCoreKoKR from '@univerjs/preset-sheets-core/lib/locales/ko-KR';
import '@univerjs/preset-sheets-core/lib/index.css';
import { DocSelectionManagerService, RichTextEditingMutation } from '@univerjs/docs';
import { HoverManagerService } from '@univerjs/sheets-ui';
import { UniverSheetsCorePreset } from '@univerjs/preset-sheets-core';
import { UniverSheetsDrawingPreset } from '@univerjs/preset-sheets-drawing';
import { UniverDocsCorePreset } from '@univerjs/preset-docs-core';
import docsCoreKoKR from '@univerjs/preset-docs-core/locales/ko-KR';
import '@univerjs/preset-docs-core/lib/index.css';
import sheetsDrawingKoKR from '@univerjs/preset-sheets-drawing/lib/locales/ko-KR';
import '@univerjs/preset-sheets-drawing/lib/index.css';

import {
  DrawingApplyType, drawingPositionToTransform, ISheetDrawingService, SetDrawingApplyMutation,
} from '@univerjs/sheets-drawing';
import { IRenderManagerService } from '@univerjs/engine-render';
import {
  AddWorksheetMergeAllCommand, AddWorksheetMergeCommand,
  AddWorksheetMergeHorizontalCommand, AddWorksheetMergeVerticalCommand,
  DeltaColumnWidthCommand, DeltaRowHeightCommand, RemoveWorksheetMergeCommand,
  ResetBackgroundColorCommand, SetBackgroundColorCommand, SetColWidthCommand,
  CopySheetCommand, InsertSheetCommand, RemoveSheetCommand, SetRangeValuesMutation,
  SetRowHeightCommand,
  SetColHiddenCommand, SetRowHiddenCommand,
  SetSelectedColsVisibleCommand, SetSelectedRowsVisibleCommand,
  SetSpecificColsVisibleCommand, SetSpecificRowsVisibleCommand,
  SetWorksheetActiveOperation, SetWorksheetNameCommand, SetWorksheetOrderCommand,
  ClearSelectionContentCommand, ClearSelectionFormatCommand,
  DeleteRangeMoveLeftCommand, DeleteRangeMoveUpCommand,
  InsertColAfterCommand, InsertColBeforeCommand, InsertRowAfterCommand, InsertRowBeforeCommand,
  RemoveColCommand, RemoveRowCommand,
  SetHorizontalTextAlignCommand, SetTextRotationCommand, SetTextWrapCommand,
  SetVerticalTextAlignCommand,
  InsertColMutation, InsertRowMutation, RemoveColMutation, RemoveRowMutation,
  SetFrozenMutation, SetTabColorMutation, SetWorksheetOrderMutation, TextToNumberCommand,
  ToggleGridlinesMutation,
  WorkbookEditablePermission,
} from '@univerjs/sheets';
import { SetNumfmtCommand } from '@univerjs/sheets-numfmt';
import {
  HeaderResizeRenderController, ISheetSelectionRenderService, SetRangeBoldCommand,
  SheetPasteFormatCommand, SheetPasteValueCommand,
  SetRangeItalicCommand, SetRangeStrickThroughCommand, SetRangeUnderlineCommand,
  SheetSkeletonManagerService,
} from '@univerjs/sheets-ui';

import { createUniverFromPresets } from './bootstrap';
import { isEcho, sameList } from './remoteEcho';
import {
  fromUniverCellData, toUniverCellData,
  type CellDto, type MergeRangeDto, type UniverCell,
} from './adapter';
import {
  createApi,
  type DrawingDto, type Range, type SheetSize, type SheetSummary, type TrashItem,
  type WorkbookResource,
} from './api';
import {
  diffDrawings, SHEET_DRAWING_RESOURCE, toDrawingDto, toDrawingResource, toUniverDrawing,
  type UniverDrawing,
} from './drawingSync';
import { renderDocumentList } from './documentList';
import type { ColorLabel } from './fileMarks';
import type { RestorePoint } from './restorePoints';
import { renderHistoryPanel, renderVersionPanel } from './historyPanel';
import { toA1 } from './lockUi';
import { faviconOf, iconOf, kindLabel, type DocKind } from './docIcons';
import { deptOfUser, describeUser, nameOfUser } from './currentUser';
import { createPresenceStore, tabKey } from './presence';
import { createPresenceLayer } from './presenceLayer';
import { correctedDelta, draggedSize, hintText, type SizeAxis } from './resizeHint';
import { createMenuBar, type MenuDefinition } from './menuBar';
import { menuIcons } from './menuIcons';
import { toBody, toParagraphs, type DocParagraph } from './docBody';
import { PAGE, paperOffset, rulerTicks } from './ruler';
import { keepEditorFocus } from './focusKeeper';
import { fitFloating, toBodyWithTables, type DocImage, type DocTable } from './docTable';
import { undoKeyOf } from './undoKey';
import { movedCells } from './sheetResave';
import { HIDDEN, type SizeSnapshot, sizesOf } from './sheetSizes';
import { bySheet, type Waiting } from './saveQueue';
import { spotAfterRemove } from './afterRemove';
import { imageOf } from './clipboard';
import { imageCommandFor } from './imagePaste';
import { showUnit } from './showUnit';
import { withFirstPageEdge } from './firstPageEdge';
import { repairDrawingResource, withFreshResources, type UnitResource }
  from './docDrawingResource';
import { fontLineRatio, withLineHeights } from './docLineHeight';
import { shrinkBeforePageBreak } from './docTrimBreak';
import { createElement } from 'react';
import { Observable } from 'rxjs';
import { LINE_SPACINGS, lineSpacingOf, lineSpacingSetting, shownLineSpacing,
  type SpacedParagraph } from './lineSpacing';
import { edgeBody, type DocEdge } from './docEdge';
import { toDocImages } from './viewerSnapshot';
import { hideBusy, renderStatus, showBusy } from './busy';
import { CJK_SPACING_FLAG } from './cjkSpacing';
import {
  effectiveIndents, indentHandles, movedIndent, type IndentHandle, type Indents,
} from './docIndent';
import { docLists } from './docLists';
import { toRowColumnData } from './sheetRows';
import { renderSlides, type SlideDeckView } from './slideView';
import {
  clearMemory, lastSheetOf, rememberSheet, rememberZoom, useMemory, zoomOf,
} from './viewMemory';
import {
  columnEdges, hitTable, resizeColumns, resizeMargin, resizeRow, resizeTable,
  resizeTableLeft, rowEdges, type TableRect,
} from './tableResize';
import { wordRangeAt } from './wordRange';
import { dismissTooltipAfterClick } from './tooltipDismiss';
import { neighborSheet } from './shortcuts';

/**
 * 서버에서 읽어 둔 문서 리소스.
 *
 * <p>워크북을 만들 때 스냅샷에 넣어야 하는데, 만드는 자리와 읽는 자리가 떨어져 있어
 * 여기에 둔다. 문서를 열 때마다 새로 채운다.
 */
let savedResources: WorkbookResource[] = [];

/**
 * 우리가 서버에 남기는 리소스 이름. Univer 가 정한 것이다.
 *
 * <p>셀 값이 아닌 상태가 여기로 온다. 늘려도 저장 경로는 그대로다 - 표가 이름표로
 * 갈라 담기 때문이다.
 *
 * <p>그림(SHEET_DRAWING_PLUGIN)은 없다. 그쪽은 우리 표에 낱개로 담아 검색·감사가
 * 되게 해 두었다.
 */
/**
 * 글 문서 전체를 담는 이름표.
 *
 * <p>문단 표에는 글자와 서식만 담긴다. 표·그림·머리글은 그 모양이 아니라 담을 자리가
 * 없었다 - 표를 넣으면 칸 경계 제어문자가 글자로 새어 들어왔다(2026-08-11 실측).
 *
 * <p>그래서 문서 한 벌을 통째로 리소스에 담는다. 문단 표는 그대로 두어 docx
 * 내보내기와 검색이 쓰게 한다 - 파생물이다.
 */
const DOC_SNAPSHOT = 'WO_DOC_SNAPSHOT';

/** 가져온 표 얼개. 서버가 워드·한글에서 뽑아 담아 둔다. */
const IMPORT_TABLES = 'WO_IMPORT_TABLES';
/** 가져온 그림 자리를 담아 둔 이름표. 서버와 같은 값이다. */
const IMPORT_IMAGES = 'WO_IMPORT_IMAGES';
/** 가져온 문서의 종이 크기와 여백을 담아 둔 이름표. 서버와 같은 값이다. */
const IMPORT_PAGE = 'WO_IMPORT_PAGE';
/** 가져온 문서의 기본 서식(글꼴·크기)을 담아 둔 이름표. 서버와 같은 값이다. */
const IMPORT_DEFAULTS = 'WO_IMPORT_DEFAULTS';

/** 시트마다의 기본 행 높이. 손대지 않은 줄을 저장하지 않는 대신 이것을 쓴다. */
const SHEET_DEFAULTS = 'WO_SHEET_DEFAULTS';

/** 쪽마다 되풀이되는 글. 머릿글과 꼬릿글이다. */
const IMPORT_HEADER = 'WO_IMPORT_HEADER';

const IMPORT_FOOTER = 'WO_IMPORT_FOOTER';

/** 첫 쪽에만 쓰는 머릿글·꼬릿글과 그 표시(titlePg). */
const IMPORT_FIRST_HEADER = 'WO_IMPORT_FIRST_HEADER';

const IMPORT_FIRST_FOOTER = 'WO_IMPORT_FIRST_FOOTER';

const IMPORT_TITLE_PAGE = 'WO_IMPORT_TITLE_PAGE';

/** 가져온 표를 표로 세울지. */
const TABLE_ASSEMBLY = true;

const SAVED_RESOURCES = [
  'SHEET_FILTER_PLUGIN',
  'SHEET_CONDITIONAL_FORMATTING_PLUGIN',
  'SHEET_DATA_VALIDATION_PLUGIN',
  'SHEET_HYPER_LINK_PLUGIN',
  'SHEET_NOTE_PLUGIN',
  'SHEET_THREAD_COMMENT_BASE_PLUGIN',
  'SHEET_TABLE_PLUGIN',
  'SHEET_TABLE_UI_PLUGIN',
];

/** 마지막으로 서버에 보낸 값. 같은 것을 다시 보내지 않는다. */
const sentResources = new Map<string, string>();
import { connect } from './realtime';
import { createServerImageIoService } from './serverImageIo';
import { openShareDialog } from './shareDialog';
import { createTitleEditor } from './titleEditor';

const PRODUCT_NAME = 'Web Office';

/**
 * 셀 선택 테두리 굵기.
 *
 * <p>Univer 기본 1픽셀은 격자선과 잘 구별되지 않는다(2026-08-07 지적). 3픽셀은
 * 두껍다는 말을 들었고 4픽셀은 칸을 먹는다. 2픽셀로 둔다.
 */
const SELECTION_STROKE_WIDTH = 2;

const HEARTBEAT_MS = 15_000;
const SAVE_DEBOUNCE_MS = 1_000;

/**
 * 격자 크기. 구글 시트와 같은 감각으로 잡는다(2026-08-07 결정).
 *
 * <p>격자 크기를 안 주면 Univer 기본값 20열이 걸려 U 열부터 아예 안 보인다. DB 에는
 * 있는데 화면에 없으니 사용자는 사라진 줄 안다.
 *
 * <p><b>엑셀처럼 통째로 잡을 수는 없다.</b> 1,048,576행 × 16,384열을 주면
 * {@code createWorkbook} 자체는 98ms 로 끝나는데 그 뒤 그리기가 브라우저를 얼린다
 * (2026-08-07 실측). 5,000행 × 200열까지는 체감 지연이 없었다.
 *
 * <p>그래서 구글 시트가 쓰는 1,000행을 기본으로 삼는다. 스크롤바가 자연스럽고 빈
 * 칸이 지나치게 많지 않다. 가져온 문서가 그보다 크면 데이터에 여유를 붙여 늘린다.
 */
/**
 * 기본 칸 크기. 엑셀과 같은 값이다(2026-08-07 결정).
 *
 * <p>Univer 기본은 열 88px·행 24px 이라 엑셀 기본(8.43글자=64px, 15pt=20px)과
 * 달랐다. 서버는 손댄 줄만 저장하므로 그 차이가 손대지 않은 줄에 그대로 남아,
 * 화면에서 들어가던 글자가 엑셀에서 잘렸다(사용자 실측). 서버 쪽 같은 값은
 * {@code Px.DEFAULT_COLUMN_WIDTH} 에 있다.
 */
const DEFAULT_COLUMN_WIDTH = 64;
const DEFAULT_ROW_HEIGHT = 20;

/**
 * 이만큼 저장돼 있는데 화면이 하나도 못 읽으면 저장을 건너뛴다.
 *
 * <p>219 문서의 열 너비 22줄이 한 번에 사라진 적이 있다(2026-08-07). 조건을 다시
 * 잡지 못했으므로 원인을 막지는 못하지만, 통째로 지워지는 것은 막는다.
 */
const WIPE_GUARD = 5;

const MIN_ROWS = 1_000;
const MIN_COLUMNS = 50;
const SPARE_ROWS = 200;
const SPARE_COLUMNS = 20;
/** 자리 목록을 다시 읽는 주기. 놓친 해제 알림으로 남은 유령을 쓸어낸다. */
const PRESENCE_REFRESH_MS = 30_000;

/**
 * 워크북과 시트의 식별자를 고정한다.
 *
 * <p>그림은 unitId·subUnitId 로 자료를 찾는다. 문서를 다시 그릴 때마다 아이디가
 * 바뀌면 그림을 어디에 붙일지 알 수 없다. 한 번에 시트 하나만 여는 지금 구조에서는
 * 고정이 맞다 - 여러 시트를 동시에 열게 되면 그때 시트 아이디를 실제 SHEET_SEQ 로
 * 바꾼다.
 */
const WORKBOOK_ID = 'wo-workbook';
/**
 * 지금 화면에 그려진 시트의 Univer 안 식별자.
 *
 * <p>상수였다가 변수가 됐다. 문서에 시트가 여럿일 수 있어서다 - 엑셀에서 시트가
 * 셋인 파일을 올리면 DB 에는 셋 다 들어가는데 화면에는 첫 시트만 나왔다
 * (2026-08-07 실측). 사용자 눈에는 나머지가 사라진 것처럼 보인다.
 *
 * <p>시트 순번에서 만든다. 그래야 다시 열어도 같은 값이 되고, 그림 리소스처럼
 * 시트 식별자로 묶인 것들이 어긋나지 않는다.
 */
let SUB_UNIT_ID = 'sheet-01';

function subUnitIdOf(sheetSeq: number): string {
  return `sheet-${sheetSeq}`;
}

const api = createApi();



interface OpenSheet {
  workbookSeq: number;
  sheet: SheetSummary;
  /** 이 문서의 시트 전부. 탭을 그리고 전환할 때 쓴다. */
  sheets: SheetSummary[];
}

let open: OpenSheet | null = null;

/**
 * 이미지 업로드를 우리 서버로 돌린다.
 *
 * <p>기본 구현은 base64 data URL 을 문서 모델에 눌러앉힌다. open 을 클로저로
 * 읽으므로 문서를 바꿔도 따라간다.
 */
const imageIoService = createServerImageIoService({
  // 글 문서에는 open 이 없다. 그때는 열린 글 번호로 올린다(2026-08-26 실측:
  // 글에 그림을 붙여넣으면 조용히 아무 일도 안 일어났다).
  getWorkbookSeq: () => open?.workbookSeq ?? openDocSeq ?? null,
  api,
});

/**
 * 프리셋이 UniverDrawingPlugin 에 넘기는 override 에 우리 서비스를 끼운다.
 *
 * <p>프리셋 설정(IUniverSheetsDrawingPresetConfig)은 allowImageSize 와
 * collaboration 만 받고 override 를 열어 주지 않는다. 등록 뒤에 인젝터에서
 * 갈아끼우는 방법도 있으나 redi 는 이미 만들어진 바인딩을 바꾸지 못한다.
 * 플러그인이 등록되기 전에 설정을 손보는 것이 유일하게 확실한 지점이다.
 */
function useServerImageIo(preset: { plugins: unknown[] }) {
  for (const entry of preset.plugins) {
    if (Array.isArray(entry) && entry[0] === UniverDrawingPlugin) {
      entry[1] = {
        ...(entry[1] as object),
        override: [[IImageIoService, { useValue: imageIoService }]],
      };
      return preset;
    }
  }
  // 프리셋 구성이 바뀌어 못 찾으면 조용히 base64 로 돌아간다. 그게 가장 나쁘다.
  throw new Error('UniverDrawingPlugin 을 프리셋에서 찾지 못했습니다.');
}

/**
 * Univer 로케일에 우리가 얹는 것.
 *
 * <p>둘을 한다. (1) 툴바 풍선말에 단축키를 적는다. (2) 한국어 로케일에 빠진 문구를
 * 채운다.
 *
 * <p>툴바 풍선말에 단축키를 적는 이유:
 *
 * <p>Univer 는 단축키를 갖고 있으면서 버튼에는 안 적는다. 서비스에
 * {@code getShortcutDisplayOfCommand} 가 있는데 아무도 부르지 않는다(2026-08-10 실측).
 * 그래서 알려 주지 않으면 키가 있는 줄도 모른다.
 *
 * <p>DOM 을 건드리지 않고 문구를 바꾼다. 풍선말은 React 가 그리므로 나중에 글자를
 * 끼워 넣으려 하면 다시 그릴 때 사라진다 - 겪어 본 함정이다(파일 메뉴).
 *
 * <p>여기 적는 것은 <b>실제로 걸려 있는</b> 키만이다. 안 되는 키를 적어 두면
 * 눌러 보고 고장으로 여긴다.
 */
const LOCALE_PATCH = {
  'sheets-ui': {
    /*
      Univer 한국어 로케일에 빠진 것. 화면에 번역 키가 그대로 떴다
      (2026-08-11 실측: "sheets-ui.info.error", "sheets-ui.info.forceStringInfo").
      문구는 같은 뜻을 담은 sheets-numfmt-ui 한국어 것을 빌린다.
    */
    info: {
      error: '오류',
      forceStringInfo: '숫자가 텍스트로 저장되었습니다',
    },
    toolbar: {
      undo: '실행 취소 (Ctrl+Z)',
      redo: '다시 실행 (Ctrl+Y)',
      bold: '굵게 (Ctrl+B)',
      italic: '기울임 (Ctrl+I)',
      underline: '밑줄 (Ctrl+U)',
      strikethrough: '취소선 (Ctrl+Shift+X)',
    },
  },
};

/**
 * Univer 는 문서 종류를 정한 뒤에 만든다.
 *
 * <p>전에는 시트용으로 한 번 만들어 두고 글 문서일 때 시트 유닛만 지웠다. 시트-ui
 * 가 사라진 워크북을 계속 붙잡아 "Cannot read properties of null (reading
 * 'getUnitId')" 가 쏟아졌고, 글자 입력과 엔터가 먹지 않았다(2026-08-11 실측).
 *
 * <p>종류가 바뀌는 일은 없다 - 다른 문서로 갈 때는 주소를 바꿔 다시 들어간다.
 * 그래서 한 번 만들면 끝이다.
 */
let univer!: Univer;
let univerAPI!: FUniver;

/**
 * 글꼴 목록에 우리가 쓰는 글꼴을 얹는다.
 *
 * <p>Univer 기본 목록은 Arial·Times New Roman 에 중국어 글꼴이다. 국내 문서에
 * 쓰는 글꼴이 하나도 없어 고를 것이 없었다(2026-08-11 지적).
 *
 * <p>label 은 화면에 그대로 나온다 - 번역 키가 아니라 이름이다.
 */
const KOREAN_FONTS = [
  { value: '맑은 고딕', label: '맑은 고딕', category: 'sans-serif' },
  { value: '굴림', label: '굴림', category: 'sans-serif' },
  { value: '돋움', label: '돋움', category: 'sans-serif' },
  { value: '바탕', label: '바탕', category: 'serif' },
  { value: '궁서', label: '궁서', category: 'serif' },
  { value: '함초롬돋움', label: '함초롬돋움', category: 'sans-serif' },
  { value: '함초롬바탕', label: '함초롬바탕', category: 'serif' },
  { value: '나눔고딕', label: '나눔고딕', category: 'sans-serif' },
  { value: '나눔명조', label: '나눔명조', category: 'serif' },
];

/**
 * 프리셋 안 UI 플러그인 설정에 글꼴 목록을 얹는다.
 *
 * <p>UI 플러그인을 우리가 따로 먼저 등록하면 초기화 차례가 어긋나 화면이 아예
 * 뜨지 않는다(2026-08-11 실측). 프리셋이 넘기는 설정만 손본다.
 *
 * <p>플러그인 이름으로 찾는다 - 클래스로 견주면 번들 사정에 따라 빗나간다.
 */
function withKoreanFonts(presets: { plugins: unknown[] }[]): { plugins: unknown[] }[] {
  return presets.map((preset) => ({
    plugins: preset.plugins.map((entry) => {
      const [ctor, config] = Array.isArray(entry) ? entry : [entry, undefined];
      if ((ctor as { pluginName?: string })?.pluginName !== 'UNIVER_UI_PLUGIN') {
        return entry;
      }
      return [ctor, { ...(config as object ?? {}), customFontFamily: { list: KOREAN_FONTS } }];
    }),
  }));
}

function startUniver(kind: DocKind) {
  const created = createUniverFromPresets(
  {
    locale: LocaleType.KO_KR,
    locales: {
      [LocaleType.KO_KR]: merge({}, sheetsCoreKoKR, docsCoreKoKR, sheetsDrawingKoKR,
        findReplaceKoKR,
        sheetsSortKoKR, sheetsFilterKoKR, conditionalKoKR,
        validationKoKR, validationBaseKoKR, dataValidationKoKR,
        hyperLinkKoKR, noteKoKR, threadCommentKoKR, sheetsCommentKoKR,
        tableKoKR, tableUiKoKR, LOCALE_PATCH),
    },
    // 기본 테마가 파랑이다. 시작 화면·로그인과 같은 초록 계열로 맞춘다.
    theme: greenTheme,
  },
  withKoreanFonts(kind === 'DOC' ? [
    // 글 문서에는 글 편집기만 올린다. 시트 플러그인을 함께 올리면 그쪽 단축키와
    // 편집 가드가 글자 입력을 가로챈다 - 엔터가 먹지 않았다(2026-08-11 실측).
    UniverDocsCorePreset({ container: 'app' }),
    { plugins: [UniverFindReplacePlugin] },
    // 그림을 넣고 그리는 데 넷이 필요하다: 살림꾼(drawing), 그림 화면(drawing-ui),
    // 글 문서 살림꾼(docs-drawing), 글 문서 그림 화면(docs-drawing-ui).
    // 마지막 것이 빠지면 그림 얼개는 서는데 화면에 아무것도 안 그려진다 -
    // 그림 주소를 부르지도 않았다(2026-08-12 실측).
    // 그림 알맹이는 우리 서버에 둔다. 안 그러면 base64 로 문서에 박혀 문서가
    // 부풀고 같은 그림이 여러 번 실린다.
    { plugins: [[UniverDrawingPlugin, {
      override: [[IImageIoService, { useValue: imageIoService }]],
    }]] } as never,
    { plugins: [UniverDrawingUIPlugin, UniverDocsDrawingPlugin, UniverDocsDrawingUIPlugin] },
  ] : [
    UniverSheetsCorePreset({ container: 'app' }),
    // 시트 안의 셀 편집기가 글 엔진을 쓴다. 시트에도 글 프리셋이 필요하다.
    UniverDocsCorePreset({ container: 'app' }),
    useServerImageIo(UniverSheetsDrawingPreset()) as never,
    // 찾기·바꾸기. 프리셋이 아니라 플러그인 둘이라 여기에 끼운다 - 프리셋과 같은
    // { plugins: [...] } 모양으로 감싸면 bootstrap 이 그대로 등록한다.
    //
    // 저장할 상태가 없다. 찾는 말은 화면에만 있고 바꾼 결과는 셀 값이라 이미
    // 저장 경로를 탄다.
    { plugins: [UniverFindReplacePlugin, UniverSheetsFindReplacePlugin] },
    // 정렬은 셀 값을 실제로 옮기므로 저장할 상태가 없다. 필터는 다르다 - 무엇을
    // 걸러 두었는지가 상태라 따로 남긴다(리소스).
    { plugins: [UniverSheetsSortPlugin, UniverSheetsSortUIPlugin] },
    { plugins: [UniverSheetsFilterPlugin, UniverSheetsFilterUIPlugin] },
    { plugins: [UniverSheetsConditionalFormattingPlugin,
      UniverSheetsConditionalFormattingUIPlugin] },
    // 데이터 확인. 체크박스·드롭다운이 이 안에 있다.
    { plugins: [UniverDataValidationPlugin, UniverSheetsDataValidationPlugin,
      UniverSheetsDataValidationUIPlugin] },
    { plugins: [UniverSheetsHyperLinkPlugin, UniverSheetsHyperLinkUIPlugin] },
    { plugins: [UniverSheetsNotePlugin, UniverSheetsNoteUIPlugin] },
    // 댓글은 네 겹이다. 문서 종류를 가리지 않는 두 겹 위에 시트용 두 겹이 얹힌다.
    { plugins: [UniverThreadCommentPlugin, UniverThreadCommentUIPlugin,
      UniverSheetsThreadCommentPlugin, UniverSheetsThreadCommentUIPlugin] },
    // 표. 도형(sheets-graphics)은 넣지 않는다 - UI 패키지가 없어 사용자가 쓸
    // 방법이 없다. 번들만 커진다.
    { plugins: [UniverSheetsTablePlugin, UniverSheetsTableUIPlugin] },
  ] as { plugins: unknown[] }[]),
  );
  univer = created.univer;
  univerAPI = created.univerAPI;

  if (kind === 'DOC') {
    // 시트 facade 확장이 글 문서에도 붙는다(프리셋을 불러오는 것만으로 붙는다).
    // 그 확장은 화면이 준비될 때마다 HoverManagerService 를 찾는데 글 문서에는
    // 시트 UI 가 없어 예외가 났고, 그 뒤에 걸려야 할 구독들이 걸리지 않았다
    // (2026-08-11 실측). 서비스만 채워 둔다 - 쓰이지는 않는다.
    univer.__getInjector().add([HoverManagerService]);
  }
}
let lockSeq: number | null = null;
let lockedRange: Range | null = null;
let heartbeatTimer: ReturnType<typeof setInterval> | null = null;
let saveTimer: ReturnType<typeof setTimeout> | null = null;
let disconnect: (() => void) | null = null;
let drawingTimer: ReturnType<typeof setTimeout> | null = null;
let presenceTimer: ReturnType<typeof setInterval> | null = null;
let sizeTimer: ReturnType<typeof setTimeout> | null = null;
/** 마지막으로 서버에 보낸(또는 서버에서 받은) 크기. 같은 것을 되보내지 않기 위한 것이다. */
let lastSizes: SheetSize[] = [];
let mergeTimer: ReturnType<typeof setTimeout> | null = null;
let lastMerges: MergeRangeDto[] = [];
let applyingRemote = false;
/** 마지막으로 서버에 보낸(또는 서버에서 받은) 그림 목록. 같은 것을 되보내지 않기 위한 것이다. */
let lastDrawings: DrawingDto[] = [];
const pendingChanges = new Map<string, Waiting<CellDto>>();
/** 마지막으로 서버에 보낸(또는 서버에서 받은) 셀 모양. 같은 값을 되보내지 않기 위한 것이다. */
const lastSent = new Map<string, string>();

/**
 * 누가 어디에 있는지.
 *
 * <p>막지 않는다. 겹쳐도 둘 다 쓸 수 있고, 같은 셀이면 마지막 저장이 이긴다
 * (2026-08-06 결정). 화면이 하는 일은 "저기 사람이 있다" 를 보여 주는 것뿐이다.
 */
const presence = createPresenceStore();
// 내 탭이 남긴 자리는 그리지 않는다. 새로고침해도 같은 키라 이전 페이지의 자리까지 걸린다.
presence.setMyTabKey(tabKey(sessionStorage));


const statusEl = document.getElementById('status')!;
const fileInput = document.getElementById('fileInput') as HTMLInputElement;
const homeButton = document.getElementById('homeButton') as HTMLButtonElement;
const menuBarEl = document.getElementById('menuBar')!;
const docInput = document.getElementById('docInput') as HTMLInputElement;
const slideInput = document.getElementById('slideInput') as HTMLInputElement;
const viewInput = document.getElementById('viewInput') as HTMLInputElement;
const slideViewEl = document.getElementById('slideView')!;
const currentUserEl = document.getElementById('currentUser')!;
// 로그아웃은 등록 로그인과 함께 돌아온다. 로그인 없는 모드에서는 끊을 세션이
// 없고, 끊으면 새 손님이 되어 앞서 만든 문서를 제 것으로 못 본다.
const userBoxEl = document.getElementById('userBox')!;
const userDeptEl = document.getElementById('userDept')!;
const toolbarLeft = document.getElementById('toolbarLeft')!;
const workbookTitleEl = document.getElementById('workbookTitle')!;
const documentListEl = document.getElementById('documentList')!;
const appEl = document.getElementById('app')!;
const shareButton = document.getElementById('shareButton') as HTMLButtonElement;
const readOnlyBadge = document.getElementById('readOnlyBadge') as HTMLElement;
const shareDialogEl = document.getElementById('shareDialog') as HTMLDialogElement;
const presenceEl = document.getElementById('presence')!;
const presenceCountEl = document.getElementById('presenceCount') as HTMLButtonElement;
const presenceListEl = document.getElementById('presenceList')!;
const docKindEl = document.getElementById('docKind') as HTMLElement;
const noticeEl = document.getElementById('notice') as HTMLElement;
const noticeTextEl = document.getElementById('noticeText')!;
const noticeCloseEl = document.getElementById('noticeClose') as HTMLButtonElement;
const rulerHEl = document.getElementById('rulerH') as HTMLCanvasElement;
const rulerVEl = document.getElementById('rulerV') as HTMLCanvasElement;

function status(message: string) {
  renderStatus(statusEl, message);
}

/**
 * 일하는 중임을 알린다. 글 앞에 도는 표시가 붙는다.
 *
 * <p>글만 바뀌면 눈에 안 띈다 - 파일을 올리는 동안 멈춘 것인지 도는 것인지 알 수
 * 없다(2026-08-13 지적).
 */
function busy(message: string) {
  renderStatus(statusEl, message, true);
}

/**
 * 긴 알림을 상단 줄 아래에 눕힌다.
 *
 * <p>status() 를 쓰지 않는 이유: 상단 줄의 제목은 가운데 절대 위치라 긴 글이
 * 그 위를 덮는다(한글 가져오기 알림에서 겪었다).
 */
function notice(message: string) {
  noticeTextEl.textContent = message;
  noticeEl.hidden = false;
}

noticeCloseEl.addEventListener('click', () => { noticeEl.hidden = true; });

/**
 * 우측 상단에 누구로 접속했는지 띄운다.
 *
 * <p>실패해도 화면을 막지 않는다. 문서를 여는 것과 무관한 표시이고, 정말로
 * 인증이 없으면 이 화면 자체가 뜨지 않는다 - 서버가 앱 셸을 로그인으로 돌린다.
 */
void (async () => {
  try {
    const user = await api.me();
    // 윗줄에는 이름만 둔다. 부서와 로그아웃은 손을 얹으면 아래로 내려온다.
    currentUserEl.textContent = nameOfUser(user);
    currentUserEl.title = describeUser(user);
    userDeptEl.textContent = deptOfUser(user);
    userBoxEl.hidden = false;
  } catch {
    currentUserEl.textContent = '';
    userBoxEl.hidden = true;
  }
})();

/**
 * 그림 자료의 출처.
 *
 * <p>파사드에 그림 이벤트가 없어 인젝터에서 직접 꺼낸다(실측 문서 2절).
 * 옵저버블은 바뀐 식별자만 주므로 알림이 오면 전체를 다시 읽어 비교한다.
 *
 * <p>모듈이 읽히는 시점에 꺼내면 안 된다. 플러그인이 아직 등록 전이라 redi 가
 * "Expect 1 dependency item(s) ... but get 0" 으로 터진다(2026-08-05 실측).
 * 첫 워크북이 만들어진 뒤에 꺼내 memoize 한다.
 */
let drawingService: ReturnType<typeof resolveDrawingService> | null = null;

function resolveDrawingService() {
  return univer.__getInjector().get(ISheetDrawingService);
}

function drawingStore() {
  if (drawingService === null) {
    drawingService = resolveDrawingService();
    subscribeDrawingChanges(drawingService);
  }
  return drawingService;
}

/** 뮤테이션 실행 통로. 그림을 화면까지 반영하려면 이것을 거쳐야 한다. */
function commandService() {
  return univer.__getInjector().get(ICommandService);
}

/**
 * 시트 화면 준비.
 *
 * <p>빈 워크북을 먼저 만든다. 플러그인 로케일이 첫 워크북과 함께 올라오기 때문에
 * 로케일 손질도 그 뒤라야 남는다.
 */
function patchRibbonLocale() {
  // 탭 이름을 "시작" 에서 "홈" 으로. 엑셀 한국어판이 "홈" 이고, 우리 파일 버튼
  // 옆에서 "시작" 은 시작 화면으로 읽힌다.
  //
  // 플러그인 로케일은 첫 워크북과 함께 올라온다. 워크북을 만든 뒤라야 이 손질이
  // 남는다(2026-08-06 실측).
  univer.__getInjector().get(LocaleService).load({
    // 키는 ui.ribbon.start 다. 최상위 ribbon 이 아니라 ui 아래에 있다(2026-08-06 실측).
    [LocaleType.KO_KR]: { ui: { ribbon: { start: '홈' } } } as never,
  });
}


// ---------------------------------------------------------------- 파일 메뉴

/**
 * 파일은 메뉴가 아니라 나가는 문이다.
 *
 * <p>메뉴에 있던 "열기" 는 시작 화면의 타일이, "내려받기" 는 옆 버튼이 맡는다.
 * 열려 있는 문서가 없으면 메뉴 항목 둘 중 하나는 늘 쓸모가 없었다.
 */
// 집 아이콘을 글자 앞에 둔다. 메뉴가 아니라 자리를 뜨는 단추라는 것이 모양으로도
// 구분된다.
homeButton.prepend(Object.assign(document.createElement('span'),
  { className: 'menu-icon', innerHTML: menuIcons.home }));
homeButton.addEventListener('click', () => { void leaveDocument('/'); });

/**
 * 문서를 닫고 다른 화면으로.
 *
 * <p>저장을 먼저 비운다. 셀 저장은 1초 모아 보내므로 방금 친 값이 아직 큐에만
 * 있을 수 있다 - 그대로 나가면 그 값이 사라진다(2026-08-06 지적).
 *
 * <p>자리도 여기서 반납한다. beforeunload 에 맡기면 beacon 이 되긴 하지만,
 * 눌러서 나가는 길은 기다릴 수 있으니 확실한 쪽을 쓴다.
 */
async function leaveDocument(to: string) {
  if (saveTimer !== null) {
    clearTimeout(saveTimer);
    saveTimer = null;
  }
  if (drawingTimer !== null) {
    clearTimeout(drawingTimer);
    drawingTimer = null;
  }
  if (sizeTimer !== null) {
    clearTimeout(sizeTimer);
    sizeTimer = null;
  }
  if (mergeTimer !== null) {
    clearTimeout(mergeTimer);
    mergeTimer = null;
  }
  busy('저장 중…');
  try {
    await flushSave();
    await flushDrawingSave();
    await flushSizeSave();
    await flushMergeSave();
  } catch {
    // 저장에 실패해도 화면은 넘긴다. 실패는 flushSave 가 상태줄에 남긴다.
  }
  releaseCurrentLock();
  location.href = to;
}

/** 누가 와 있는지. 이름표는 격자 위에 있으니 여기서는 인원만 세고, 누르면 펼친다. */
function openPresenceList(open: boolean) {
  presenceListEl.hidden = !open;
  presenceCountEl.setAttribute('aria-expanded', String(open));
}

/** 열림 여부의 출처는 aria-expanded 하나다. hidden 은 "until-found" 도 될 수 있어 진위값이 아니다. */
function presenceListIsOpen(): boolean {
  return presenceCountEl.getAttribute('aria-expanded') === 'true';
}

presenceCountEl.addEventListener('click', () => {
  openPresenceList(!presenceListIsOpen());
});

document.addEventListener('pointerdown', (event) => {
  const target = event.target as Element | null;
  if (presenceListIsOpen() && !target?.closest('#presence')) {
    openPresenceList(false);
  }
});
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape' && presenceListIsOpen()) {
    openPresenceList(false);
    presenceCountEl.focus();
  }
});

// ---------------------------------------------------------------- 문서 열기

docInput.addEventListener('change', async (event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;

  try {
    busy('올리는 중…');
    // 목록 화면에는 상단 알림 줄이 없다. 화면 가운데에도 띄운다(2026-08-13 지적).
    showBusy(document.body, `${file.name} 올리는 중…`);
    const created = await api.importDoc(file);
    // 한글 문서는 글자·서식·정렬만 옮긴다. 무엇이 빠졌는지 연 화면에서 알려 준다 -
    // 표가 사라진 것을 나중에 발견하면 우리가 흘린 줄 모른다.
    const lossy = /\.hwpx?$/i.test(file.name) ? '&from=hwp' : '';
    // 주소를 바꿔 들어간다. #app 을 숨긴 채로 그리면 Univer 가 캔버스를 0 크기로 잡는다.
    location.href = `/?workbookSeq=${created.workbookSeq}${lossy}`;
  } catch (error) {
    status(messageOf(error));
  } finally {
    hideBusy(document.body);
    input.value = '';
  }
});

/**
 * 발표 자료를 올려서 본다.
 *
 * <p>고쳐 쓰지 못한다. 슬라이드 편집기가 없다(2026-08-13 결정) - 올린 그대로
 * 보여 주기만 한다.
 */
/**
 * 읽기 전용으로 잠깐 보기.
 *
 * <p>파일함에 담지 않는다 - 뷰어 길로 보내고 그 주소로 옮겨 간다. 한 시간 뒤에는
 * 사라지므로 고쳐 쓸 파일은 「가져오기」 쪽이 맞다(2026-08-20 결정).
 */
viewInput.addEventListener('change', async (event) => {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) {
    return;
  }
  busy(`${file.name} 여는 중…`);
  showBusy(document.body, `${file.name} 여는 중…`);
  try {
    const viewUrl = await api.viewMine(file);
    location.href = viewUrl;
  } catch (error) {
    hideBusy(document.body);
    status(messageOf(error));
  } finally {
    // 같은 파일을 다시 골라도 change 가 오게 비운다.
    viewInput.value = '';
  }
});

slideInput.addEventListener('change', async (event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;

  try {
    busy('올리는 중…');
    showBusy(document.body, `${file.name} 올리는 중…`);
    const created = await api.importSlides(file);
    location.href = `/?workbookSeq=${created.workbookSeq}`;
  } catch (error) {
    status(messageOf(error));
  } finally {
    hideBusy(document.body);
    input.value = '';
  }
});

/**
 * 발표 자료를 펼친다.
 *
 * <p>슬라이드 폭이 화면보다 넓으면 줄여서 통째로 보인다. 발표 자료는 한 장이 한
 * 뜻이라 잘라 보이면 읽을 수 없다.
 */
async function openSlides(workbookSeq: number) {
  documentListEl.hidden = true;
  appEl.hidden = true;
  toolbarLeft.hidden = true;
  slideViewEl.hidden = false;
  // 목록 화면이 제목을 숨겨 두었다. 다시 켜지 않으면 자리표시자만 뜬다.
  workbookTitleEl.hidden = false;
  showDocKind('SLIDE');
  void showTitle(workbookSeq);

  const deck = await api.readSlides(workbookSeq);
  drawSlides(deck);
  status(`슬라이드 — ${deck.slides.length}장 (보기 전용)`);

  // 창 크기가 바뀌면 배율도 다시 잡는다.
  window.addEventListener('resize', () => drawSlides(deck));
}

function drawSlides(deck: SlideDeckView) {
  // 좌우 여백 48px 을 빼고 남는 폭에 맞춘다. 원본보다 크게는 키우지 않는다.
  const room = Math.max(320, slideViewEl.clientWidth - 48);
  renderSlides(slideViewEl, deck, Math.min(1, room / deck.width));
}

fileInput.addEventListener('change', async (event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;

  try {
    busy('올리는 중…');
    showBusy(document.body, `${file.name} 올리는 중…`);
    const result = await api.importWorkbook(file);
    if (!appEl.hidden) {
      await openSheet(result.workbookSeq, result.sheets[0], result.sheets);
      return;
    }
    // 목록 화면에서 올린 경우다. #app 을 숨긴 채로 그리면 Univer 가 캔버스를
    // 0 크기로 잡는다. 주소를 바꿔 다시 들어간다 - 새로고침·즐겨찾기와도 맞는다.
    location.href = `/?workbookSeq=${result.workbookSeq}`;
  } catch (error) {
    status(messageOf(error));
  } finally {
    // 같은 파일을 다시 골라도 change 가 발생하게 한다.
    hideBusy(document.body);
    input.value = '';
  }
});

// 제목은 고치는 동안 폭이 계속 변한다. 그때마다 탭 줄 여백을 다시 재지 않으면
// 글자가 "시작" 위로 넘어간다(2026-08-06 실측). blur 는 되돌리기로 줄어드는 경우다.

/** 지금 연 문서에서 내 역할. 공유 대화상자가 소유자 전용 부분을 가릴 때 쓴다. */
let myRole = 'VIEWER';

let resourceSaveTimer: ReturnType<typeof setTimeout> | null = null;

/**
 * 셀 값이 아닌 상태(필터·조건부 서식)를 서버에 남긴다.
 *
 * <p>거는 동안 뮤테이션이 여러 번 오므로 모아서 한 번만 보낸다. 셀 저장과 같은
 * 방식이다.
 *
 * <p>Univer 가 들고 있는 것을 그대로 꺼내 보낸다. 우리가 JSON 을 만들면 저쪽 모양이
 * 바뀔 때마다 따라가야 한다.
 *
 * <p>바뀐 것만 보낸다. 문서를 열자마자 명령이 몇 개 지나가는데, 그때마다 같은 값을
 * 되보내면 변경 이력이 연 횟수만큼 쌓인다 - 셀에서 겪은 함정이다.
 */
function queueResourceSave() {
  if (!open || applyingRemote) {
    return;
  }
  if (resourceSaveTimer !== null) {
    clearTimeout(resourceSaveTimer);
  }
  resourceSaveTimer = setTimeout(() => {
    resourceSaveTimer = null;
    const workbookSeq = open?.workbookSeq;
    if (workbookSeq == null) return;

    const resources = univer.__getInjector().get(IResourceManagerService)
      .getResources(WORKBOOK_ID, UniverInstanceType.UNIVER_SHEET) as WorkbookResource[];

    for (const name of SAVED_RESOURCES) {
      // 끄면 Univer 가 빈 것을 준다. 그대로 보내면 서버가 지운다.
      //
      // "{}" 도 빈 것으로 본다. 플러그인마다 빈 상태를 빈 문자열로 주기도 하고
      // 빈 객체로 주기도 하는데, 담아 두면 쓰지도 않는 줄이 문서마다 쌓인다
      // (2026-08-11 실측: 필터·데이터 확인을 쓴 적 없는 문서에 "{}" 가 남았다).
      const raw = resources.find((one) => one.name === name)?.data ?? '';
      const data = raw.trim() === '{}' ? '' : raw;
      if (sentResources.get(name) === data) {
        continue;
      }
      sentResources.set(name, data);
      void api.saveResource(workbookSeq, name, data)
        .catch((error) => status(messageOf(error)));
    }
  }, 700);
}

/**
 * 틀 고정을 서버에 남긴다. Univer 손잡이로 바꾼 것을 받는 자리다.
 *
 * <p>어느 시트인지는 뮤테이션이 알려 주는 것을 쓴다. "지금 보고 있는 시트" 로
 * 짐작하면 되돌리기가 다른 시트 값을 덮는다.
 */
/**
 * 걸러 보기(필터)를 건 자리를 시트에 담는다.
 *
 * <p>화면 필터는 리소스에 담기지만 내려받는 파일은 시트에 적힌 자리를 본다.
 * 걸러 보기를 풀면 빈 값을 보내 지운다.
 */
function saveFilterRange() {
  if (!open || applyingRemote) {
    return;
  }
  const active = univerAPI.getActiveWorkbook()?.getActiveSheet();
  const subUnitId = (active as unknown as { getSheetId?: () => string })?.getSheetId?.();
  const sheet = open.sheets.find((one) => subUnitIdOf(one.sheetSeq) === subUnitId)
    ?? open.sheet;
  const worksheet = active as unknown as {
    getFilter?: () => { getRange?: () => { getA1Notation?: () => string } } | null;
  } | undefined;
  const range = worksheet?.getFilter?.()?.getRange?.()?.getA1Notation?.() ?? '';
  void api.setFilterRange(sheet.sheetSeq, range)
    .catch((cannotSave) => console.warn('걸러 보기 자리를 담지 못했다', cannotSave));
}

function saveFreeze(params?: { subUnitId?: string; ySplit?: number; xSplit?: number }) {
  if (!open || applyingRemote) {
    return;
  }
  const sheet = open.sheets.find((one) => subUnitIdOf(one.sheetSeq) === params?.subUnitId);
  if (!sheet) {
    return;
  }
  const rows = Math.max(0, params?.ySplit ?? 0);
  const columns = Math.max(0, params?.xSplit ?? 0);
  // 이미 그 값이면 보내지 않는다. 문서를 열 때 스냅샷이 같은 값을 다시 흘린다.
  if (sheet.freezeRowNo === rows && sheet.freezeColumnNo === columns) {
    return;
  }
  sheet.freezeRowNo = rows;
  sheet.freezeColumnNo = columns;

  void api.setFreeze(sheet.sheetSeq, rows, columns)
    .catch((error) => status(messageOf(error)));
}

/**
 * 눈금선 상태를 서버에 남긴다.
 *
 * <p>화면에서만 끄면 새로고침에 되살아난다 - 사용자는 자기가 끈 것이 왜 돌아왔는지
 * 모른다. 엑셀도 문서에 담는 설정이다.
 *
 * <p>시트마다 다르므로 뮤테이션이 알려 준 시트에 적는다. "지금 보고 있는 시트" 로
 * 짐작하면 되돌리기가 다른 시트의 값을 덮어쓸 수 있다.
 */
function saveGridlines(params?: { subUnitId?: string; showGridlines?: number }) {
  if (!open || applyingRemote) {
    return;
  }
  const sheet = open.sheets.find((one) => subUnitIdOf(one.sheetSeq) === params?.subUnitId);
  if (!sheet) {
    return;
  }
  const show = params?.showGridlines !== 0;
  // 이미 그 값이면 보내지 않는다. 문서를 열 때 스냅샷이 같은 값을 다시 흘릴 수 있다.
  if (sheet.showGridlines === show) {
    return;
  }
  sheet.showGridlines = show;

  void api.setGridlines(sheet.sheetSeq, show)
    .then(() => status(show ? '눈금선을 보입니다' : '눈금선을 감춥니다'))
    .catch((error) => status(messageOf(error)));
}

/**
 * 시트 탭 색을 저장한다.
 *
 * <p>화면에서만 칠하면 새로고침에 사라진다(2026-08-19 지적). 엑셀도 문서에 담는
 * 설정이다.
 *
 * <p>격자선과 같은 규칙이다 - 뮤테이션이 알려 준 시트에 적고, 값이 그대로면
 * 보내지 않는다. 빈 문자열은 "색 지우기" 다.
 */
function saveTabColor(params?: { subUnitId?: string; color?: string }) {
  if (!open || applyingRemote) {
    return;
  }
  const sheet = open.sheets.find((one) => subUnitIdOf(one.sheetSeq) === params?.subUnitId);
  if (!sheet) {
    return;
  }
  const color = params?.color?.trim() ?? '';
  if ((sheet.tabColor ?? '') === color) {
    return;
  }
  sheet.tabColor = color === '' ? null : color;

  void api.setTabColor(sheet.sheetSeq, color)
    .then(() => status(color === '' ? '탭 색을 지웠습니다' : '탭 색을 바꿨습니다'))
    .catch((error) => status(messageOf(error)));
}

/** 지금 글 문서를 보고 있는가. 시트와 글은 부를 수 있는 함수가 다르다. */
function inDoc(): boolean {
  return openDocSeq != null;
}


/**
 * 되돌리기 키를 화면 어디서 눌러도 듣는다.
 *
 * <p>AI 대화창 옆 단추나 패널에 손이 가 있으면 Ctrl+Z 가 격자까지 닿지 않는다
 * (2026-08-26 실측). 글을 적는 칸에서는 손대지 않는다.
 */
window.addEventListener('keydown', (event) => {
  const wanted = undoKeyOf(event);
  if (wanted == null) {
    return;
  }
  event.preventDefault();
  void univerAPI.executeCommand(wanted === 'UNDO' ? UndoCommand.id : RedoCommand.id);
});

/**
 * 글에 그림을 넣는다.
 *
 * <p>그림 넣기 명령은 파일을 인자로 받지 않는다 - 스스로 파일 고르기 창을 연다.
 * 붙여넣기에는 그 창이 필요 없으므로, 창을 여는 자리에 우리 파일을 잠깐 끼워
 * 넣는다. 명령이 끝나면 원래대로 돌려놓는다.
 */
async function insertDocImage(image: File, commandId: string): Promise<boolean> {
  const opener = univer.__getInjector().get(ILocalFileService) as unknown as {
    openFile: (options?: unknown) => Promise<File[]>;
  };
  const real = opener.openFile.bind(opener);
  opener.openFile = async () => [image];
  try {
    return await univerAPI.executeCommand(commandId) as unknown as boolean;
  } finally {
    opener.openFile = real;
  }
}

/**
 * 클립보드 그림 붙여넣기.
 *
 * <p>격자는 그림을 글자로 바꿔 넣는다(2026-08-26 실측: 캡처를 붙였더니 칸에 글이
 * 들어갔다). 그림이 들어 있으면 우리가 가로채 넣는다 - 시트는 떠 있는 그림,
 * 글은 글 그림 명령이다. 어느 명령인지와 그 까닭은 {@link imageCommandFor} 에
 * 적었다. 글·표는 그대로 격자에 맡긴다.
 */
document.addEventListener('paste', (event) => {
  const image = imageOf(event.clipboardData);
  if (image == null) {
    return;
  }
  if (!canWrite()) {
    status('읽기 전용 문서라 그림을 넣을 수 없습니다');
    return;
  }
  if (inDoc()) {
    // 글에는 facade 가 없다. 그림 넣기 명령을 직접 부른다(플러그인이 파일을 받아
    // 우리 서버로 올린다 - 이미지 업로드 길은 시트와 같다).
    event.preventDefault();
    event.stopImmediatePropagation();
    // 명령은 "어디에 넣을지" 를 커서에서 읽는다. 커서가 없으면 글 끝에 세운다.
    const doc = univerAPI.getActiveDocument();
    const end = Math.max(0, (doc?.getDocumentDataModel()?.getSnapshot()?.body?.dataStream?.length ?? 2) - 2);
    doc?.setSelection?.(end, end);
    void insertDocImage(image, imageCommandFor('DOC', image).id)
      .then((done) => status(done ? '그림을 붙여넣었습니다' : '그림을 붙여넣지 못했습니다'))
      .catch((error) => status(messageOf(error)));
    return;
  }
  if (univerAPI.getActiveWorkbook() == null) {
    return;
  }
  // 격자가 같은 붙여넣기를 글자로 또 넣지 못하게 막는다.
  event.preventDefault();
  event.stopImmediatePropagation();
  const command = imageCommandFor('SHEET', image);
  void Promise.resolve(univerAPI.executeCommand(command.id, command.params))
    .then((done) => status(done ? '그림을 붙여넣었습니다' : '그림을 붙여넣지 못했습니다'))
    .catch((error) => status(messageOf(error)));
}, true);

/** 격자 안 한 칸의 자리. getCellRect 가 주는 값은 캔버스 안 좌표다. */
shareButton.addEventListener('click', () => {
  if (!open) return;
  void openShareDialog({
    dialog: shareDialogEl,
    workbookSeq: open.workbookSeq,
    isOwner: myRole === 'OWNER',
    api,
    onError: (message) => status(message),
  }).catch((error) => status(messageOf(error)));
});

/**
 * 제목 표시와 수정.
 *
 * <p>권한은 문서마다 다르므로 만들 때가 아니라 문서를 열 때 정한다.
 */
const titleEditor = createTitleEditor({
  element: workbookTitleEl,
  canWrite: false,
  onRename: (name) => {
    // open 은 시트를 열 때만 채워진다. 그것만 보면 **글 문서의 제목 바꾸기가
    // 아무 말 없이 사라진다** - 화면의 글자는 바뀌고, 서버는 그대로다. 글은
    // openDocSeq 로 열린다(355·1633행이 쓰는 것과 같은 관용구다).
    const workbookSeq = open?.workbookSeq ?? openDocSeq;
    if (workbookSeq == null) return;
    void api.renameWorkbook(workbookSeq, name)
      .then(() => status('제목을 바꿨습니다'))
      .catch((error) => status(messageOf(error)));
  },
});

/**
 * 브라우저 탭 제목.
 *
 * <p>탭이 여럿일 때 어느 것이 어느 문서인지 알 수 있어야 한다 - 전부 "Web Office"
 * 였다(2026-08-07 지적). 목록 화면이나 못 여는 문서에서는 제품 이름만 남긴다.
 */
function showPageTitle(workbookName: string | null) {
  const name = workbookName?.trim();
  document.title = name ? `${name} — ${PRODUCT_NAME}` : PRODUCT_NAME;
}

/**
 * 지금 무엇을 열었는지 왼쪽 위와 탭 아이콘에 알린다.
 *
 * <p>탭이 여럿이면 제목만으로는 어느 것이 글이고 어느 것이 시트인지 알기 어렵다.
 * 색이 먼저 보인다 - 시트는 초록, 글은 파랑.
 *
 * <p>목록 화면에서는(null) 종류가 없다. 표시를 감추고 탭 아이콘은 제품 것으로
 * 되돌린다.
 */
function showDocKind(kind: DocKind | null) {
  // 한글과 영문 사이 자동 간격. 워드는 넣고 엑셀은 넣지 않는다 - 여는 종류를
  // 따라간다. 스위치를 끼운 자리는 vite.config.ts 다.
  (globalThis as unknown as Record<string, boolean>)[CJK_SPACING_FLAG] = kind !== 'SHEET';

  // 글 문서는 파란 계열을 쓴다. 초록은 시트 몫이다(2026-08-19 요청).
  document.body.classList.toggle('wo-doc', kind === 'DOC');

  docKindEl.hidden = kind == null;
  docKindEl.innerHTML = kind ? iconOf(kind) : '';
  docKindEl.title = kind ? kindLabel(kind) : '';

  const link = document.querySelector<HTMLLinkElement>('link[rel="icon"]');
  if (link) {
    link.href = kind ? faviconOf(kind) : '/favicon.svg';
  }
}

/**
 * 셀 선택 테두리를 조금 두껍게 한다.
 *
 * <p>Univer 기본은 1픽셀이라 격자선과 잘 구별되지 않는다(2026-08-07 지적).
 *
 * <p>선택 스타일은 렌더 서비스 안에 있고 바꾸는 길이 공개돼 있지 않다. 워크북을
 * 만든 뒤 그 값을 직접 고친다 - 판올림에 이름이 바뀌면 굵기만 기본으로 돌아가고
 * 화면이 깨지지는 않는다.
 */
function thickenSelection() {
  // 워크북을 막 만든 직후에는 렌더 유닛이 아직 없다. 그때 부르면 조용히 아무 일도
  // 일어나지 않으므로 다음 프레임에 한 번 더 시도한다.
  if (!applyThickSelection()) {
    setTimeout(applyThickSelection, 0);
  }
}

function applyThickSelection(): boolean {
  const render = univer.__getInjector().get(IRenderManagerService).getRenderUnitById(WORKBOOK_ID);
  const service = render?.with(ISheetSelectionRenderService) as unknown as {
    newSelectionControl?: (...args: unknown[]) => SelectionControlLike;
    getSelectionControls?: () => SelectionControlLike[];
    __woThickened?: boolean;
  } | undefined;

  if (!service?.newSelectionControl || service.__woThickened) {
    return service?.__woThickened === true;
  }

  // 굵기는 선택 컨트롤이 들고 있다. 서비스의 _selectionStyle 을 고쳐도 소용이 없다 -
  // 그 값은 그리기에 쓰이지 않는다(2026-08-07 실측: 1픽셀 그대로였다).
  //
  // 컨트롤이 만들어질 때마다 굵기를 얹는다. 감싸는 방식이라 판올림에 이름이 바뀌면
  // 굵기만 기본으로 돌아가고 화면이 깨지지는 않는다.
  const original = service.newSelectionControl.bind(service);
  service.newSelectionControl = (...args: unknown[]) => thicken(original(...args));
  service.getSelectionControls?.().forEach(thicken);
  service.__woThickened = true;
  return true;
}

/** Univer 의 SelectionControl 중 우리가 쓰는 부분. */
interface SelectionControlLike {
  updateStyle?: (style: { strokeWidth: number }) => void;
}

function thicken(control: SelectionControlLike): SelectionControlLike {
  control.updateStyle?.({ strokeWidth: SELECTION_STROKE_WIDTH });
  return control;
}

/**
 * 머리글 경계를 끄는 동안 크기를 픽셀로 보인다.
 *
 * <p>끄는 중에는 몇 픽셀이 되는지 알 길이 없었다. 놓아 보고 아니면 다시 끄는 식이라
 * 엑셀에서 옮겨 온 표의 폭을 맞추기 어려웠다(2026-08-07 지적).
 *
 * <p>문서마다가 아니라 한 번만 건다. 문서를 닫았다 열어도 살아 있어야 하고, 리스너가
 * 겹치면 눈금이 두 번 그려진다.
 */
function installSizeHint() {
  // 캡처 단계로 받는다. Univer 가 같은 눌림을 처리하면서 전파를 멈춘다.
  document.addEventListener('pointerdown', beginSizeHint, true);
}

function beginSizeHint(event: PointerEvent) {
  // 끄는 중인지는 커서로 안다. Univer 가 경계 위에서 커서를 바꿔 두므로 그 값이
  // 곧 "여기를 누르면 크기 조정" 이라는 뜻이다. 우리가 경계를 다시 재지 않는다.
  const canvas = event.target;
  if (!(canvas instanceof HTMLCanvasElement)) return;
  const axis = axisOfCursor(getComputedStyle(canvas).cursor);
  if (!axis) return;

  const index = resizeAnchor(axis);
  const sheet = univerAPI.getActiveWorkbook()?.getActiveSheet();
  if (index == null || !sheet) return;

  const startSize = axis === 'ROW' ? sheet.getRowHeight(index) : sheet.getColumnWidth(index);
  const scale = renderScale(axis);
  const startAt = axis === 'ROW' ? event.clientY : event.clientX;

  const move = (moved: PointerEvent) => {
    const now = axis === 'ROW' ? moved.clientY : moved.clientX;
    showSizeHint(hintText(axis, draggedSize(startSize, now - startAt, scale)), moved);
  };
  const end = () => {
    window.removeEventListener('pointermove', move);
    window.removeEventListener('pointerup', end);
    window.removeEventListener('pointercancel', end);
    hideSizeHint();
  };
  window.addEventListener('pointermove', move);
  window.addEventListener('pointerup', end);
  window.addEventListener('pointercancel', end);
}

function axisOfCursor(cursor: string): SizeAxis | null {
  if (cursor === 'row-resize') return 'ROW';
  if (cursor === 'col-resize') return 'COLUMN';
  return null;
}

/**
 * 지금 끌고 있는 행·열 번호.
 *
 * <p>Univer 가 경계 위를 지날 때 이미 골라 둔 값을 그대로 쓴다. 우리가 스크롤과
 * 틀 고정을 감안해 다시 재면 Univer 와 한 칸씩 어긋날 수 있다. 공개된 필드가 아니라
 * 판올림에 이름이 바뀌면 눈금만 안 뜨고 크기 조정 자체는 그대로 된다.
 */
function resizeAnchor(axis: SizeAxis): number | null {
  const render = univer.__getInjector().get(IRenderManagerService).getRenderUnitById(WORKBOOK_ID);
  const controller = render?.with(HeaderResizeRenderController) as unknown as {
    _currentRow?: number; _currentColumn?: number;
  } | undefined;
  const index = axis === 'ROW' ? controller?._currentRow : controller?._currentColumn;
  return typeof index === 'number' ? index : null;
}

/** 확대 배율. 200% 면 화면 2픽셀이 시트 1픽셀이다. */
function renderScale(axis: SizeAxis): number {
  const render = univer.__getInjector().get(IRenderManagerService).getRenderUnitById(WORKBOOK_ID);
  const scale = render?.scene.getAncestorScale();
  const value = axis === 'ROW' ? scale?.scaleY : scale?.scaleX;
  return value && value > 0 ? value : 1;
}

function showSizeHint(text: string, at: PointerEvent) {
  const hint = sizeHintElement();
  hint.textContent = text;
  // 포인터 오른쪽 아래. 끄는 경계를 눈금이 가리면 볼 수 없다.
  hint.style.left = `${at.clientX + 14}px`;
  hint.style.top = `${at.clientY + 16}px`;
  hint.hidden = false;
}

function hideSizeHint() {
  const hint = document.getElementById('sizeHint');
  if (hint) hint.hidden = true;
}

function sizeHintElement(): HTMLElement {
  const found = document.getElementById('sizeHint');
  if (found) return found;
  const hint = document.createElement('div');
  hint.id = 'sizeHint';
  hint.hidden = true;
  document.body.append(hint);
  return hint;
}

/**
 * 열람 권한이면 화면을 읽기 전용으로 만든다.
 *
 * <p>서버는 이미 저장을 막는다. 그런데 화면이 그대로라 셀에 글자가 찍히고, 저장만
 * 403 이 나서 경고 한 줄이 뜬 뒤 값은 화면에 남았다. 새로고침하면 사라진다 -
 * 사용자는 자기가 쓴 것이 왜 없어졌는지 모른다(2026-08-07 실측).
 *
 * <p>Univer 권한을 내린다. 그러면 편집 명령이 안 먹고 서식 버튼이 스스로 흐려진다
 * (2026-08-07 실측: 버튼 38개가 disabled 가 됐다). CSS 로 툴바를 감추려다 지웠다 -
 * 권한이 이미 하는 일이고, 흐린 버튼이 사라진 버튼보다 낫다. 무엇을 못 하는지가
 * 보이기 때문이다.
 *
 * <p>배지는 따로 단다. 흐린 버튼만으로는 "권한이 없다" 와 "지금은 못 누른다" 가
 * 구분되지 않는다.
 */
function showReadOnly(readOnly: boolean) {
  readOnlyBadge.hidden = !readOnly;

  // 시트 권한이다. 글 문서에는 걸 곳이 없다 - 문서 유닛에 시트 권한 포인트를
  // 만들면 시트-ui 의 편집 가드가 그것을 보고 "보호된 범위" 라고 막는다.
  //
  // 함수가 있는지부터 본다. getActiveWorkbook 은 시트 facade 가 얹어 주는 것이라
  // 글 문서에서는 아예 없다 - 그냥 부르면 여기서 터지고 문서가 반만 열린다
  // (2026-08-11 실측).
  const api = univerAPI as unknown as { getActiveWorkbook?: () => unknown };
  if (typeof api.getActiveWorkbook !== 'function' || api.getActiveWorkbook() == null) {
    return;
  }

  const permission = univer.__getInjector().get(IPermissionService);
  const point = new WorkbookEditablePermission(WORKBOOK_ID);
  permission.updatePermissionPoint(point.id, !readOnly);
}

/**
 * 문서의 제목과 내 역할.
 *
 * <p>실패해도 문서는 연다. 제목이 안 보이는 것과 문서를 못 여는 것은 무게가 다르다.
 */
async function showTitle(workbookSeq: number) {
  try {
    const summary = await api.getWorkbook(workbookSeq);
    titleEditor.setEditable(summary.role !== 'VIEWER');
    titleEditor.show(summary.workbookName);
    showPageTitle(summary.workbookName);
    // 소유자가 아니어도 누가 함께 쓰는지는 볼 수 있어야 한다. 바꾸는 것만 소유자다.
    myRole = summary.role;
    shareButton.hidden = false;
    showReadOnly(summary.role === 'VIEWER');
  } catch {
    titleEditor.setEditable(false);
    titleEditor.show('');
    showPageTitle(null);
    shareButton.hidden = true;
    showReadOnly(false);
  }
}

/**
 * 이 시트의 기본 행 높이.
 *
 * <p>가져온 문서는 손으로 높인 줄만 담는다. 나머지 줄은 이 값으로 그린다 - 없으면
 * 원본이 22px 인 시트가 20px 로 그려져 통째로 짧아진다(2026-08-18 지적).
 */
function defaultRowHeightOf(sheetSeq?: number): number {
  const saved = savedResources.find((one) => one.name === SHEET_DEFAULTS);
  if (saved?.data == null || sheetSeq == null) {
    return DEFAULT_ROW_HEIGHT;
  }
  try {
    const all = JSON.parse(saved.data) as Record<string, number>;
    const height = all[String(sheetSeq)];
    return typeof height === 'number' && height > 0 ? height : DEFAULT_ROW_HEIGHT;
  } catch {
    return DEFAULT_ROW_HEIGHT;
  }
}

/** 시트 하나의 격자 크기. 데이터 끝에 여유를 붙이되 기본보다 작지 않게 잡는다. */
function toGrid(sheet?: SheetSummary) {
  return {
    rowCount: Math.max((sheet?.maxRowNo ?? 0) + 1 + SPARE_ROWS, MIN_ROWS),
    columnCount: Math.max((sheet?.maxColumnNo ?? 0) + 1 + SPARE_COLUMNS, MIN_COLUMNS),
    defaultColumnWidth: DEFAULT_COLUMN_WIDTH,
    defaultRowHeight: defaultRowHeightOf(sheet?.sheetSeq),
    // Univer 는 0/1 로 받는다. 서버에 없는 시트(문서가 없을 때)는 보이는 쪽이 기본이다.
    showGridlines: sheet?.showGridlines === false ? 0 : 1,
    // 탭 색. 없으면 넣지 않는다 - 빈 문자열을 주면 Univer 가 색을 칠하려 든다.
    ...(sheet?.tabColor ? { tabColor: sheet.tabColor } : {}),
    // 틀 고정. xSplit/ySplit 이 붙박이 개수고, startRow/startColumn 은 그 다음
    // 자리다 - 둘이 어긋나면 스크롤이 엉뚱한 줄부터 시작한다.
    freeze: toFreeze(sheet?.freezeRowNo ?? 0, sheet?.freezeColumnNo ?? 0),
  };
}

/** 시트 하나가 화면에 그려지는 데 필요한 것 전부. */
interface SheetData {
  sheet: SheetSummary;
  cells: CellDto[];
  drawings: DrawingDto[];
  sizes: SheetSize[];
  merges: MergeRangeDto[];
}

/** 네 갈래를 한꺼번에 부른다. 줄줄이 기다리면 시트 수만큼 왕복이 쌓인다. */
async function loadSheetData(sheet: SheetSummary): Promise<SheetData> {
  const [cells, drawings, sizes, merges] = await Promise.all([
    api.readCells(sheet.sheetSeq, {
      fromRowNo: 0,
      fromColumnNo: 0,
      toRowNo: Math.max(sheet.maxRowNo, 0),
      toColumnNo: Math.max(sheet.maxColumnNo, 0),
    }).catch(() => [] as CellDto[]),
    api.readDrawings(sheet.sheetSeq).catch(() => [] as DrawingDto[]),
    api.readSizes(sheet.sheetSeq).catch(() => [] as SheetSize[]),
    api.readMerges(sheet.sheetSeq).catch(() => [] as MergeRangeDto[]),
  ]);
  return { sheet, cells, drawings, sizes, merges };
}

async function openSheet(workbookSeq: number, sheet: SheetSummary,
                         siblings: SheetSummary[] = [sheet]) {
  releaseCurrentLock();
  disconnect?.();
  // 목록은 그리드 위를 덮는다. 올리기로 바로 들어온 경우 치우지 않으면 문서가 안 보인다.
  documentListEl.hidden = true;
  // 그리드를 먼저 보인 뒤에 그린다. 숨긴 채로 그리면 Univer 가 캔버스를 0 크기로 잡는다.
  appEl.hidden = false;
  toolbarLeft.hidden = false;
  workbookTitleEl.hidden = false;

  // 목록에서 바로 들어온 경우 종류 표시가 꺼져 있다. 여는 자리에서 다시 켠다.
  showDocKind('SHEET');

  open = { workbookSeq, sheet, sheets: siblings };
  SUB_UNIT_ID = subUnitIdOf(sheet.sheetSeq);
  lastSent.clear();
  pendingChanges.clear();
  // 지울 대기줄도 함께 비운다. 남겨 두면 앞 시트에서 지우려던 자리가 새 시트에서
  // 지워진다.
  pendingDeletes.clear();

  // 시트 전부를 한꺼번에 읽는다. 예전에는 여는 시트만 읽고 탭을 누를 때마다 워크북을
  // 통째로 다시 만들었는데, 그것이 2초쯤 걸렸고 그 사이에 다른 탭을 누르면 Univer 의
  // 자동 높이 계산이 버려진 워크시트를 붙잡고 터졌다(2026-08-07 지적).
  //
  // 이제 전환은 Univer 안에서 끝난다. 우리가 하는 일은 "지금 어느 시트인가" 를
  // 갱신하고 실시간 구독을 옮기는 것뿐이다.
  // 시트를 다 읽어 그리기 전까지는 빈 격자만 보인다. 큰 문서는 그 사이가
  // 몇 초라 멈춘 줄 안다(2026-08-20 지적). 읽는 동안 도는 표시를 덮는다.
  showBusy(document.body, `${sheet.sheetName} 여는 중…`);
  busy('여는 중…');
  const loaded = await Promise.all(siblings.map((tab) => loadSheetData(tab)))
    .finally(() => hideBusy(document.body));
  // 필터 같은 "셀 값이 아닌 상태" 는 리소스로 온다. 실패해도 문서는 연다 -
  // 필터가 없는 것과 문서를 못 여는 것은 무게가 다르다.
  savedResources = await api.readResources(workbookSeq).catch(() => []);
  // 방금 읽은 것을 "이미 보낸 것" 으로 기록한다. 이러지 않으면 문서를 여는 것만으로
  // 같은 값이 되돌아가 저장된다.
  sentResources.clear();
  for (const name of SAVED_RESOURCES) {
    sentResources.set(name, savedResources.find((one) => one.name === name)?.data ?? '');
  }
  const mine = loaded.find((data) => data.sheet.sheetSeq === sheet.sheetSeq) ?? loaded[0];

  renderWorkbook(sheet.sheetName, loaded, siblings, sheet);
  rememberSheet(workbookSeq, sheet.sheetSeq);
  saveViewMemory(workbookSeq, sheet.sheetSeq);
  applySavedZoom(sheet.sheetSeq);
  rememberMerges(mine.merges);
  rememberSizes(mine.sizes);
  const cells = mine.cells;
  const savedDrawings = mine.drawings;
  void showTitle(workbookSeq);
  status(`${sheet.sheetName} — 셀 ${cells.length}개`
    + (savedDrawings.length > 0 ? `, 그림 ${savedDrawings.length}개` : ''));

  subscribeSheet(sheet.sheetSeq);
}

/**
 * 이 문서에서 내가 보던 자리를 서버에서 받아 온다.
 *
 * <p>실패해도 문서는 연다 - 첫 시트로 여는 것과 문서를 못 여는 것은 무게가 다르다.
 */
async function loadViewMemory(workbookSeq: number) {
  clearMemory();
  const memory = await api.readView(workbookSeq).catch(() => null);
  if (memory != null) {
    useMemory(workbookSeq, memory);
  }
}

/** 보던 자리를 서버에 담는다. 실패해도 화면은 그대로 쓴다. */
function saveViewMemory(workbookSeq: number, sheetSeq: number, zoom?: number) {
  void api.saveView(workbookSeq, sheetSeq, zoom).catch(() => {
    // 기억을 못 담아도 이번 화면에서는 보던 대로 보인다.
  });
}

/**
 * 지난번에 보던 시트. 기억이 없거나 그 사이 지워졌으면 첫 시트다.
 *
 * <p>엑셀도 보던 시트로 다시 연다(2026-08-13 지적).
 */
function seenSheet(workbookSeq: number, sheets: SheetSummary[]): SheetSummary {
  const seen = lastSheetOf(workbookSeq);
  return sheets.find((sheet) => sheet.sheetSeq === seen) ?? sheets[0];
}

/** 자리 표시를 새로 잡고 그 시트 토픽을 구독한다. 시트를 옮길 때도 이것만 다시 한다. */
function subscribeSheet(sheetSeq: number) {
  presence.replaceAll([]);
  presenceLayer.clear();
  // 이미 와 있는 사람들. 이것이 없으면 상대가 다음에 움직일 때까지 안 보인다.
  refreshPresence(sheetSeq);
  startPresenceRefresh(sheetSeq);

  disconnect = connect(sheetSeq, {
    onPresenceSet: (seat) => { presence.set(seat); drawPresence(); },
    onPresenceCleared: (clearedSeq) => { presence.clear(clearedSeq); drawPresence(); },
    onCellsChanged: (cells, actorName) => applyRemoteCells(cells, actorName),
    onSizesChanged: (changed, deleted, actorName) =>
      applyRemoteSizes(changed, deleted, actorName),
    onMergesChanged: (merges, actorName) => applyRemoteMerges(merges, actorName),
    onSheetsChanged: (actorName) => void reopenSheets(actorName),
    onDrawingsChanged: (drawings, deletedIds, actorName) =>
      applyRemoteDrawings(drawings, deletedIds, actorName),
  });
}

/**
 * 워크북을 그린다. 시트 <b>전부</b>에 자료를 채운다.
 *
 * <p>예전에는 여는 시트만 채우고 탭을 누를 때마다 워크북을 다시 만들었다. 그것이
 * 2초쯤 걸렸고, 그 사이에 다른 탭을 누르면 Univer 의 자동 높이 계산이 버려진
 * 워크시트를 붙잡고 터졌다(2026-08-07 지적). 이제 전환은 Univer 안에서 끝난다.
 */
/** 기록 패널. 칸 기록과 버전 기록이 같은 자리를 쓴다. */
let historyBox: HTMLDivElement | null = null;

function historyPanel(): HTMLDivElement {
  if (historyBox == null) {
    historyBox = document.createElement('div');
    historyBox.id = 'historyPanel';
    historyBox.hidden = true;
    document.body.appendChild(historyBox);
  }
  return historyBox;
}

function hideHistory() {
  historyPanel().hidden = true;
  versionsShown = false;
}

/**
 * 문서 버전 기록.
 *
 * <p>따로 저장한 판이 아니라 편집 기록을 덩어리로 묶은 것이다 - 구글과 같다.
 * 되돌리기는 덮어쓰기가 아니라 새 편집이라, 되돌린 뒤에도 다시 앞으로 갈 수 있다.
 */
async function showVersions(quiet = false) {
  // 시트 문서와 글 문서가 같은 목록을 쓴다. 어느 쪽이 열려 있든 문서 번호는 하나다.
  const workbookSeq = open?.workbookSeq ?? openDocSeq;
  if (workbookSeq == null) {
    return;
  }
  const box = historyPanel();
  box.hidden = false;
  versionsShown = true;
  if (!quiet) {
    renderHistoryPanel(box, {
      title: '버전 기록', entries: [], notice: '읽는 중…', onClose: hideHistory,
    });
  }
  try {
    // 복구 지점은 곁가지다. 못 읽어도 버전 기록은 떠야 한다.
    const [versions, points] = await Promise.all([
      api.listVersions(workbookSeq),
      api.listRestorePoints(workbookSeq).catch(() => [] as RestorePoint[]),
    ]);
    renderVersionPanel(box, {
      versions,
      points,
      onClose: hideHistory,
      onRefresh: () => { void showVersions(); },
      onOpen: (versionId) => api.versionChanges(workbookSeq, versionId),
      onRestore: (versionId) => { void restoreVersion(workbookSeq, versionId); },
      // 고칠 수 있는 사람만 만들고 되돌린다. 볼 수만 있으면 새 문서로만 뜬다.
      onTakePoint: canWrite() ? (label) => { void takeRestorePoint(workbookSeq, label); }
        : undefined,
      onRestorePoint: canWrite()
        ? (snapshotSeq) => { void restoreToPoint(workbookSeq, snapshotSeq); } : undefined,
      onCopyPoint: (snapshotSeq) => { void copyRestorePoint(snapshotSeq); },
    });
  } catch (error) {
    renderHistoryPanel(box, {
      title: '버전 기록', entries: [], notice: messageOf(error), onClose: hideHistory,
    });
  }
}

/** 기록 패널이 버전 목록을 띄우고 있는가. 저장 뒤 다시 읽을지 가른다. */
let versionsShown = false;

/** 저장이 끝날 때마다 부른다. 잇달아 저장되므로 잠시 모았다가 한 번만 다시 읽는다. */
let versionRefreshTimer: number | null = null;

function refreshVersionsSoon() {
  if (!versionsShown || historyPanel().hidden) {
    return;
  }
  if (versionRefreshTimer !== null) {
    clearTimeout(versionRefreshTimer);
  }
  versionRefreshTimer = window.setTimeout(() => {
    versionRefreshTimer = null;
    // 조용히 다시 읽는다 - "읽는 중…" 이 깜빡이면 고치는 동안 눈에 거슬린다.
    void showVersions(true);
  }, 1200);
}

/** 되돌린 뒤에는 문서를 다시 읽는다 - 화면에 남은 옛 값이 그대로 다시 저장된다. */
async function restoreVersion(workbookSeq: number, versionId: number) {
  busy('되돌리는 중…');
  try {
    const count = await api.restoreVersion(workbookSeq, versionId);
    status(`${count}군데를 되돌렸습니다`);
    hideHistory();
    // 화면에 남은 옛 값이 그대로 다시 저장되지 않도록 문서를 다시 읽는다.
    if (open != null) {
      await openSheet(workbookSeq, open.sheet, open.sheets);
    } else if (openDocSeq === workbookSeq) {
      await openDoc(workbookSeq, workbookTitleEl.textContent ?? '');
    }
  } catch (error) {
    status(messageOf(error));
  }
}

/** 지금 모습을 복구 지점으로 남긴다. */
async function takeRestorePoint(workbookSeq: number, label: string) {
  busy('복구 지점을 만드는 중…');
  try {
    await api.takeRestorePoint(workbookSeq, label);
    status(label ? `복구 지점 "${label}" 을 만들었습니다` : '복구 지점을 만들었습니다');
    await showVersions(true);
  } catch (error) {
    status(messageOf(error));
  }
}

/** 그 시점으로 문서를 통째로 되돌린다. */
async function restoreToPoint(workbookSeq: number, snapshotSeq: number) {
  busy('되돌리는 중…');
  try {
    await api.restoreToPoint(snapshotSeq);
    status('문서를 그 시점으로 되돌렸습니다');
    hideHistory();
    // 화면에 남은 옛 값이 그대로 다시 저장되지 않도록 문서를 다시 읽는다.
    if (open != null) {
      const sheets = await fetchSheets(workbookSeq);
      const next = sheets.find((one) => one.sheetSeq === open?.sheet.sheetSeq) ?? sheets[0];
      await openSheet(workbookSeq, next, sheets);
    } else if (openDocSeq === workbookSeq) {
      await openDoc(workbookSeq, workbookTitleEl.textContent ?? '');
    }
  } catch (error) {
    status(messageOf(error));
  }
}

/** 그 시점 내용으로 새 문서를 만들어 연다. 원본은 그대로 둔다. */
async function copyRestorePoint(snapshotSeq: number) {
  busy('새 문서를 만드는 중…');
  try {
    const workbookSeq = await api.copyRestorePoint(snapshotSeq);
    // 새 창으로 연다. 지금 문서와 나란히 놓고 보라고 만든 길이다.
    window.open(`/?workbookSeq=${workbookSeq}`, '_blank');
    status('복구본을 새 창에서 열었습니다');
  } catch (error) {
    status(messageOf(error));
  }
}

/**
 * 지금 고른 칸의 수정 기록.
 *
 * <p>구글 시트는 칸을 우클릭해 연다. 우리는 우클릭 메뉴가 Univer 것이라 우리
 * 메뉴에 둔다 - 고른 칸의 왼쪽 위 한 칸이 대상이다.
 */
async function showCellHistory() {
  if (open == null) {
    return;
  }
  // 고른 자리는 Univer 에게 직접 묻는다. 자리 표시(lock)는 잠금이 성공해야
  // 갱신돼서 한 박자 늦거나 비어 있을 수 있다(2026-08-20 실측: 늘 A1 이 나왔다).
  const picked = univerAPI.getActiveWorkbook()?.getActiveSheet()?.getSelection()
    ?.getActiveRange();
  const rowNo = picked?.getRow() ?? lockedRange?.fromRowNo ?? 0;
  const columnNo = picked?.getColumn() ?? lockedRange?.fromColumnNo ?? 0;
  const box = historyPanel();
  box.hidden = false;
  renderHistoryPanel(box, {
    title: `${toA1(rowNo, columnNo)} 수정 기록`,
    entries: [],
    notice: '읽는 중…',
    onClose: hideHistory,
  });
  try {
    const entries = await api.cellHistory(open.sheet.sheetSeq, rowNo, columnNo);
    renderHistoryPanel(box, {
      title: `${toA1(rowNo, columnNo)} 수정 기록`,
      entries,
      notice: `${open.sheet.sheetName} 시트`,
      onClose: hideHistory,
    });
  } catch (error) {
    renderHistoryPanel(box, {
      title: `${toA1(rowNo, columnNo)} 수정 기록`,
      entries: [],
      notice: messageOf(error),
      onClose: hideHistory,
    });
  }
}

function renderWorkbook(sheetName: string, loaded: SheetData[],
                        siblings: SheetSummary[] = [], active?: SheetSummary) {
  const mine = loaded.find((data) => data.sheet.sheetSeq === active?.sheetSeq) ?? loaded[0];
  const cellData = toUniverCellData(mine?.cells ?? []);

  // 방금 서버에서 읽은 값을 "이미 보낸 것"으로 기록해 둔다. 이러지 않으면 문서를
  // 여는 것만으로 전 셀이 저장되고 변경 이력이 열어본 횟수만큼 쌓인다.
  for (const [rowNo, columns] of Object.entries(cellData)) {
    for (const [columnNo, cell] of Object.entries(columns)) {
      remember(Number(rowNo), Number(columnNo), cell);
    }
  }
  applyingRemote = true;
  try {
    const current = univerAPI.getActiveWorkbook();
    if (current) {
      univerAPI.disposeUnit(current.getId());
    }

    const tabs = siblings.length > 0 ? siblings : (active ? [active] : []);
    const sheetsSnapshot: Record<string, Record<string, unknown>> = {};
    const order: string[] = [];
    for (const tab of tabs) {
      const id = subUnitIdOf(tab.sheetSeq);
      const data = loaded.find((one) => one.sheet.sheetSeq === tab.sheetSeq);
      const grid = toGrid(tab);
      order.push(id);
      sheetsSnapshot[id] = {
        id,
        name: tab.sheetName,
        cellData: toUniverCellData(data?.cells ?? []),
        // 병합·크기도 스냅샷에 넣는다. 나중에 얹으려면 그 시트를 활성으로 만들어야
        // 하는데, 그러면 탭이 순간순간 옮겨 다니는 것이 보인다.
        mergeData: (data?.merges ?? []).map(toUniverRange),
        ...grid,
        ...toRowColumnData(data?.sizes ?? [], grid.rowCount),
      };
    }
    if (order.length === 0) {
      const id = SUB_UNIT_ID;
      order.push(id);
      sheetsSnapshot[id] = { id, name: sheetName, cellData, ...toGrid(active) };
    }

    // 리본을 띄우려고 만들어 둔 빈 워크북을 치운다. 그대로 두면 그것이 활성으로
    // 남아 격자가 빈 채로 보인다(2026-08-11 실측).
    const blank = univerAPI.getActiveWorkbook();
    if (blank && blank.getId() !== WORKBOOK_ID) {
      univerAPI.disposeUnit(blank.getId());
    }
    univerAPI.createWorkbook({
      id: WORKBOOK_ID,
      name: active?.sheetName ?? sheetName,
      sheetOrder: order,
      sheets: sheetsSnapshot as never,
      // 그림은 셀과 달리 시트 자료가 아니라 리소스로 들어간다. data 는 객체가
      // 아니라 JSON 문자열이어야 한다(실측 문서 1절).
      resources: [
        toDrawingResource(WORKBOOK_ID,
          loaded.map((one) => ({
            subUnitId: subUnitIdOf(one.sheet.sheetSeq), drawings: one.drawings,
          }))),
        // 그림은 우리 표에 따로 담는다. 나머지(필터 등)는 이름표 붙인 JSON 그대로다.
        ...savedResources.filter((one) => one.name !== SHEET_DRAWING_RESOURCE),
      ],
    });
    patchRibbonLocale();

    // 서버가 준 목록이 아니라 Univer 를 한 번 거친 형태를 "이미 보낸 것" 으로
    // 기록한다. 둘은 미세하게 다르다 - Univer 가 필드를 붙이고 오프셋을 다시
    // 계산한다. 서버 목록을 기준으로 삼으면 문서를 여는 것만으로 전 그림이
    // 저장된다(2026-08-05 브라우저 실측). 셀이 같은 함정에 빠진 적이 있다.
    //
    // currentDrawings() 가 접근자를 부르므로 그림 변경 구독도 여기서 걸린다.
    // 구독을 lazy 접근자 안에만 두면 저장 경로에서만 접근자가 호출돼 닭과
    // 달걀이 된다 - 구독이 없어 저장이 안 되고, 저장이 없어 접근자가 안 불린다.
    thickenSelection();

    // 유닛을 캔버스에 붙인다. 까닭은 showUnit 에 적었다. 시트는 빈 워크북을
    // 먼저 세워 둬 지금까지 걸리지 않았지만, 그 빈 것을 치우는 길이라 같은
    // 경주에 놓인다.
    void showUnit(univer, WORKBOOK_ID);

    // 만든 직후에는 첫 시트가 활성이다. 두 번째 시트를 열었다면 맞춰 준다.
    if (order[0] !== SUB_UNIT_ID) {
      univerAPI.getActiveWorkbook()?.setActiveSheet(SUB_UNIT_ID);
    }

    rememberDrawings(currentDrawings());
  } finally {
    applyingRemote = false;
  }
}

/**
 * 누가 했는지 이름으로 부른다.
 *
 * <p>"다른 사용자" 라고만 하면 셋이 붙어 있을 때 누구인지 알 수 없다. 이름을
 * 모르는 경우(인명부에 없는 사람, 사라진 계정)에만 그렇게 물러선다.
 */
function actorLabel(actorName: string | null): string {
  return actorName?.trim() ? `${actorName.trim()}님` : '다른 사용자';
}

/** 다른 사용자의 변경을 그리드에 얹는다. 전체를 다시 그리면 편집 중인 위치가 튄다. */
function applyRemoteCells(cells: CellDto[], actorName: string | null = null) {
  const sheet = univerAPI.getActiveWorkbook()?.getActiveSheet();
  if (!sheet) return;

  const data = toUniverCellData(cells);
  const incoming = Object.entries(data).flatMap(([rowNo, columns]) =>
    Object.entries(columns).map(([columnNo, cell]) => ({
      rowNo: Number(rowNo), columnNo: Number(columnNo), cell,
      key: `${rowNo}:${columnNo}`, value: JSON.stringify(cell),
    })));

  // 내가 보낸 것이 돌아온 것이면 아무것도 하지 않는다. 다시 쓰면 헛일이고,
  // 알림을 띄우면 자기 편집마다 자기 이름이 뜬다(2026-08-07 실측).
  if (isEcho(lastSent, incoming)) {
    return;
  }

  // 여기서 쓰는 값이 다시 SheetValueChanged 를 일으킨다. 막지 않으면
  // 받은 변경을 그대로 되보내고, 그것을 상대가 또 받아 무한히 오간다.
  applyingRemote = true;
  try {
    for (const entry of incoming) {
      remember(entry.rowNo, entry.columnNo, entry.cell);
      // Univer 의 셀 타입은 문단(p)을 제 문서 타입으로 좁게 잡는다. 우리는 그
      // 안을 해석하지 않고 왕복만 시키므로 여기서만 넓혀 넘긴다.
      sheet.getRange(entry.rowNo, entry.columnNo).setValue(entry.cell as never);
    }
  } finally {
    applyingRemote = false;
  }
  status(`${actorLabel(actorName)}이 셀을 고쳤습니다.`);
}

// ---------------------------------------------------------------- 그림

/**
 * 현재 그림 목록을 Univer 에서 읽는다.
 *
 * <p>우리 서버가 올린 이미지가 아닌 것(base64·외부 URL)은 뺀다. IMAGE_SEQ 가
 * 없어 저장할 수 없다. 화면에는 그대로 보이지만 저장 대상이 아니다.
 */
function currentDrawings(): DrawingDto[] {
  const forUnit = drawingStore().getDrawingDataForUnit(WORKBOOK_ID);
  const item = forUnit?.[SUB_UNIT_ID];
  if (!item) return [];

  const dtos: DrawingDto[] = [];
  item.order.forEach((drawingId, zOrder) => {
    const drawing = item.data[drawingId] as unknown as UniverDrawing | undefined;
    if (!drawing) return;
    const dto = toDrawingDto(drawing, zOrder);
    if (dto) dtos.push(dto);
  });
  return dtos;
}

function rememberDrawings(drawings: DrawingDto[]) {
  lastDrawings = drawings;
}

/** 그림 변경을 모아 보낸다. 드래그 중에는 매 프레임 알림이 오므로 디바운스가 필수다. */
function queueDrawingSave() {
  if (applyingRemote || !open) return;
  if (drawingTimer !== null) {
    clearTimeout(drawingTimer);
  }
  drawingTimer = setTimeout(() => void flushDrawingSave(), SAVE_DEBOUNCE_MS);
}

async function flushDrawingSave() {
  if (!open) return;

  const after = currentDrawings();
  const delta = diffDrawings(lastDrawings, after);
  if (delta.upserted.length === 0 && delta.deletedIds.length === 0) {
    return;
  }

  // 먼저 기록한다. 저장 중에 또 알림이 와도 같은 델타를 두 번 보내지 않는다.
  rememberDrawings(after);
  try {
    const saved = await api.saveDrawings(
      open.sheet.sheetSeq, delta.upserted, delta.deletedIds);
    status(`그림 ${saved}개 저장됨`);
  } catch (error) {
    // 다음 알림 때 다시 잡히도록 기록을 되돌린다.
    rememberDrawings(lastDrawings === after ? [] : lastDrawings);
    status(messageOf(error));
  }
}

/** 다른 사용자의 그림 변경을 얹는다. */
function applyRemoteDrawings(drawings: DrawingDto[], deletedIds: string[],
                             actorName: string | null = null) {
  const merged = new Map(lastDrawings.map((d) => [d.drawingId, d]));
  for (const id of deletedIds) {
    merged.delete(id);
  }
  for (const drawing of drawings) {
    merged.set(drawing.drawingId, drawing);
  }
  const next = [...merged.values()].sort((a, b) => a.zOrder - b.zOrder);

  const before = new Map(lastDrawings.map((d) => [d.drawingId, d]));
  const removed = deletedIds.filter((id) => before.has(id));
  const added = drawings.filter((d) => !before.has(d.drawingId));
  const updated = drawings.filter((d) => before.has(d.drawingId));

  // 받은 것을 그대로 되보내지 않도록 먼저 기록하고, 반영 중에는 저장을 막는다.
  rememberDrawings(next);
  applyingRemote = true;
  try {
    const service = drawingStore();
    // 순서가 있다. 지우고, 넣고, 고친다. 반대로 하면 같은 식별자를 다시 넣는
    // 경우에 방금 넣은 것이 지워진다.
    if (removed.length > 0) {
      applyDrawingOp(DrawingApplyType.REMOVE, service.getBatchRemoveOp(removed.map(search)));
    }
    if (added.length > 0) {
      applyDrawingOp(DrawingApplyType.INSERT, service.getBatchAddOp(added.map(toUniver)));
    }
    if (updated.length > 0) {
      applyDrawingOp(DrawingApplyType.UPDATE, service.getBatchUpdateOp(updated.map(toUniver)));
    }
  } finally {
    applyingRemote = false;
  }
  status(`${actorLabel(actorName)}이 그림을 바꿨습니다.`);
}

/**
 * 그림 변경을 화면까지 반영한다.
 *
 * <p>스토어({@code ISheetDrawingService})를 직접 갈아 끼우면 저장은 되는데 상대
 * 화면에 아무것도 안 나타난다. 그리는 쪽은 {@code IDrawingManagerService} 이고,
 * 둘을 잇는 것이 이 뮤테이션이다(2026-08-06 실측).
 *
 * <p>명령({@code InsertSheetDrawingCommand})이 아니라 뮤테이션을 쓴다. 명령은
 * 되돌리기 이력을 쌓는데, 남이 한 변경이 내 Ctrl+Z 에 걸리면 안 된다.
 */
function applyDrawingOp(type: DrawingApplyType, op: unknown) {
  const { unitId, subUnitId, redo, objects } =
    op as { unitId: string; subUnitId: string; redo: unknown; objects: unknown };
  void commandService().executeCommand(SetDrawingApplyMutation.id,
    { unitId, subUnitId, op: redo, objects, type });
}

/**
 * 실시간 반영용 그림 한 장.
 *
 * <p>셀 앵커만으로는 안 그려진다. 렌더러는 절대좌표 {@code transform} 을 본다.
 * 문서를 열 때는 Univer 의 리소스 로더가 앵커에서 그것을 계산해 주지만, 살아 있는
 * 문서에 얹을 때는 아무도 해 주지 않아 그림이 모델에만 들어가고 화면에는 없다
 * (2026-08-06 실측).
 */
function toUniver(dto: DrawingDto) {
  const drawing = toUniverDrawing(dto, WORKBOOK_ID, SUB_UNIT_ID) as Record<string, unknown>;
  const transform = drawingPositionToTransform(
    drawing.sheetTransform as never, currentSkeleton());
  return (transform ? { ...drawing, transform } : drawing) as never;
}

/** 현재 시트의 스켈레톤. 행 높이·열 너비를 아는 쪽이라 좌표 변환이 여기 붙는다. */
function currentSkeleton() {
  const render = univer.__getInjector().get(IRenderManagerService).getRenderUnitById(WORKBOOK_ID);
  return render?.with(SheetSkeletonManagerService).getCurrentParam() ?? null;
}

/** 알림이 요구하는 식별 3종. 그림 자체가 아니라 이것만 넘긴다. */
function search(drawingId: string) {
  return { unitId: WORKBOOK_ID, subUnitId: SUB_UNIT_ID, drawingId };
}

function toDrawingMap(drawings: DrawingDto[]) {
  const data: Record<string, unknown> = {};
  const order: string[] = [];
  for (const dto of drawings) {
    data[dto.drawingId] = toUniverDrawing(dto, WORKBOOK_ID, SUB_UNIT_ID);
    order.push(dto.drawingId);
  }
  return { [SUB_UNIT_ID]: { unitId: WORKBOOK_ID, subUnitId: SUB_UNIT_ID, data, order } } as never;
}

/**
 * 옵저버블은 바뀐 식별자만 준다. 무엇이 어떻게 바뀌었는지는 전체를 다시 읽어 비교한다.
 *
 * <p>하나로 묶어 돌리면 payload 타입이 달라 tsc 가 호출 시그니처를 못 맞춘다.
 */
function subscribeDrawingChanges(service: ReturnType<typeof resolveDrawingService>) {
  // 그림을 고른 것도 자리다. 셀 선택만 보면 상대가 그림을 잡고 있을 때
  // 화면에 아무 표시도 안 난다 - 그림에는 잠금이 없어 서버도 모른다.
  service.focus$.subscribe((focused) => {
    const range = focusedRange(service, focused);
    if (range !== null) {
      void acquireFor(range);
    }
  });
  service.add$.subscribe(() => queueDrawingSave());
  service.remove$.subscribe(() => queueDrawingSave());
  service.update$.subscribe(() => queueDrawingSave());
  service.refreshTransform$.subscribe(() => queueDrawingSave());
  service.order$.subscribe(() => queueDrawingSave());
}

/**
 * 고른 그림이 덮은 셀 범위.
 *
 * <p>자리는 셀 범위로만 표현된다. 그림이 걸친 칸을 그대로 쓰면 그림 언저리에
 * 테두리가 생겨 "저 사람이 이 그림을 잡고 있다" 가 읽힌다.
 */
function focusedRange(service: ReturnType<typeof resolveDrawingService>,
                      focused: { drawingId?: string }[]): Range | null {
  const first = focused[0];
  if (!first?.drawingId) return null;

  const drawing = service.getDrawingByParam({
    unitId: WORKBOOK_ID, subUnitId: SUB_UNIT_ID, drawingId: first.drawingId,
  }) as { sheetTransform?: { from: { row: number; column: number };
                             to: { row: number; column: number } } } | null;
  const at = drawing?.sheetTransform;
  if (!at) return null;

  return {
    fromRowNo: at.from.row, fromColumnNo: at.from.column,
    toRowNo: Math.max(at.to.row, at.from.row),
    toColumnNo: Math.max(at.to.column, at.from.column),
  };
}

// ---------------------------------------------------------------- 상대 위치

const presenceLayer = createPresenceLayer({
  scene: () => {
    const render = univer.__getInjector().get(IRenderManagerService)
      .getRenderUnitById(WORKBOOK_ID);
    return (render?.scene as never) ?? null;
  },
  // 범위를 픽셀 상자로 바꾼다. 떠 있는 그림이 쓰는 변환과 같은 것을 쓴다 -
  // 행 높이·열 너비를 아는 쪽은 스켈레톤 하나뿐이다.
  showNames: (labels) => {
    presenceEl.hidden = labels.length === 0;
    if (labels.length === 0) {
      openPresenceList(false);
    }
    presenceCountEl.textContent = `${labels.length}명 접속 중`;
    presenceListEl.replaceChildren(...labels.map(({ text, color }) => {
      const line = document.createElement('li');
      const dot = document.createElement('span');
      dot.className = 'dot';
      dot.style.background = color;
      const name = document.createElement('span');
      name.textContent = text;
      line.append(dot, name);
      return line;
    }));
  },
  toRect: (seat) => drawingPositionToTransform({
    from: { row: seat.fromRowNo, rowOffset: 0,
            column: seat.fromColumnNo, columnOffset: 0 },
    to: { row: seat.toRowNo + 1, rowOffset: 0,
          column: seat.toColumnNo + 1, columnOffset: 0 },
  } as never, currentSkeleton()) as never,
});

function drawPresence() {
  presenceLayer.render(presence.all());
}

function refreshPresence(sheetSeq: number) {
  void api.listPresence(sheetSeq)
    .then((seats) => { presence.replaceAll(seats); drawPresence(); })
    .catch(() => undefined);
}

/**
 * 자리 목록을 주기적으로 다시 읽는다.
 *
 * <p>해제 알림을 놓치면 아무도 없는 자리가 화면에 남는다. 알림만으로는 그것을
 * 되돌릴 길이 없어 주기 갱신이 마지막 안전망이다. 만료된 자리는 서버 목록에서
 * 빠지므로 여기서 함께 사라진다.
 */
function startPresenceRefresh(sheetSeq: number) {
  if (presenceTimer !== null) {
    clearInterval(presenceTimer);
  }
  presenceTimer = setInterval(() => refreshPresence(sheetSeq), PRESENCE_REFRESH_MS);
}

// ---------------------------------------------------------------- 자리 알림

async function acquireFor(range: Range) {
  if (!open) return;
  if (lockedRange && sameRange(lockedRange, range)) return;

  releaseCurrentLock();
  try {
    const seat = await api.acquireLock(open.sheet.sheetSeq, range, tabKey(sessionStorage));
    lockSeq = seat.lockSeq;
    lockedRange = range;
    // 자기 자리 알림도 되돌아온다. 응답보다 먼저 도착했을 수 있어 여기서 지운다.
    presence.setMine(seat.lockSeq);
    drawPresence();
    startHeartbeat();
  } catch (error) {
    // 자리 알림이 실패해도 편집은 막지 않는다. 표시가 안 될 뿐이다.
    status(messageOf(error));
  }
}

function releaseCurrentLock({ leaving = false } = {}) {
  stopHeartbeat();
  if (lockSeq !== null) {
    // 떠나는 길에는 fetch 가 중간에 죽는다. 그러면 내 자리가 만료까지 남아
    // 남들 화면에 "작성 중" 으로 보인다(2026-08-06 지적).
    if (!leaving || !api.releaseLockOnUnload(lockSeq)) {
      void api.releaseLock(lockSeq).catch(() => undefined);
    }
  }
  lockSeq = null;
  lockedRange = null;
  presence.setMine(null);
}

function startHeartbeat() {
  stopHeartbeat();
  heartbeatTimer = setInterval(() => {
    if (lockSeq !== null) {
      void api.heartbeat(lockSeq).catch(() => undefined);
    }
  }, HEARTBEAT_MS);
}

function stopHeartbeat() {
  if (heartbeatTimer !== null) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
}

// 브라우저를 닫아도 자리는 만료 회수로 풀리지만, 정상 종료에서는 즉시 돌려준다.
window.addEventListener('beforeunload', () => releaseCurrentLock({ leaving: true }));

// ---------------------------------------------------------------- 저장

function queueSave(cell: CellDto) {
  // 담을 때의 시트를 함께 적는다. 보낼 때의 시트로 보내면 시트를 더하거나 바꾸는
  // 사이에 쌓인 것이 엉뚱한 시트로 간다(2026-08-31 실측).
  pendingChanges.set(`${cell.rowNo}:${cell.columnNo}`,
    { sheetSeq: open?.sheet.sheetSeq ?? null, cell });
  if (saveTimer !== null) {
    clearTimeout(saveTimer);
  }
  saveTimer = setTimeout(() => void flushSave(), SAVE_DEBOUNCE_MS);
}

/** 값을 통째로 미는 뮤테이션들. 이것이 지나가면 그 아래가 전부 어긋난다. */
const MOVING_MUTATIONS: string[] = [
  RemoveRowMutation.id, InsertRowMutation.id,
  RemoveColMutation.id, InsertColMutation.id,
];

/** 밀린 자리를 다시 저장한다. 한 번에 몰아서 한다 - 여러 줄을 지우면 뮤테이션도 여럿이다. */
let movedTimer: ReturnType<typeof setTimeout> | null = null;

function queueMovedSave() {
  if (movedTimer !== null) {
    clearTimeout(movedTimer);
  }
  movedTimer = setTimeout(() => {
    movedTimer = null;
    saveMoved();
  }, SAVE_DEBOUNCE_MS);
}

/**
 * 화면 전체와 "이미 보낸 것" 을 견주어 다시 보낸다.
 *
 * <p>바뀐 자리는 changed 로, 사라진 자리는 deleted 로 간다. 서버가 삭제를 받는 길이
 * 이것뿐이다 - 평소 저장은 바뀐 셀만 보낸다.
 */
function saveMoved() {
  if (!open || applyingRemote) {
    return;
  }
  const sheet = univerAPI.getActiveWorkbook()?.getActiveSheet();
  const snapshot = sheet?.getSheet().getSnapshot() as unknown as
    { cellData?: Record<string, Record<string, unknown>> } | undefined;
  const cellData = snapshot?.cellData ?? {};

  const now = new Map<string, string>();
  for (const [rowNo, columns] of Object.entries(cellData)) {
    for (const [columnNo, cell] of Object.entries(columns)) {
      now.set(`${rowNo}:${columnNo}`, JSON.stringify(cell));
    }
  }
  const done = movedCells(lastSent, now);
  if (done.changed.length === 0 && done.deleted.length === 0) {
    return;
  }
  for (const key of done.changed) {
    const [rowNo, columnNo] = key.split(':').map(Number);
    const cell = cellData[rowNo]?.[columnNo] as UniverCell;
    // 서버는 서식을 객체로 되돌려 준다. 기억도 그 모양으로 해야 되돌아온 것을
    // "내가 보낸 것" 으로 알아본다 - 아니면 자기 변경이 남의 변경으로 보인다.
    const resolved = withResolvedStyle(cell);
    remember(rowNo, columnNo, resolved);
    const [dto] = fromUniverCellData({ [rowNo]: { [columnNo]: resolved } }, '', []).cells;
    queueSave(dto);
  }
  for (const key of done.deleted) {
    const [rowNo, columnNo] = key.split(':').map(Number);
    // 지운 자리도 "보낸 것" 으로 기억한다. 잊어버리면 서버가 되돌려 주는 빈 칸을
    // 남이 고친 것으로 보고 화면에 다시 써서, 되돌리기가 그 위에서 돈다.
    remember(rowNo, columnNo, {} as UniverCell);
    queueDelete(rowNo, columnNo);
  }
}

/** 서버에서 지울 자리. 화면에서 사라진 칸이다. */
const pendingDeletes = new Map<string, Waiting<CellDto>>();

function queueDelete(rowNo: number, columnNo: number) {
  const [dto] = fromUniverCellData({ [rowNo]: { [columnNo]: {} } }, '', []).cells;
  pendingDeletes.set(`${rowNo}:${columnNo}`,
    { sheetSeq: open?.sheet.sheetSeq ?? null, cell: dto });
  if (saveTimer !== null) {
    clearTimeout(saveTimer);
  }
  saveTimer = setTimeout(() => void flushSave(), SAVE_DEBOUNCE_MS);
}

async function flushSave() {
  if (!open || (pendingChanges.size === 0 && pendingDeletes.size === 0)) return;

  const changed = [...pendingChanges.values()];
  const deleted = [...pendingDeletes.values()];
  pendingChanges.clear();
  pendingDeletes.clear();

  // 담을 때의 시트로 나눠 보낸다. 한 번에 몰아 보내면 시트를 바꾸는 사이에 쌓인
  // 것이 지금 시트로 가서 남의 표를 덮는다.
  const changedBySheet = bySheet<CellDto>(changed);
  const deletedBySheet = bySheet<CellDto>(deleted);
  const sheets = new Set([...changedBySheet.keys(), ...deletedBySheet.keys()]);

  let saved = 0;
  for (const sheetSeq of sheets) {
    const rows = changedBySheet.get(sheetSeq) ?? [];
    const gone = deletedBySheet.get(sheetSeq) ?? [];
    try {
      saved += await api.saveDelta(sheetSeq, rows, gone);
    } catch (error) {
      // 실패분은 되돌려 넣어 다음 저장에 다시 시도한다. 시트도 함께 기억한다.
      for (const cell of rows) {
        pendingChanges.set(`${cell.rowNo}:${cell.columnNo}`, { sheetSeq, cell });
      }
      for (const cell of gone) {
        pendingDeletes.set(`${cell.rowNo}:${cell.columnNo}`, { sheetSeq, cell });
      }
      status(messageOf(error));
      return;
    }
  }
  status(`${saved}개 셀 저장됨`);
  refreshVersionsSoon();
}

/**
 * 셀의 서식을 객체로 푼다.
 *
 * <p>Univer 의 셀 서식({@code s})은 <b>객체일 수도 있고 문자열 id 일 수도 있다</b>
 * (타입이 {@code IStyleData | string}). 툴바로 색·배경·굵기를 주면 같은 서식을
 * 여러 셀이 공유하도록 워크북 서식표에 넣고 셀에는 그 id 만 남긴다.
 *
 * <p>id 를 그대로 저장하면 저장은 되는데 다시 열 때 아무 서식도 안 붙는다. 그 id 는
 * 그때 그 워크북 안에서만 뜻이 있어서다(2026-08-06 지적). 풀어서 보내야 한다.
 */
function withResolvedStyle(cell: UniverCell): UniverCell {
  if (typeof cell.s !== 'string') {
    return cell;
  }
  const styles = univer.__getInjector().get(IUniverInstanceService)
    .getUnit(WORKBOOK_ID) as { getStyles?: () => { get: (id: string) => unknown } } | null;
  const found = styles?.getStyles?.().get(cell.s) ?? null;
  return { ...cell, s: found as UniverCell['s'] };
}

// ---------------------------------------------------------------- 행 높이·열 너비

/**
 * 지금 화면의 크기 중 기본이 아닌 것.
 *
 * <p>Univer 는 손대지 않은 줄도 기본값으로 들고 있다. 전부 보내면 시트 하나에
 * 행이 백만 개일 때 표가 터진다 - 기본과 다른 것만 고른다.
 */
function currentSizes(): SheetSize[] | null {
  const sheet = univerAPI.getActiveWorkbook()?.getActiveSheet();
  // 시트를 못 읽었다. 빈 배열을 주면 "크기가 하나도 없다" 로 읽혀 저장하는 쪽이
  // 서버의 크기를 전부 지운다 - 실제로 219 문서의 열 너비 22줄이 한 번에 사라진
  // 적이 있다(2026-08-07). "없음" 과 "못 읽음" 은 달라야 한다.
  if (!sheet) return null;

  // 숨김(hd)도 함께 읽는다. 크기만 보면 숨긴 열이 "손댄 적 없음" 으로 빠져
  // 새로고침에 되살아난다(2026-09-03 지적).
  return sizesOf(sheet.getSheet().getSnapshot() as unknown as SizeSnapshot);
}

function sizeKey(size: SheetSize): string {
  return `${size.axisType}:${size.indexNo}`;
}

function rememberSizes(sizes: SheetSize[]) {
  lastSizes = sizes;
}

/** 화면에 얹는다. 저장이 돌지 않게 반영 중 표시를 세운다. */
function applySizes(sizes: SheetSize[]) {
  const sheet = univerAPI.getActiveWorkbook()?.getActiveSheet();
  if (!sheet) return;

  applyingRemote = true;
  try {
    for (const size of sizes) {
      if (size.sizePx == null) continue;
      const hiding = size.sizePx === HIDDEN;
      if (size.axisType === 'ROW') {
        if (hiding) {
          sheet.hideRows(size.indexNo, 1);
        } else {
          // 숨김을 먼저 푼다. 숨긴 줄에 높이만 주면 화면에서는 계속 숨어 있다.
          sheet.showRows(size.indexNo, 1);
          sheet.setRowHeight(size.indexNo, size.sizePx);
        }
      } else if (hiding) {
        sheet.hideColumns(size.indexNo, 1);
      } else {
        sheet.showColumns(size.indexNo, 1);
        sheet.setColumnWidth(size.indexNo, size.sizePx);
      }
    }
  } finally {
    applyingRemote = false;
  }
}

/**
 * 숨긴 행·열을 모두 보이게 한다.
 *
 * <p>Univer 의 우클릭 메뉴에는 "보이기" 가 없다(0.25.1 실측). 숨긴 줄을 감싸도록
 * 범위를 잡아야만 풀 수 있는데, 숨긴 것이 맨 앞이나 맨 끝이면 그 범위를 잡기도
 * 어렵다 - 우리 메뉴에 한 자리를 둔다(2026-09-04 지적).
 */
function unhideAll() {
  const sheet = univerAPI.getActiveWorkbook()?.getActiveSheet();
  if (!sheet || open == null) {
    return;
  }
  const hidden = (currentSizes() ?? []).filter((size) => size.sizePx === HIDDEN);
  if (hidden.length === 0) {
    status('숨긴 행이나 열이 없습니다');
    return;
  }

  applyingRemote = true;
  try {
    for (const size of hidden) {
      if (size.axisType === 'ROW') {
        sheet.showRows(size.indexNo, 1);
      } else {
        sheet.showColumns(size.indexNo, 1);
      }
    }
  } finally {
    applyingRemote = false;
  }

  // 서버에서는 지운다. 크기를 안 적은 줄이 곧 "기본대로 보인다" 는 뜻이다.
  void api.saveSizes(open.sheet.sheetSeq, [], hidden)
    .then(() => {
      rememberSizes(currentSizes() ?? []);
      status(`숨긴 줄 ${hidden.length}개를 다시 보입니다`);
    })
    .catch((error) => status(messageOf(error)));
}

/** 크기 변경을 모아 보낸다. 끌어 늘리는 동안 매 프레임 알림이 온다. */
function queueSizeSave() {
  if (applyingRemote || !open) return;
  if (sizeTimer !== null) {
    clearTimeout(sizeTimer);
  }
  sizeTimer = setTimeout(() => void flushSizeSave(), SAVE_DEBOUNCE_MS);
}

async function flushSizeSave() {
  if (!open) return;

  const after = currentSizes();
  if (after === null) return;

  const before = new Map(lastSizes.map((size) => [sizeKey(size), size]));
  const now = new Map(after.map((size) => [sizeKey(size), size]));

  const changed = after.filter((size) => before.get(sizeKey(size))?.sizePx !== size.sizePx);
  // 기본으로 되돌린 줄이다. 안 지우면 표가 손댄 적 없는 줄로 채워진다.
  const deleted = lastSizes.filter((size) => !now.has(sizeKey(size)))
    .map((size) => ({ axisType: size.axisType, indexNo: size.indexNo }));

  if (changed.length === 0 && deleted.length === 0) return;

  // 손댄 줄을 통째로 지우려 한다. 사용자가 수십 줄을 한 번에 기본으로 되돌리는
  // 일은 드물고, 화면 쪽 사고일 가능성이 훨씬 크다. 지우지 않고 다음을 기다린다.
  if (after.length === 0 && lastSizes.length >= WIPE_GUARD) {
    status('행·열 크기를 읽지 못했습니다. 저장을 건너뜁니다.');
    return;
  }

  rememberSizes(after);
  try {
    await api.saveSizes(open.sheet.sheetSeq, changed, deleted);
  } catch (error) {
    // 다음 변경 때 다시 잡히도록 기록을 되돌린다.
    rememberSizes(before.size === 0 ? [] : [...before.values()]);
    status(messageOf(error));
  }
}

// ---------------------------------------------------------------- 병합

function toUniverRange(merge: MergeRangeDto) {
  return {
    startRow: merge.fromRowNo, startColumn: merge.fromColumnNo,
    endRow: merge.toRowNo, endColumn: merge.toColumnNo,
  };
}

function mergeKey(merge: MergeRangeDto): string {
  return `${merge.fromRowNo}:${merge.fromColumnNo}:${merge.toRowNo}:${merge.toColumnNo}`;
}

function sameMerges(a: MergeRangeDto[], b: MergeRangeDto[]): boolean {
  return sameList(a, b, mergeKey);
}

/** 지금 시트에 있는 병합. 스냅샷을 그대로 읽는다. */
function currentMerges(): MergeRangeDto[] | null {
  const sheet = univerAPI.getActiveWorkbook()?.getActiveSheet();
  // 크기와 같은 이유. 병합은 통째로 갈아치우므로 빈 배열이 곧 전부 삭제다.
  if (!sheet) return null;

  const snapshot = sheet.getSheet().getSnapshot() as unknown as {
    mergeData?: { startRow: number; startColumn: number; endRow: number; endColumn: number }[];
  };
  return (snapshot.mergeData ?? []).map((range) => ({
    fromRowNo: range.startRow, fromColumnNo: range.startColumn,
    toRowNo: range.endRow, toColumnNo: range.endColumn,
  }));
}

function rememberMerges(merges: MergeRangeDto[]) {
  lastMerges = merges;
}

/**
 * 화면의 병합을 목록 그대로 맞춘다.
 *
 * <p>있는 것을 전부 풀고 다시 합친다. 델타로 하면 "A1:C1 을 풀고 A1:B1 로 다시
 * 합쳤다" 같은 변경에서 순서에 따라 다른 그림이 나온다.
 */
function applyMerges(merges: MergeRangeDto[]) {
  const sheet = univerAPI.getActiveWorkbook()?.getActiveSheet();
  if (!sheet) return;

  applyingRemote = true;
  try {
    for (const range of sheet.getMergedRanges()) {
      range.breakApart();
    }
    for (const merge of merges) {
      const rows = merge.toRowNo - merge.fromRowNo + 1;
      const columns = merge.toColumnNo - merge.fromColumnNo + 1;
      sheet.getRange(merge.fromRowNo, merge.fromColumnNo, rows, columns).merge();
    }
  } finally {
    applyingRemote = false;
  }
}

function queueMergeSave() {
  if (applyingRemote || !open) return;
  if (mergeTimer !== null) {
    clearTimeout(mergeTimer);
  }
  mergeTimer = setTimeout(() => void flushMergeSave(), SAVE_DEBOUNCE_MS);
}

async function flushMergeSave() {
  if (!open) return;

  const after = currentMerges();
  if (after === null || sameMerges(lastMerges, after)) return;

  const before = lastMerges;
  rememberMerges(after);
  try {
    await api.saveMerges(open.sheet.sheetSeq, after);
  } catch (error) {
    // 다음 변경 때 다시 잡히도록 기록을 되돌린다.
    rememberMerges(before);
    status(messageOf(error));
  }
}

function applyRemoteMerges(merges: MergeRangeDto[], actorName: string | null = null) {
  // 내가 방금 보낸 것이 돌아온 것이면 다시 그리지 않는다. 전부 풀고 다시 합치는
  // 일이라 눈에 띄게 깜빡이고, 알림까지 뜬다.
  if (sameMerges(lastMerges, merges)) {
    return;
  }
  rememberMerges(merges);
  applyMerges(merges);
  status(`${actorLabel(actorName)}이 셀 병합을 바꿨습니다.`);
}

function applyRemoteSizes(changed: SheetSize[], deleted: SheetSize[],
                          actorName: string | null = null) {
  // 내가 보낸 것이 돌아온 경우다. 지운 줄이 없고 바뀐 줄이 전부 내가 아는 값이면
  // 다시 얹을 것도, 알릴 것도 없다.
  const known = new Map(lastSizes.map((size) => [sizeKey(size), String(size.sizePx)]));
  const incoming = changed.map((size) => ({ key: sizeKey(size), value: String(size.sizePx) }));
  if (deleted.length === 0 && isEcho(known, incoming)) {
    return;
  }

  const merged = new Map(lastSizes.map((size) => [sizeKey(size), size]));
  for (const size of deleted) {
    merged.delete(sizeKey(size));
  }
  for (const size of changed) {
    merged.set(sizeKey(size), size);
  }
  rememberSizes([...merged.values()]);

  applySizes(changed);
  // 기본으로 되돌린 줄은 크기를 다시 알려 주지 않는다. 스냅샷의 기본값으로 돌린다.
  const sheet = univerAPI.getActiveWorkbook()?.getActiveSheet();
  const snapshot = sheet?.getSheet().getSnapshot() as unknown as
    { defaultRowHeight: number; defaultColumnWidth: number } | undefined;
  if (sheet && snapshot) {
    applyingRemote = true;
    try {
      for (const size of deleted) {
        if (size.axisType === 'ROW') {
          sheet.setRowHeight(size.indexNo, snapshot.defaultRowHeight);
        } else {
          sheet.setColumnWidth(size.indexNo, snapshot.defaultColumnWidth);
        }
      }
    } finally {
      applyingRemote = false;
    }
  }
  status(`${actorLabel(actorName)}이 행·열 크기를 바꿨습니다.`);
}

// ---------------------------------------------------------------- 내려받기

/** 지금 글 문서를 docx 로 내려받는다. 서버가 문단 표에서 뽑는다. */
function downloadDoc() {
  if (openDocSeq == null) return;
  location.href = api.docExportUrl(openDocSeq);
}

/** 지금 글 문서를 hwpx 로 내려받는다. 받는 쪽이 한글이면 이쪽이 덜 어긋난다. */
function downloadDocHwpx() {
  if (openDocSeq == null) return;
  location.href = api.docExportHwpxUrl(openDocSeq);
}

/** 지금 문서를 xlsx 로 내려받는다. 서버가 DB 에서 뽑으므로 다른 사람의 변경도 담긴다. */
function downloadWorkbook() {
  if (!open) return;
  location.href = api.exportUrl(open.workbookSeq);
}

// ---------------------------------------------------------------- Univer 연결

// SelectionChanged 는 @univerjs/sheets-ui 가 core 의 이벤트 목록에 덧붙이는 것이라
// @univerjs/sheets 쪽 목록에는 없다. 이름이 틀리면 tsc 가 아니라 런타임에서만 드러난다.
/**
 * 시트에서만 도는 Univer 이벤트 구독.
 *
 * <p>글 문서에는 시트 플러그인이 아예 없어 이 이벤트가 오지 않는다. 등록만 해도
 * 없는 이벤트 이름을 찾다가 터진다.
 */
function installSheetEvents() {
  univerAPI.addEvent(univerAPI.Event.SelectionChanged, (event: {
    selections?: Array<{ startRow: number; startColumn: number; endRow: number; endColumn: number }>;
  }) => {
    const selection = event.selections?.[0];
    if (!selection) return;
    void acquireFor({
      fromRowNo: selection.startRow,
      fromColumnNo: selection.startColumn,
      toRowNo: selection.endRow,
      toColumnNo: selection.endColumn,
    });
  });

  // 배율을 바꾸면 그 시트 것으로 기억한다. 개인 것이라 이 브라우저에만 남는다
  // (2026-08-13 지적: 시트별로 몇 %로 보고 있었는지 기억해야 한다).
  univerAPI.addEvent(univerAPI.Event.SheetZoomChanged, (event: { zoom?: number }) => {
    if (open == null || typeof event.zoom !== 'number') {
      return;
    }
    if (rememberZoom(open.sheet.sheetSeq, event.zoom)) {
      saveViewMemory(open.workbookSeq, open.sheet.sheetSeq, event.zoom);
    }
  });

  univerAPI.addEvent(univerAPI.Event.SheetValueChanged, (event) => {
    if (applyingRemote) return;

    for (const effected of event.effectedRanges ?? []) {
      // 어느 시트가 바뀐 것인지 반드시 본다. 수식은 다른 시트에서도 다시 계산되어
      // 이 자리로 온다 - 안 보면 그 값을 지금 시트의 같은 자리에 써 버린다.
      // 새 시트를 더했더니 빈 시트에 요약 시트의 계산값이 들어갔다(2026-08-31 실측).
      if (effected.getSheetId() !== SUB_UNIT_ID) continue;
      const range = effected.getRange();
      // Univer 의 ICellData 와 adapter 의 UniverCell 은 우리가 쓰는 네 필드(v·t·f·s)가
      // 같다. 서식(s)의 표현이 더 넓어 구조적으로는 안 맞으므로 여기서만 좁힌다.
      const grid = effected.getCellDataGrid() as Array<Array<UniverCell | null | undefined>>;

      grid.forEach((row, rowOffset) => {
        row.forEach((cell, columnOffset) => {
          const rowNo = range.startRow + rowOffset;
          const columnNo = range.startColumn + columnOffset;
          if (!cell) {
            return;
          }
          const resolved = withResolvedStyle(cell);
          if (unchanged(rowNo, columnNo, resolved)) {
            return;
          }
          remember(rowNo, columnNo, resolved);
          const [dto] = fromUniverCellData({ [rowNo]: { [columnNo]: resolved } }, '', []).cells;
          queueSave(dto);
        });
      });
    }
  });
}

/**
 * 실제로 바뀐 범위만 보낸다.
 *
 * <p>잠근 범위 전체를 훑던 초기 구현은 세 가지가 한꺼번에 잘못됐다. 손대지 않은
 * 빈 셀까지 빈 문자열로 덮어썼고, 수식 셀을 계산 결과값으로 바꿔 수식을 지웠고,
 * 값이 그대로여도 매번 저장해 이력을 오염시켰다. effectedRanges 가 바뀐 곳을
 * 알려주므로 그것만 쓴다.
 */
/**
 * 행 높이·열 너비 변경.
 *
 * <p>파사드에 이 이벤트가 없어 명령을 직접 듣는다. 크기 명령은 여러 개이고 델타
 * 형태(끌어 늘리기)도 있어 이름으로 거른 뒤 스냅샷 전체를 다시 읽어 비교한다 -
 * 그림 변경과 같은 방식이다.
 */
const SIZE_COMMANDS = [
  SetRowHeightCommand.id, DeltaRowHeightCommand.id,
  SetColWidthCommand.id, DeltaColumnWidthCommand.id,
  // 숨기기·보이기도 크기다(숨김은 크기 0). 안 들으면 숨겨도 저장되지 않아
  // 새로고침에 되살아난다(2026-09-03 지적).
  SetRowHiddenCommand.id, SetSelectedRowsVisibleCommand.id,
  SetSpecificRowsVisibleCommand.id,
  SetColHiddenCommand.id, SetSelectedColsVisibleCommand.id,
  SetSpecificColsVisibleCommand.id,
];

/**
 * 병합을 바꾸는 명령들.
 *
 * <p>합치기는 전체·가로·세로 세 갈래고 풀기는 하나다. 넷 다 잡지 않으면 어떤
 * 메뉴로 합쳤느냐에 따라 저장되기도 하고 안 되기도 한다.
 */
/**
 * 시트 탭을 눌러 다른 시트로 간다.
 *
 * <p>워크북을 다시 그린다. 시트마다 셀·그림·크기·병합을 따로 읽어야 하고, 저장
 * 경로와 실시간 구독도 시트 순번에 묶여 있어서다 - 한 시트만 갈아끼우려면 그
 * 배선을 전부 시트별로 나눠야 하는데, 얻는 것은 다시 그리는 찰나뿐이다.
 *
 * <p>떠나기 전에 저장을 마저 보낸다. 안 그러면 방금 친 값이 사라진다.
 */
async function switchSheet(subUnitId: string) {
  if (!open || subUnitId === SUB_UNIT_ID) {
    return;
  }
  const next = open.sheets.find((sheet) => subUnitIdOf(sheet.sheetSeq) === subUnitId);
  if (!next) {
    return;
  }

  // 화면은 Univer 가 이미 바꿨다. 우리가 하는 일은 "지금 어느 시트인가" 를 옮기고
  // 실시간 구독을 그 시트 토픽으로 다시 여는 것뿐이다 - 워크북을 다시 만들지 않는다.
  // 저장은 시트가 바뀌기 전에 이미 내보냈다(installSheetCommandWatch). 여기서
  // 다시 부르면 새 시트의 크기·병합을 읽어 앞 시트에 적는다.
  releaseCurrentLock();
  disconnect?.();

  open.sheet = next;
  SUB_UNIT_ID = subUnitId;

  // 새 시트 기준으로 "이미 보낸 것" 을 다시 잡는다. 앞 시트의 기록을 들고 있으면
  // 같은 자리의 값이 안 바뀐 것으로 보여 저장이 빠진다.
  lastSent.clear();
  pendingChanges.clear();
  pendingDeletes.clear();
  rememberDrawings(currentDrawings());
  rememberSizes(currentSizes() ?? []);
  rememberMerges(currentMerges() ?? []);

  // 보던 자리는 개인 것이다. 계정을 따라가도록 서버에 담는다(2026-08-13 지적).
  rememberSheet(open.workbookSeq, next.sheetSeq);
  saveViewMemory(open.workbookSeq, next.sheetSeq);
  applySavedZoom(next.sheetSeq);

  status(`${next.sheetName} — 셀 ${currentCellCount()}개`);
  subscribeSheet(next.sheetSeq);
}

/**
 * 이 시트를 지난번에 보던 배율로 되돌린다.
 *
 * <p>기억이 없으면 손대지 않는다 - 문서에 적힌 배율이 그대로 산다.
 */
function applySavedZoom(sheetSeq: number) {
  const zoom = zoomOf(sheetSeq);
  if (zoom == null) {
    return;
  }
  univerAPI.getActiveWorkbook()?.getActiveSheet()?.zoom(zoom);
}

/** 지금 시트에 값이 있는 셀 수. 상태줄에만 쓴다. */
function currentCellCount(): number {
  const sheet = univerAPI.getActiveWorkbook()?.getActiveSheet();
  const snapshot = sheet?.getSheet().getSnapshot() as unknown as
    { cellData?: Record<string, Record<string, unknown>> } | undefined;
  return Object.values(snapshot?.cellData ?? {})
    .reduce((sum, row) => sum + Object.keys(row).length, 0);
}

/**
 * 시트를 더한다.
 *
 * <p>Univer 가 화면에 먼저 만들고 우리는 서버에 남긴다. 서버가 정한 이름·순번으로
 * 다시 열어 맞춘다 - 화면이 지은 이름과 서버가 지은 이름이 다를 수 있고(같은 이름을
 * 서버가 막는다), 시트 순번을 알아야 앞으로의 저장이 그 시트로 간다.
 */
async function addSheet(sheetName?: string) {
  if (!open) return;
  try {
    const added = await api.addSheet(open.workbookSeq, sheetName);
    const sheets = await fetchSheets(open.workbookSeq);
    await openSheet(open.workbookSeq, added, sheets);
  } catch (error) {
    status(messageOf(error));
    await reopenSheets();
  }
}

/**
 * 시트 복사.
 *
 * <p>값만이 아니라 서식·병합·크기·그림·틀고정까지 서버가 함께 옮긴다. 화면에서
 * 베껴 보내면 눈에 보이는 것만 가고 나머지가 조용히 빠진다.
 */
async function copySheet(subUnitId?: string, sheetName?: string) {
  const target = sheetOf(subUnitId) ?? open?.sheet;
  if (!open || !target) return;
  try {
    const made = await api.copySheet(target.sheetSeq, sheetName);
    const sheets = await fetchSheets(open.workbookSeq);
    await openSheet(open.workbookSeq, made, sheets);
    status(`${made.sheetName} 시트를 만들었습니다`);
  } catch (error) {
    status(messageOf(error));
    await reopenSheets();
  }
}

async function removeSheet(subUnitId?: string) {
  const target = sheetOf(subUnitId);
  if (!open || !target) return;
  const { workbookSeq } = open;
  try {
    await api.removeSheet(target.sheetSeq);
    const sheets = await fetchSheets(workbookSeq);
    // 지운 시트를 보고 있었다면 첫 시트로 옮겨 간다.
    const next = sheets.find((sheet) => sheet.sheetSeq === open?.sheet.sheetSeq) ?? sheets[0];
    await openSheet(workbookSeq, next, sheets);
  } catch (error) {
    status(messageOf(error));
    await reopenSheets();
  }
}

/**
 * 탭을 끌어 옮긴 순서를 서버에 남긴다.
 *
 * <p>명령 파라미터의 자리 번호가 아니라 <b>화면의 지금 순서</b>를 읽는다. 명령은
 * "몇 번째로 옮겼다" 만 알려 주는데, 그것을 우리가 다시 재배열하면 Univer 가
 * 실제로 만든 순서와 어긋날 수 있다.
 */
async function reorderSheets() {
  if (!open) return;
  const workbook = univerAPI.getActiveWorkbook();
  if (!workbook) return;

  const bySubUnit = new Map(open.sheets.map((sheet) => [subUnitIdOf(sheet.sheetSeq), sheet]));
  const ordered = workbook.getSheets()
    .map((sheet) => bySubUnit.get(sheet.getSheetId())?.sheetSeq)
    .filter((seq): seq is number => seq != null);

  // 화면이 아는 시트와 서버가 아는 시트가 어긋나면 보내지 않는다. 서버는 빠짐없이
  // 받아야 하고, 일부만 보내면 400 이다.
  if (ordered.length !== open.sheets.length) return;

  // 명령과 뮤테이션이 같은 변경을 두 번 알린다. 이미 그 순서면 보내지 않는다.
  const known = open.sheets.map((sheet) => sheet.sheetSeq);
  if (ordered.every((seq, at) => known[at] === seq)) return;

  try {
    await api.reorderSheets(open.workbookSeq, ordered);
    open.sheets = await fetchSheets(open.workbookSeq);
  } catch (error) {
    status(messageOf(error));
    await reopenSheets();
  }
}

async function renameSheet(subUnitId?: string, name?: string) {
  const target = sheetOf(subUnitId);
  if (!open || !target || !name) return;
  try {
    await api.renameSheet(target.sheetSeq, name);
    open.sheets = await fetchSheets(open.workbookSeq);
  } catch (error) {
    // 서버가 막은 이름이다(빈 이름·중복). 화면은 이미 바꿨으므로 되돌린다.
    status(messageOf(error));
    await reopenSheets();
  }
}

function sheetOf(subUnitId?: string): SheetSummary | undefined {
  if (!open) return undefined;
  const id = subUnitId ?? SUB_UNIT_ID;
  return open.sheets.find((sheet) => subUnitIdOf(sheet.sheetSeq) === id);
}

/** 서버가 아는 대로 다시 그린다. 실패를 되돌리거나 남의 변경을 받을 때 쓴다. */
async function reopenSheets(actorName: string | null = null) {
  if (!open) return;
  const { workbookSeq } = open;
  const sheets = await fetchSheets(workbookSeq).catch(() => [] as SheetSummary[]);
  if (sheets.length === 0) return;

  // 내가 방금 한 변경이 알림으로 돌아온 것이면 다시 그리지 않는다.
  //
  // 워크북을 다시 그리는 일은 공짜가 아니다. Univer 의 자동 높이 계산은 프레임마다
  // 도는데, 그 사이에 시트를 버리면 없어진 워크시트를 붙잡고 터진다
  // ("Cannot destructure property 'rowData' of 'this._worksheetData' as it is null",
  // 2026-08-07 실측). 헛되이 다시 그리지 않는 것이 그 경합을 없애는 길이다.
  if (sameSheetList(open.sheets, sheets)) return;

  if (actorName !== null) {
    status(`${actorLabel(actorName)}이 시트를 바꿨습니다.`);
  }
  const current = sheets.find((sheet) => sheet.sheetSeq === open?.sheet.sheetSeq) ?? sheets[0];
  await openSheet(workbookSeq, current, sheets);
}

/** 시트 목록이 같은가. 순서·이름까지 본다 - 이름만 바뀌어도 탭 줄을 다시 그려야 한다. */
function sameSheetList(a: SheetSummary[], b: SheetSummary[]): boolean {
  return a.length === b.length
      && a.every((sheet, at) => sheet.sheetSeq === b[at].sheetSeq
              && sheet.sheetName === b[at].sheetName);
}

const MERGE_COMMANDS = [
  AddWorksheetMergeCommand.id, AddWorksheetMergeAllCommand.id,
  AddWorksheetMergeHorizontalCommand.id, AddWorksheetMergeVerticalCommand.id,
  RemoveWorksheetMergeCommand.id,
];

/**
 * 글 문서 명령 감시.
 *
 * <p>본문을 바꾸는 명령이 여럿이라 접두사로 짚는다. 하나씩 적으면 빠뜨린다 -
 * 붙여넣기·되돌리기·서식이 모두 다른 이름이다.
 */
/**
 * 고른 글자가 없으면 커서가 놓인 낱말을 잡아 준다.
 *
 * <p>글자를 고르지 않고 색이나 굵게를 누르면 아무 일도 일어나지 않았다. Univer 는
 * 그럴 때 "다음에 칠 글자의 서식" 으로 담아 두는데 그 값이 살아남지 못한다
 * (2026-08-11 실측). 워드처럼 커서가 놓인 낱말에 거는 쪽으로 맞춘다.
 *
 * <p>단추를 누르는 순간(눌리기 전)에 잡아야 한다 - 명령이 돌 때는 이미 늦다.
 */
function installWordPick() {
  document.addEventListener('pointerdown', (event) => {
    if (openDocSeq == null) {
      return;
    }
    const target = event.target as HTMLElement | null;
    const button = target?.closest<HTMLElement>('[data-u-command]');
    const command = button?.getAttribute('data-u-command') ?? '';
    // 글자 서식 단추만이다. 문단 정렬이나 목록은 커서만으로도 걸린다.
    if (!command.startsWith('doc.command.set-inline-format')) {
      return;
    }
    pickWordAtCursor();
  }, true);
}

/** 커서가 낱말 안(또는 끝)에 있으면 그 낱말을 고른 것으로 만든다. */
function pickWordAtCursor() {
  const selection = univer.__getInjector().get(DocSelectionManagerService);
  const ranges = selection.getDocRanges();
  const range = ranges?.[0];
  if (range == null || range.startOffset == null || range.endOffset == null
      || range.startOffset !== range.endOffset) {
    // 이미 고른 글자가 있다. 사용자가 고른 것을 건드리지 않는다.
    return;
  }

  const body = univerAPI.getActiveDocument()?.getDocumentDataModel()?.getSnapshot()?.body;
  if (body?.dataStream == null) {
    return;
  }
  const word = wordRangeAt(body.dataStream, range.startOffset);
  if (word == null) {
    return;
  }
  selection.replaceDocRanges([{ startOffset: word.start, endOffset: word.end }]);
}

function installDocCommandWatch() {
  commandService().onCommandExecuted((command) => {
    // 커서 자리는 문서가 열리기 전에도 온다. 들여쓰기 손잡이가 그 자리를 쓴다.
    if (command.id === SET_SELECTIONS_OPERATION) {
      const ranges = (command.params as
        { ranges?: { startOffset?: number }[] } | undefined)?.ranges;
      const at = ranges?.[0]?.startOffset;
      if (typeof at === 'number') {
        docCaretAt = at;
      }
    }
    if (openDocSeq == null) {
      return;
    }
    if (command.id.startsWith('doc.') || command.id === UndoCommand.id
        || command.id === RedoCommand.id) {
      queueDocSave();
    }
  });
}

/**
 * 시트 명령 감시. 크기·병합·서식 변경을 저장 대기줄에 넣는다.
 *
 * <p>시트에서만 건다 - 글 문서에는 이 명령들이 아예 없다.
 */
function installSheetCommandWatch() {
  // 크기 변화량을 바로잡는다. Univer 는 손잡이 두께의 절반(2px)을 이동 거리에서
  // 빼고 보낸다 - 30px 을 끌면 28px 만 바뀌고, 안 움직여도 -2px 이 나가 경계를
  // 누르기만 해도 줄이 2px 로 주저앉는다(2026-08-18 지적).
  //
  // 실행에 넘어가는 것과 같은 객체라 여기서 고치면 그대로 쓰인다. 0 이 되면
  // 바꿀 것이 없으니 던져서 취소한다.
  commandService().beforeCommandExecuted((command) => {
    const axis = command.id === DeltaRowHeightCommand.id ? 'ROW'
      : command.id === DeltaColumnWidthCommand.id ? 'COLUMN' : null;
    if (axis == null) {
      return;
    }
    const params = command.params as { deltaX?: number; deltaY?: number } | undefined;
    if (params == null) {
      return;
    }
    const key = axis === 'ROW' ? 'deltaY' : 'deltaX';
    const fixed = Math.round(correctedDelta(params[key] ?? 0, renderScale(axis)));
    if (fixed === 0) {
      // 이 갈래만 조용히 취소된다. 다른 오류는 밖으로 다시 던져진다
      // (@univerjs/core CommandService 실측).
      throw new CustomCommandExecutionError('크기를 바꾸지 않았습니다');
    }
    params[key] = fixed;
  });

  // "+" 를 눌렀을 때 Univer 가 제 이름("시트1")으로 탭을 먼저 만들지 못하게 한다.
  //
  // 이름은 서버가 정한다(같은 이름을 막는다). 화면이 먼저 짓고 우리가 다시 열어
  // 고치면 "시트1" 이 잠깐 보였다가 "Sheet12" 로 바뀐다(2026-08-31 지적).
  // 여기서 취소하고 우리 길로만 더한다 - 탭은 서버 이름으로 한 번에 나타난다.
  commandService().beforeCommandExecuted((command) => {
    if (command.id !== InsertSheetCommand.id) {
      return;
    }
    // 부르는 쪽이 이름을 정했으면 그대로 살린다. AI 가 addSheet("2027계획") 을
    // 부르는 길이 이것인데, 이름을 버리면 "Sheet12" 가 만들어진다.
    const params = command.params as { sheet?: { name?: string } } | undefined;
    void addSheet(params?.sheet?.name);
    throw new CustomCommandExecutionError('시트는 서버가 더한다');
  });

  // 탭을 복제할 때도 같은 길로 보낸다. Univer 가 화면에만 복제하면 서버에는
  // 아무것도 안 남아, 새로고침하면 사라진다.
  commandService().beforeCommandExecuted((command) => {
    if (command.id !== CopySheetCommand.id) {
      return;
    }
    const params = command.params as { subUnitId?: string } | undefined;
    void copySheet(params?.subUnitId);
    throw new CustomCommandExecutionError('시트 복사는 서버가 한다');
  });

  // 시트가 바뀌기 전에 아직 안 보낸 것을 내보낸다.
  //
  // Univer 는 시트를 먼저 바꾸고 나서 우리에게 알린다. 알림을 받고 저장하면
  // 새 시트의 행 높이·열 너비·병합을 읽어 앞 시트 번호로 적는다 - 시트를 오갈
  // 때마다 앞 시트의 크기가 뒤 시트 것으로 덮였다(2026-08-18 실측: 시트 이동
  // 한 번에 33줄 갱신, 51줄 삭제). 여기서 부르면 아직 앞 시트가 활성이다.
  //
  // 보내는 것은 기다리지 않는다. 무엇을 보낼지는 이 자리에서 정해진다.
  commandService().beforeCommandExecuted((command) => {
    if (command.id !== SetWorksheetActiveOperation.id) {
      return;
    }
    void flushSave();
    void flushDrawingSave();
    void flushSizeSave();
    void flushMergeSave();
  });

  commandService().onCommandExecuted((command) => {
    if (SIZE_COMMANDS.includes(command.id)) {
      queueSizeSave();
      showChangedSize(command.id, command.params);
    }
    if (MERGE_COMMANDS.includes(command.id)) {
      queueMergeSave();
    }
    // 되돌리기(Ctrl+Z)로 바뀐 셀은 SheetValueChanged 가 알려 주지 않는다. 그 이벤트는
    // 명령 목록을 보고 나오는데 UndoCommand 는 그 목록에 없다 - 화면에서는 지워지고
    // 서버에는 남아, 새로고침하면 되살아났다(2026-08-07 실측).
    //
    // 값이 실제로 바뀌는 자리인 뮤테이션을 직접 듣는다. 보통 편집에서는 두 경로가
    // 겹치는데 unchanged 가 두 번째를 걸러 낸다.
    if (command.id === SetRangeValuesMutation.id) {
      const params = command.params as
        { cellValue?: Record<string, Record<string, unknown>>; subUnitId?: string } | undefined;
      queueSaveForChangedCells(params?.cellValue, params?.subUnitId);
    }
    // 행·열을 지우거나 끼우면 값이 통째로 밀린다. 그 이동은 위 뮤테이션으로 오지
    // 않아 화면만 줄고 서버는 그대로였다(2026-08-27 실측: 340줄에서 두 줄을 지워도
    // 서버가 안 바뀌었다). 밀린 자리를 견주어 다시 보낸다.
    if (MOVING_MUTATIONS.includes(command.id)) {
      queueMovedSave();
    }
    // 지운 줄이 그대로 골라져 있으면 다음 조작이 엉뚱한 줄에 걸린다. 그 자리
    // 한 칸만 남긴다 - 엑셀도 그렇게 한다.
    const spot = spotAfterRemove(command.id, command.params as never);
    if (spot != null) {
      const sheet = univerAPI.getActiveWorkbook()?.getActiveSheet() as unknown as {
        getRange?: (row: number, column: number) => { activate?: () => unknown };
        scrollToCell?: (row: number, column: number) => unknown;
      } | undefined;
      sheet?.getRange?.(spot.row, spot.column)?.activate?.();
      // 지운 자리가 화면 밖이면 어디가 지워졌는지 알 수 없다. 그 자리로 옮겨 준다.
      sheet?.scrollToCell?.(spot.row, spot.column);
    }
    // 눈금선은 Univer 가 오른쪽 아래에 이미 단추를 갖고 있다. 우리가 하나 더 만들면
    // 같은 일을 하는 것이 둘이 되고 서로 어긋난다(2026-08-10 지적). 저 단추가 바꾼
    // 것을 서버에 남기기만 한다.
    //
    // 명령이 아니라 뮤테이션을 듣는다. 되돌리기(Ctrl+Z)도 여기를 지나간다 - 명령만
    // 들으면 되돌린 것이 화면에만 남고 서버에는 안 남는다.
    // Univer 도 머리글 모서리에 틀 고정 손잡이를 갖고 있다. 그것으로 바꾼 것도
    // 남겨야 한다 - 메뉴로 한 것만 남으면 손잡이로 한 것은 새로고침에 풀린다.
    // 필터·조건부 서식은 셀 값이 아니라 리소스에 담긴다. 명령 이름으로 짚는다 -
    // 저쪽이 거는 뮤테이션이 여럿이라 하나씩 적으면 빠뜨린다. 판올림에 이름이 바뀌면
    // 저장이 안 될 뿐 화면은 그대로 돈다.
    if (command.id.includes('filter') || command.id.includes('conditional')
        || command.id.includes('data-validation') || command.id.includes('validation')
        || command.id.includes('hyper-link') || command.id.includes('note')
        || command.id.includes('comment') || command.id.includes('table')) {
      queueResourceSave();
    }
    if (command.id.includes('filter')) {
      // 걸러 보기 자리는 시트에도 담는다. 리소스에만 두면 내려받은 파일에
      // 안 남는다(2026-08-26).
      saveFilterRange();
    }
    if (command.id === SetFrozenMutation.id) {
      saveFreeze(command.params as
        { subUnitId?: string; ySplit?: number; xSplit?: number } | undefined);
    }
    if (command.id === ToggleGridlinesMutation.id) {
      saveGridlines(command.params as
        { subUnitId?: string; showGridlines?: number } | undefined);
    }
    if (command.id === SetTabColorMutation.id) {
      saveTabColor(command.params as
        { subUnitId?: string; color?: string } | undefined);
    }
    if (command.id === RemoveSheetCommand.id) {
      void removeSheet((command.params as { subUnitId?: string } | undefined)?.subUnitId);
    }
    // 명령과 뮤테이션을 함께 듣는다. UI 가 어느 경로로 옮기든(끌기·메뉴) 실제
    // 변경은 뮤테이션에서 일어난다 - 삭제가 RemoveSheetCommand 가 아니라 확인
    // 대화상자를 거치는 다른 명령이었던 것과 같은 함정을 피한다(2026-08-07).
    if (command.id === SetWorksheetOrderCommand.id
        || command.id === SetWorksheetOrderMutation.id) {
      void reorderSheets();
    }
    if (command.id === SetWorksheetNameCommand.id) {
      const params = command.params as { subUnitId?: string; name?: string } | undefined;
      void renameSheet(params?.subUnitId, params?.name);
    }
    if (command.id === SetWorksheetActiveOperation.id) {
      const target = (command.params as { subUnitId?: string } | undefined)?.subUnitId;
      if (target) {
        void switchSheet(target);
      }
    }
    // 숫자 서식은 값 변경으로 치지 않아 SheetValueChanged 가 뜨지 않는다. 그래서
    // 서식만 바꾸면 다음 편집이 있을 때까지 저장되지 않았다(2026-08-06 실측).
    if (command.id === SetNumfmtCommand.id) {
      queueStyleSave(command.params as { range?: IRange } | undefined);
    }

    // 배경색 버튼에 지금 색을 표시한다. Univer 는 글자색 아이콘만 고른 색으로
    // 칠하고 배경색 아이콘에는 그런 자리가 없다 - 무슨 색을 고르는지 눌러 보기
    // 전에는 알 수 없다(2026-08-06 지적).
    if (command.id === SetBackgroundColorCommand.id) {
      const color = (command.params as { value?: string } | undefined)?.value;
      showFillColor(color ?? null);
    }
    if (command.id === ResetBackgroundColorCommand.id) {
      showFillColor(null);
    }
  });
}

/**
 * 방금 바꾼 행 높이·열 너비를 픽셀로 알린다.
 *
 * <p>끌어서 맞출 때 지금 몇 픽셀인지 볼 곳이 없었다(2026-08-07 지적). 엑셀과 견줄
 * 때도, 여러 열을 같은 폭으로 맞출 때도 숫자가 있어야 한다.
 *
 * <p>끄는 중이 아니라 <b>놓은 뒤</b>에 뜬다. Univer 는 끄는 동안의 값을 명령으로
 * 내보내지 않는다 - 그것까지 따라가려면 그리기 계층에 손을 넣어야 한다.
 */
function showChangedSize(commandId: string, params: unknown) {
  const sheet = univerAPI.getActiveWorkbook()?.getActiveSheet();
  if (!sheet) return;

  const row = commandId === SetRowHeightCommand.id || commandId === DeltaRowHeightCommand.id;
  const at = params as {
    anchorRow?: number; anchorCol?: number; value?: number;
    ranges?: { startRow: number; startColumn: number }[];
  } | undefined;

  // 끌어서 바꾸면 anchor 를, 메뉴로 값을 넣으면 ranges 를 준다.
  const index = row
    ? at?.anchorRow ?? at?.ranges?.[0]?.startRow
    : at?.anchorCol ?? at?.ranges?.[0]?.startColumn;
  if (index == null) return;

  const size = row ? sheet.getRowHeight(index) : sheet.getColumnWidth(index);
  status(`${row ? '행 높이' : '열 너비'} ${Math.round(size)}px`);
}

/** 배경색 버튼 밑줄. 색이 없으면 줄도 없앤다 - 회색 줄은 회색을 고른 것처럼 보인다. */
function showFillColor(color: string | null) {
  document.documentElement.style.setProperty('--wo-fill-color', color ?? 'transparent');
}

/**
 * 서식만 바뀐 범위를 저장 큐에 넣는다.
 *
 * <p>값이 그대로라 SheetValueChanged 를 기다릴 수 없다. 바뀐 범위를 직접 읽어
 * 셀마다 DTO 를 만든다.
 */
function queueStyleSave(params: { range?: IRange } | undefined) {
  const sheet = univerAPI.getActiveWorkbook()?.getActiveSheet();
  const range = params?.range;
  if (!sheet || !range) return;

  for (let rowNo = range.startRow; rowNo <= range.endRow; rowNo += 1) {
    for (let columnNo = range.startColumn; columnNo <= range.endColumn; columnNo += 1) {
      const cell = sheet.getRange(rowNo, columnNo).getCellData() as UniverCell | null;
      if (!cell) continue;
      const resolved = withResolvedStyle(cell);
      remember(rowNo, columnNo, resolved);
      const [dto] = fromUniverCellData({ [rowNo]: { [columnNo]: resolved } }, '', []).cells;
      queueSave(dto);
    }
  }
}

/**
 * 셀이 바뀌면 저장한다.
 *
 * <p>내 자리(presence) 범위로 거르지 않는다. 잠금이 강제였을 때의 잔재인데, 그
 * 때문에 엑셀에서 여러 칸을 붙여넣으면 자리에 든 한 칸만 남고 나머지가 조용히
 * 버려졌다(2026-08-06 지적). 자리는 이제 "여기 사람이 있다" 는 표시일 뿐이라
 * 저장을 막을 근거가 없다.
 */

/**
 * 뮤테이션이 손댄 자리를 화면에서 다시 읽어 저장 큐에 넣는다.
 *
 * <p>뮤테이션이 실어 온 셀을 그대로 쓰지 않는다. 그것은 부분일 수 있고(서식만 바꾼
 * 뮤테이션은 값이 없다) 되돌리기에서는 이전 값이 실린다 - 화면의 최종 모습이 정답이다.
 *
 * <p>빈 셀은 {@code {}} 로 보낸다. 지운 것도 저장되어야 한다.
 *
 * <p>어느 시트의 변경인지({@code subUnitId})를 반드시 본다. 안 보면 다른 시트의
 * 변경을 지금 시트에서 읽어 저장한다 - 새 시트를 더했더니 빈 시트에 남의 값이
 * 들어갔다(2026-08-31 실측).
 */
function queueSaveForChangedCells(cellValue?: Record<string, Record<string, unknown>>,
  subUnitId?: string) {
  const sheet = univerAPI.getActiveWorkbook()?.getActiveSheet();
  if (applyingRemote || !cellValue || !sheet) return;
  if (subUnitId != null && subUnitId !== SUB_UNIT_ID) return;

  for (const [row, columns] of Object.entries(cellValue)) {
    for (const column of Object.keys(columns)) {
      const rowNo = Number(row);
      const columnNo = Number(column);
      const cell = (sheet.getRange(rowNo, columnNo).getCellData() as UniverCell | null) ?? {};
      const resolved = withResolvedStyle(cell);
      if (unchanged(rowNo, columnNo, resolved)) continue;

      remember(rowNo, columnNo, resolved);
      const [dto] = fromUniverCellData({ [rowNo]: { [columnNo]: resolved } }, '', []).cells;
      queueSave(dto);
    }
  }
}

/** 잠근 범위 밖은 보내지 않는다. 서버도 막지만 왕복을 낭비할 이유가 없다. */
function unchanged(rowNo: number, columnNo: number, cell: UniverCell): boolean {
  return lastSent.get(`${rowNo}:${columnNo}`) === JSON.stringify(cell);
}

function remember(rowNo: number, columnNo: number, cell: UniverCell) {
  lastSent.set(`${rowNo}:${columnNo}`, JSON.stringify(cell));
}

// ---------------------------------------------------------------- 보조

function sameRange(a: Range, b: Range): boolean {
  return a.fromRowNo === b.fromRowNo && a.fromColumnNo === b.fromColumnNo
      && a.toRowNo === b.toRowNo && a.toColumnNo === b.toColumnNo;
}

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : '알 수 없는 오류가 발생했습니다.';
}

// ---------------------------------------------------------------- 진입

const params = new URLSearchParams(location.search);
const workbookSeqParam = params.get('workbookSeq');
if (workbookSeqParam) {
  // 인증은 세션 쿠키다. 토큰 파라미터를 읽지 않는다 - 세션이 없으면 서버가
  // 이 화면을 열기 전에 /login 으로 보낸다.
  void (async () => {
    try {
      const workbookSeq = Number(workbookSeqParam);
      // 무엇을 열지는 문서가 안다. 시트 개수로 짐작하면 빈 문서에서 갈래가 없다.
      const summary = await api.getWorkbook(workbookSeq);
      // 종류를 안 뒤에 편집기를 만든다. 시트용으로 만들어 두고 나중에 갈아 끼우면
      // 시트-ui 가 사라진 워크북을 붙잡는다(2026-08-11 실측).
      // 발표 자료는 편집기를 만들지 않는다. 보기만 하는 화면이라 화면 엔진이
      // 할 일이 없다(2026-08-13 결정).
      if (summary.docType === 'SLIDE') {
        await openSlides(workbookSeq);
        return;
      }
      startEditor(summary.docType === 'DOC' ? 'DOC' : 'SHEET');
      if (summary.docType === 'DOC') {
        await openDoc(workbookSeq, summary.workbookName);
        if (params.get('from') === 'hwp') {
          notice('한글 문서를 가져왔습니다. 글자와 굵게·기울임·밑줄·취소선, 문단 정렬을'
            + ' 옮겼습니다. 표는 칸 안의 글만 문단으로 남고 칸 모양은 사라지며,'
            + ' 그림·글상자는 빠집니다. 내려받기는 docx 입니다.');
        }
        return;
      }
      const sheets = await fetchSheets(workbookSeq);
      if (sheets.length > 0) {
        await loadViewMemory(workbookSeq);
        await openSheet(workbookSeq, seenSheet(workbookSeq, sheets), sheets);
      }
    } catch (error) {
      // 못 여는 문서다(지웠거나 권한이 없다). 빈 화면에 문구만 남기지 않고
      // 목록으로 돌려보낸다 - 그래야 다음에 무엇을 할지 보인다.
      status(messageOf(error));
      void showDocumentList();
    }
  })();
} else {
  void showDocumentList();
}

/**
 * 휴지통 비우기.
 *
 * <p>다 지울 때까지 기다리지 않는다. 휴지통 뒤에는 셀이 수백만 줄 딸려 있어
 * 기다리면 화면이 멈춘 것으로 보인다(2026-09-04 지적). 시작만 알리고 얼마나
 * 갔는지 물어보며 알려 준다.
 */
async function purgeTrash(keepDays: number) {
  try {
    const total = await api.emptyTrash(keepDays);
    if (total === 0) {
      status('휴지통에서 지울 것이 없습니다');
      return;
    }
    status(`휴지통을 비우는 중… 0/${total}`);
    await watchTrashPurge();
  } catch (error) {
    status(messageOf(error));
  }
}

/** 얼마나 갔는지 물어보며 기다린다. 끝나면 목록을 다시 읽는다. */
async function watchTrashPurge() {
  for (;;) {
    await new Promise((wake) => { window.setTimeout(wake, TRASH_PURGE_ASK_MS); });
    const now = await api.trashPurgeProgress();
    if (!now.running) {
      status(now.message ?? '휴지통을 비웠습니다');
      await showDocumentList();
      return;
    }
    status(`휴지통을 비우는 중… ${now.done}/${now.total}`);
  }
}

/** 진행을 물어보는 사이. 너무 잦으면 지우는 일 자체가 느려진다. */
const TRASH_PURGE_ASK_MS = 1000;

/**
 * 문서 목록 화면.
 *
 * <p>문서를 고르지 않고 들어오면 빈 그리드만 떴다. 주소를 잃으면 문서를 다시
 * 찾을 방법이 없었다.
 */
async function showDocumentList() {
  showPageTitle(null);
  showDocKind(null);
  try {
    // 휴지통은 있으면 좋고 없어도 되는 곁가지다. 못 읽어도 목록은 뜬다.
    const [workbooks, trash, colorLabels] = await Promise.all([
      api.listWorkbooks(),
      api.listTrash().catch(() => [] as TrashItem[]),
      // 색 이름도 곁가지다. 못 읽으면 기본 색 이름으로 부른다.
      api.listColorLabels().catch(() => [] as ColorLabel[]),
    ]);
    renderDocumentList({
      container: documentListEl,
      workbooks,
      trash,
      onRestore: (workbookSeq, workbookName) => {
        void api.restoreWorkbook(workbookSeq)
          .then(() => {
            status(`${workbookName} 문서를 되살렸습니다`);
            return showDocumentList();
          })
          .catch((error) => status(messageOf(error)));
      },
      // 주소를 바꿔 들어간다. 그래야 새로고침·즐겨찾기가 같은 문서를 연다.
      onOpen: (workbookSeq) => { location.href = `/?workbookSeq=${workbookSeq}`; },
      onSearch: (words) => api.searchWorkbooks(words),
      onUpload: () => fileInput.click(),
      onUploadDoc: () => docInput.click(),
      onUploadSlide: () => slideInput.click(),
      onView: () => viewInput.click(),
      onDelete: (workbookSeq, workbookName) => {
        void api.deleteWorkbook(workbookSeq)
          // 지운 뒤 목록을 다시 읽는다. 화면에서만 지우면 실패한 삭제가 성공처럼 보인다.
          .then(() => { status(`${workbookName} 문서를 지웠습니다`); return showDocumentList(); })
          .catch((error) => status(messageOf(error)));
      },
      onCreate: createWorkbookAndOpen,
      // 비우기는 되돌릴 수 없다. 화면이 두 번 묻고 나서야 여기까지 온다.
      onPurgeTrash: (keepDays) => { void purgeTrash(keepDays); },
      colorLabels,
      // 화면은 이미 바뀐 모양을 그렸다. 실패하면 그것만 알린다 - 목록을 통째로
      // 다시 그리면 누르던 자리가 사라진다.
      onMark: (workbookSeq, starred, color) => {
        const found = [...workbooks.mine, ...workbooks.shared]
          .find((one) => one.workbookSeq === workbookSeq);
        if (found) {
          found.starred = starred;
          found.color = color;
        }
        void api.markWorkbook(workbookSeq, starred, color)
          .catch((error) => status(messageOf(error)));
      },
      onRenameColor: (color, name) => {
        void api.renameColorLabel(color, name)
          .then(() => showDocumentList())
          .catch((error) => status(messageOf(error)));
      },
    });
    documentListEl.hidden = false;
    // Univer 의 탭 줄·리본은 #app 안에 있다. 숨기지 않으면 목록 위에 그것들이
    // 그대로 남아 편집 화면처럼 보인다(2026-08-06 실측).
    appEl.hidden = true;
    // 파일 메뉴는 열린 문서에 대고 쓰는 것이다. 목록 화면에서는 "열기" 도
    // "내려받기" 도 대상이 없다. 시작 타일이 그 일을 대신한다.
    toolbarLeft.hidden = true;
    // 제목은 가운데 절대 위치라 toolbarLeft 를 숨겨도 남는다. 열린 문서가 없으면
    // 자리표시자 "제목" 만 떠서 무엇의 제목인지 알 수 없다.
    workbookTitleEl.hidden = true;
  } catch (error) {
    status(messageOf(error));
  }
}

/**
 * 글 문서를 연다.
 *
 * <p>본문 저장은 아직 없다(1b). 지금은 빈 글로 열어 편집기가 뜨는 데까지다 -
 * 저장 없이 내보내면 고친 것이 사라지므로 화면에 그렇다고 적는다.
 */
async function openDoc(workbookSeq: number, workbookName: string) {
  // 같은 이름으로 두 번 만들 수 없다("cannot create a unit with the same unit id",
  // 2026-08-26 실측). 다시 열 때는 먼저 있던 것을 치운다 - 시트도 같은 길이다.
  const shown = univerAPI.getActiveDocument();
  if (shown != null) {
    univerAPI.disposeUnit(shown.getId());
  }
  documentListEl.hidden = true;
  appEl.hidden = false;
  toolbarLeft.hidden = false;
  workbookTitleEl.hidden = false;

  // 실패해도 문서는 연다. 빈 글로 여는 것과 못 여는 것은 무게가 다르다.
  const saved = await api.readParagraphs(workbookSeq).catch(() => [] as DocParagraph[]);

  // 문서 한 벌이 남아 있으면 그것으로 연다. 표·그림은 문단 표에 담기지 않으므로
  // 이 길이라야 되살아난다. 없으면(옛 문서다) 문단으로 짓는다.
  const images = await readImportImages(workbookSeq);
  const page = await readImportPage(workbookSeq);
  const snapshot = await readDocSnapshot(workbookSeq);
  if (snapshot != null) {
    // 빈 그림 리소스를 담은 채 저장된 옛 문서를 되살린다. 까닭은
    // docDrawingResource 에 적었다.
    univerAPI.createDocument(withFirstPageEdge({
      ...repairDrawingResource(snapshot), id: `wo-doc-${workbookSeq}`,
    }) as never);
  } else {
  // 가져온 표가 있으면 표까지 세워 짓는다. 없으면 문단만으로 짓는다.
  // 표 얼개에 빠진 것이 있으면 Univer 가 문서를 아예 못 그린다. 그럴 때는 표를
  // 접고 문단만 세운다 - 글까지 잃는 것보다 낫다.
  const tables = TABLE_ASSEMBLY ? await readImportTables(workbookSeq) : [];
  // 표는 원본 종이의 본문 폭에 맞춘다. 우리 기본 폭(A4)으로 재면 레터 문서의
  // 표가 좁게 나온다(2026-08-13 실측: 원본 표 719px, 우리 606px).
  const style = pageStyle(page);
  const bodyWidthPx = style.pageSize.width - style.marginLeft - style.marginRight;
  // 글꼴과 줄 높이를 원본대로 잡는다. 그러지 않으면 쪽 나눔이 원본과 어긋난다
  // (2026-08-25 지적: 24쪽 문서가 화면에서 25쪽).
  const basics = await readImportDefaults(workbookSeq);
  const defaultFont = basics?.font ?? null;
  const measured = shrinkBeforePageBreak(
    withLineHeights(saved, defaultFont, fontLineRatio, basics?.fontSize),
    new Set([...tables.map((one) => one.afterParagraphNo),
      ...images.map((one) => one.afterParagraphNo)]));
  // 종이보다 큰 표지 배경은 종이 안에 넣는다. 워드는 밖을 잘라 그린다.
  const fitted = fitFloating(images, style.pageSize.width, style.pageSize.height,
    style.marginLeft, style.marginTop);
  const body = toBodyWithTables(measured, tables, fitted, `wo-doc-${workbookSeq}`,
    bodyWidthPx, '', defaultFont);
  // 실측용 창구. 쪽마다 첫 글과 줄 높이를 꺼내 원본과 견준다(2026-08-25).
  (window as unknown as Record<string, unknown>).__woSkeleton = () => {
    const render = univer.__getInjector().get(IRenderManagerService)
      .getRenderUnitById(`wo-doc-${workbookSeq}`);
    const paper = render?.mainComponent as unknown as {
      getSkeleton?: () => { getSkeletonData?: () => { pages?: unknown[] } };
    } | undefined;
    return paper?.getSkeleton?.()?.getSkeletonData?.()?.pages ?? [];
  };
  // 쪽마다 되풀이되는 글. 없으면 빈 채로 둔다.
  const headerEdge = await readImportEdge(workbookSeq, IMPORT_HEADER);
  const footerEdge = await readImportEdge(workbookSeq, IMPORT_FOOTER);
  const headers = edgeUnit(headerEdge, 'header', `wo-doc-${workbookSeq}`, bodyWidthPx);
  const footers = edgeUnit(footerEdge, 'footer', `wo-doc-${workbookSeq}`, bodyWidthPx);
  // 첫 쪽만 다른 문서가 있다. 이 문서는 첫 쪽에 아무것도 안 찍는다 - 빈 머릿글을
  // 세워 첫 쪽을 비운다(2026-08-13 지적: 원본 첫 쪽에는 머릿글이 없다).
  const titlePage = await readImportFlag(workbookSeq, IMPORT_TITLE_PAGE);
  const firstHeaderEdge = titlePage
    ? (await readImportEdge(workbookSeq, IMPORT_FIRST_HEADER)) ?? EMPTY_EDGE : null;
  const firstFooterEdge = titlePage
    ? (await readImportEdge(workbookSeq, IMPORT_FIRST_FOOTER)) ?? EMPTY_EDGE : null;
  const firstHeaders = edgeUnit(firstHeaderEdge, 'first-header',
    `wo-doc-${workbookSeq}`, bodyWidthPx);
  const firstFooters = edgeUnit(firstFooterEdge, 'first-footer',
    `wo-doc-${workbookSeq}`, bodyWidthPx);
  univerAPI.createDocument(withFirstPageEdge({
    id: `wo-doc-${workbookSeq}`,
    body: body as never,
    documentStyle: {
      ...style,
      // 문서 기본 글자. 안 주면 화면이 제 기본(11pt Arial)을 쓰고 툴바에도 그렇게
      // 뜬다 - 원본이 10pt 인 문서가 11pt 로 보였다(2026-08-26 지적).
      textStyle: {
        fs: basics?.fontSize ?? 10,
        ...(defaultFont ? { ff: defaultFont } : {}),
      },
      ...(headers ? { defaultHeaderId: headers.id } : {}),
      ...(footers ? { defaultFooterId: footers.id } : {}),
      ...(titlePage ? { useFirstPageHeaderFooter: 1 } : {}),
      ...(firstHeaders ? { firstPageHeaderId: firstHeaders.id } : {}),
      ...(firstFooters ? { firstPageFooterId: firstFooters.id } : {}),
    },
    // 표를 넣으려면 담을 자리가 미리 있어야 한다. 없으면 명령이 조용히 실패하고
    // "Table not found when creating table skeleton" 만 남는다(2026-08-11 실측).
    tableSource: (body.tableSource ?? {}) as never,
    // 그림도 같다. 본문에 자리(\b)만 있고 얼개가 없으면 아무것도 안 그려진다.
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
    // 목록 모양. 기본을 그대로 쓰면 글머리표가 본문보다 커서 줄이 높아지고
    // 들여쓰기가 얇다 - 워드에 맞춘 것을 실어 보낸다.
    lists: docLists() as never,
    headers: { ...(headers?.map ?? {}), ...(firstHeaders?.map ?? {}) } as never,
    footers: { ...(footers?.map ?? {}), ...(firstFooters?.map ?? {}) } as never,
  }) as never);
  }
  // 방금 읽은 것을 "이미 보낸 것" 으로 기록한다. 이러지 않으면 문서를 여는 것만으로
  // 전 문단이 저장되고 변경 이력이 열어본 횟수만큼 쌓인다 - 셀에서 겪은 함정이다.
  sentParagraphs.clear();
  for (const row of saved) {
    sentParagraphs.set(row.paragraphNo, signatureOf(row));
  }
  openDocSeq = workbookSeq;
  // 유닛을 캔버스에 붙인다. 까닭은 showUnit 에 적었다. 그림 등록도 그 뒤라야
  // 한다 - 붙기 전에 알리면 받을 화면이 없어 그림만 조용히 빠진다(2026-10-16
  // 실측: 붙여넣은 그림이 다시 열면 사라졌다).
  void showUnit(univer, `wo-doc-${workbookSeq}`)
    .then(() => showImportedImages(`wo-doc-${workbookSeq}`));
  installDocRuler(`wo-doc-${workbookSeq}`);

  void showTitle(workbookSeq);
  showPageTitle(workbookName);
  status(`문서 — 문단 ${saved.length}개`);
}

/**
 * 가져온 그림을 화면에 건다.
 *
 * <p>문서를 세울 때 그림 얼개를 함께 넘겨도 화면은 그것을 바로 그리지 않는다 -
 * 그림 살림꾼에 알려 줘야 그린다(2026-08-12 실측: 얼개는 있는데 그림 주소를 부르지도
 * 않았다). 문서가 다 선 뒤에 한 번 알린다.
 */
function showImportedImages(unitId: string) {
  const snapshot = univerAPI.getActiveDocument()?.getDocumentDataModel()?.getSnapshot() as unknown as {
    drawings?: Record<string, { drawingId?: string; subUnitId?: string }>;
  } | undefined;
  const drawings = snapshot?.drawings ?? {};
  const ids = Object.keys(drawings);
  if (ids.length === 0) {
    return;
  }
  const manager = univer.__getInjector().get(IDrawingManagerService);
  manager.registerDrawingData(unitId, { [unitId]: { unitId, subUnitId: unitId,
    data: drawings as never, order: ids } } as never);
  manager.addNotification(ids.map((drawingId) => ({
    unitId, subUnitId: unitId, drawingId,
  })));
}

/** 지금 열려 있는 글 문서. 시트와 달리 open 을 쓰지 않아 따로 둔다. */
let openDocSeq: number | null = null;

/** 마지막으로 서버에 보낸 문단. 같은 것을 다시 보내지 않는다. */
const sentParagraphs = new Map<number, string>();

let docSaveTimer: ReturnType<typeof setTimeout> | null = null;

/**
 * 한 문단이 같은지 견줄 지문. 셋 다 같으면 보내지 않는다.
 *
 * <p>JSON 은 다시 만들어 견준다. 서버가 돌려준 글자와 우리가 만든 글자는 빈칸
 * 자리가 달라 그대로 견주면 늘 다르게 나온다 - 문서를 여는 것만으로 서식 있는
 * 문단이 저장됐다(2026-08-11 실측).
 */
function signatureOf(row: DocParagraph): string {
  return [row.text, normalizeJson(row.style), normalizeJson(row.runs)].join(' ');
}

/** 못 읽는 값은 그대로 둔다. 지문을 만들다 문서 저장이 멈추면 안 된다. */
function normalizeJson(value: string | null): string {
  if (!value) return '';
  try {
    return JSON.stringify(JSON.parse(value));
  } catch {
    return value;
  }
}

/**
 * 글 본문을 서버에 남긴다.
 *
 * <p>바뀐 문단과 사라진 문단만 보낸다. 통째로 보내면 글이 길어질수록 저장이
 * 무거워지고, 둘이 동시에 고칠 때 나중 것이 앞선 것을 덮는다.
 *
 * <p>치는 동안 명령이 글자마다 오므로 모아서 한 번만 보낸다. 셀 저장과 같은 방식이다.
 */
function queueDocSave() {
  if (openDocSeq == null) {
    return;
  }
  if (docSaveTimer !== null) {
    clearTimeout(docSaveTimer);
  }
  docSaveTimer = setTimeout(() => {
    docSaveTimer = null;
    const workbookSeq = openDocSeq;
    if (workbookSeq == null) return;

    const body = univerAPI.getActiveDocument()?.getDocumentDataModel()?.getSnapshot()?.body;
    if (!body) return;

    const rows = toParagraphs(body as never);
    const changed = rows.filter((row) => sentParagraphs.get(row.paragraphNo) !== signatureOf(row));
    // 글이 짧아지면 뒤쪽 문단이 사라진다. 남겨 두면 다음에 열 때 되살아난다.
    const deleted = [...sentParagraphs.keys()].filter((no) => no >= rows.length);

    // 한 벌은 문단과 상관없이 담는다. 그림·표는 문단 글을 안 바꾸므로, 문단
    // 차이가 없다고 여기서 돌아 나가면 붙여넣은 그림이 저장되지 않는다
    // (2026-10-16 실측: 그림이 다시 열면 사라졌다).
    saveDocSnapshot(workbookSeq);

    if (changed.length === 0 && deleted.length === 0) {
      return;
    }
    for (const row of changed) {
      sentParagraphs.set(row.paragraphNo, signatureOf(row));
    }
    for (const no of deleted) {
      sentParagraphs.delete(no);
    }

    void api.saveParagraphs(workbookSeq, changed, deleted)
      .then(() => {
        status(`저장했습니다 — 문단 ${rows.length}개`);
        refreshVersionsSoon();
      })
      .catch((error) => status(messageOf(error)));
  }, 800);
}

/**
 * 저장해 둔 문서 한 벌을 읽는다.
 *
 * <p>실패하면 없는 것으로 본다. 문단으로도 문서를 지을 수 있으니 화면은 열린다.
 */
/** 가져오기가 남긴 표 얼개. 없으면 빈 목록이다. */
async function readImportTables(workbookSeq: number): Promise<DocTable[]> {
  const resources = await api.readResources(workbookSeq).catch(() => []);
  const saved = resources.find((one) => one.name === IMPORT_TABLES);
  if (saved?.data == null || saved.data.length === 0) {
    return [];
  }
  try {
    return JSON.parse(saved.data) as DocTable[];
  } catch {
    return [];
  }
}

/**
 * 가져온 문서의 종이 크기와 여백.
 *
 * <p>없으면 우리 기본(A4)을 쓴다. 여백이 다르면 본문 폭이 달라져 표와 그림이
 * 원본과 다른 자리에 앉는다(2026-08-13 지적: 표 크기가 다르다).
 */
/**
 * 가져온 문서의 기본 서식. 없으면 없다.
 *
 * <p>글꼴을 안 적은 글은 이 글꼴로 그린다. 모르면 Univer 기본으로 그려 줄이
 * 접히는 자리가 원본과 달라진다(2026-08-25 지적).
 */
async function readImportDefaults(workbookSeq: number):
Promise<{ font: string | null; fontSize: number } | null> {
  const resources = await api.readResources(workbookSeq).catch(() => []);
  const saved = resources.find((one) => one.name === IMPORT_DEFAULTS);
  if (saved?.data == null || saved.data.length === 0) {
    return null;
  }
  try {
    return JSON.parse(saved.data) as { font: string | null; fontSize: number };
  } catch {
    return null;
  }
}

async function readImportPage(workbookSeq: number): Promise<DocPageSetup | null> {
  const resources = await api.readResources(workbookSeq).catch(() => []);
  const saved = resources.find((one) => one.name === IMPORT_PAGE);
  if (saved?.data == null || saved.data.length === 0) {
    return null;
  }
  try {
    return JSON.parse(saved.data) as DocPageSetup;
  } catch {
    return null;
  }
}

/** 아무것도 없는 머릿글 한 벌. 첫 쪽을 비울 때 쓴다. */
const EMPTY_EDGE: DocEdge = { paragraphs: [], tables: [] };

/** 켜짐 표시 하나를 읽는다. 없으면 꺼진 것이다. */
async function readImportFlag(workbookSeq: number, name: string): Promise<boolean> {
  const resources = await api.readResources(workbookSeq).catch(() => []);
  return resources.some((one) => one.name === name && one.data === 'true');
}

async function readImportEdge(workbookSeq: number,
  name: string): Promise<DocEdge | null> {
  const resources = await api.readResources(workbookSeq).catch(() => []);
  const saved = resources.find((one) => one.name === name);
  if (saved?.data == null || saved.data.length === 0) {
    return null;
  }
  try {
    return JSON.parse(saved.data) as DocEdge;
  } catch {
    return null;
  }
}

/**
 * 머릿글·꼬릿글 한 벌을 Univer 모양으로 짓는다.
 *
 * <p>본문과 같은 길로 짓는다 - 표도 그대로 선다. 글이 없으면 아무것도 안 짓는다.
 */
function edgeUnit(edge: DocEdge | null, kind: EdgeKind, unitId: string,
  bodyWidth: number): EdgeUnit | null {
  // 빈 벌도 세운다. 첫 쪽을 비우려면 "빈 머릿글" 이 있어야 한다 - 없으면 화면이
  // 쪽마다 쓰는 머릿글을 첫 쪽에도 찍는다.
  if (edge == null) {
    return null;
  }
  const id = `${unitId}-${kind}`;
  // 머릿글 표는 자리만 잡히고 그려지지 않는다(Univer 0.25.1 실측: 표 높이만큼
  // 본문이 밀리고 화면에는 아무것도 안 나왔다). 표 글은 줄로 펴서 적는다.
  // 그림(로고)은 얼개에 실어야 그려진다(2026-08-25 지적).
  const body = edgeBody(edge, id, bodyWidth, null,
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

/** 어느 자리에 쓰는 벌인가. */
type EdgeKind = 'header' | 'footer' | 'first-header' | 'first-footer';

/** 종이 크기와 여백(px). */
interface DocPageSetup {
  width: number;
  height: number;
  marginLeft: number;
  marginRight: number;
  marginTop: number;
  marginBottom: number;
}

/** 가져온 그림 자리. 알맹이는 그림 저장소에 있고 여기에는 주소만 있다. */
async function readImportImages(workbookSeq: number): Promise<DocImage[]> {
  const resources = await api.readResources(workbookSeq).catch(() => []);
  const saved = resources.find((one) => one.name === IMPORT_IMAGES);
  if (saved?.data == null || saved.data.length === 0) {
    return [];
  }
  try {
    // 떠 있는 그림은 제 문단을 갖지 않는다. 흐름에 넣으면 표지 배경이 첫 쪽을
    // 통째로 차지한다(2026-08-25 지적).
    return (JSON.parse(saved.data) as DocImage[])
      .map((one) => (one.floating ? { ...one, inline: true } : one));
  } catch {
    return [];
  }
}

async function readDocSnapshot(workbookSeq: number): Promise<Record<string, unknown> | null> {
  const resources = await api.readResources(workbookSeq).catch(() => []);
  const saved = resources.find((one) => one.name === DOC_SNAPSHOT);
  if (saved?.data == null || saved.data.length === 0) {
    return null;
  }
  try {
    const snapshot = JSON.parse(saved.data) as Record<string, unknown>;
    // 되보내지 않으려면 읽은 것과 같은 모양을 기억해야 한다.
    sentSnapshot = saved.data;
    return snapshot;
  } catch {
    return null;
  }
}

/**
 * 문서 한 벌을 통째로 담는다.
 *
 * <p>표·그림처럼 문단 표에 담기지 않는 것을 여기서 지킨다. 실패해도 문단 저장은
 * 이미 끝났다 - 글자를 잃지는 않는다.
 */
function saveDocSnapshot(workbookSeq: number) {
  const snapshot = univerAPI.getActiveDocument()?.getDocumentDataModel()?.getSnapshot();
  if (snapshot == null) {
    return;
  }
  // 모델이 들고 있는 resources 는 문서를 열 때 받은 그대로라 그림을 넣어도 안
  // 바뀐다. 그 빈 것을 저장하면 다음에 열 때 그림이 지워진다 - 까닭은
  // docDrawingResource 에 적었다.
  const resources = univer.__getInjector().get(IResourceManagerService)
    .getResources(`wo-doc-${workbookSeq}`, UniverInstanceType.UNIVER_DOC) as UnitResource[];
  const data = JSON.stringify(withFreshResources(snapshot as never, resources));
  if (data === sentSnapshot) {
    return;
  }
  sentSnapshot = data;
  void api.saveResource(workbookSeq, DOC_SNAPSHOT, data)
    .catch(() => { sentSnapshot = null; });
}

/** 마지막으로 보낸 문서 한 벌. 같은 것을 되보내지 않는다. */
let sentSnapshot: string | null = null;

/**
 * 빈 글 문서 한 벌.
 *
 * <p>빈 객체로 만들면 "보호되어 있어 편집 권한이 없습니다" 가 뜬다(2026-08-11 실측).
 * 본문 뼈대가 없어 Univer 가 잠긴 것으로 본다.
 *
 * <p>{@code dataStream} 끝의 두 글자가 규칙이다 - 
 이 문단 끝, 
 이 구역 끝이다.
 * 문단 하나가 0에서 시작한다고 함께 알려야 글자가 들어갈 자리가 생긴다.
 */
/**
 * 눈금자를 종이에 맞춰 그린다.
 *
 * <p>Univer 는 종이를 캔버스 안에 그리므로 종이의 화면 좌표를 렌더러에게 물어본다 -
 * 우리가 가운데 정렬을 다시 계산하면 줌이나 창 크기가 바뀔 때마다 어긋난다.
 *
 * <p>스크롤·줌 값을 매 프레임 견줘 바뀔 때만 다시 그린다. Univer 가 스크롤을
 * 캔버스 안에서 처리해 DOM 스크롤 이벤트가 오지 않는다.
 */
function installDocRuler(unitId: string) {
  rulerHEl.hidden = false;
  rulerVEl.hidden = false;

  let last = '';
  const refresh = () => {
    if (openDocSeq == null) {
      return;
    }
    const page = pageOnScreen(unitId);
    if (!page) {
      return;
    }
    // 커서가 바뀌면 눈금자는 그대로여도 들여쓰기 손잡이는 옮겨야 한다. 자리
    // 열쇠에 커서를 넣지 않아, 스크롤해야 손잡이가 따라왔다(2026-08-19 지적).
    const key = `${page.x},${page.y},${page.scale},${page.canvas.left},${page.canvas.top},`
      + `${page.canvas.width},${page.canvas.height},${docCaretAt}`;
    if (key === last) {
      return;
    }
    last = key;
    placeRulers(page);
    // 자리를 잡은 값이 화면에 반영된 뒤라야 크기를 제대로 잰다. 이 한 줄이 없으면
    // 첫 그림이 0 크기로 나가고, 값이 그대로라 다시 그리지도 않는다 - 창 크기를
    // 바꿔야 눈금이 나타났다(2026-08-11 실측).
    void rulerHEl.offsetWidth;
    drawRuler(rulerHEl, 'H', page);
    drawRuler(rulerVEl, 'V', page);
    placeTableHandles(unitId, page);
    placeMarginHandles(page);
    placeRulerShield(page);
    showPageCount(unitId, page);
    placeIndentHandles(page);
  };

  // 프레임마다 보지 않는다. 종이 자리를 물으려면 화면 크기를 재야 하는데(강제
  // 레이아웃), 그것을 매 프레임 하면 문단이 많은 문서에서 화면이 통째로 얼어붙는다
  // (2026-08-11 실측: 문단 231개 문서가 열리지 않았다).
  //
  // Univer 는 스크롤을 캔버스 안에서 처리해 DOM 스크롤 이벤트가 오지 않는다.
  // 그래서 짧은 간격으로 들여다보되, 값이 바뀔 때만 다시 그린다.
  followTableHover(unitId);
  watchToolbarHover();
  refreshTableHandles = () => {
    const page = pageOnScreen(unitId);
    if (page) {
      placeTableHandles(unitId, page);
    }
  };
  refresh();
  const timer = setInterval(refresh, 150);
  window.addEventListener('resize', refresh);
  window.addEventListener('wheel', refresh, { passive: true });
  window.addEventListener('beforeunload', () => clearInterval(timer));
}

/**
 * 표 칸 경계에 얹는 손잡이.
 *
 * <p>화면 엔진에는 표 경계를 끄는 기능이 없다. 우리가 얹는다 - 워드·한글처럼
 * 경계를 잡아 끌면 양옆 칸 너비가 바뀐다.
 *
 * <p>표 폭은 그대로 둔다. 늘린 만큼 옆 칸이 줄어든다 - 표까지 넓히면 종이 밖으로
 * 나간다.
 */
let tableHandleBox: HTMLDivElement | null = null;

/** 지금 끌고 있는 손잡이. 끄는 동안에는 자리를 다시 잡지 않는다. */
let draggingHandle: { tableId: string; edge: number } | null = null;

/** 마우스가 얹힌 표. 이 표에만 손잡이를 단다. */
let hoveredTableId: string | null = null;

/** 끌기가 끝나면 손잡이 자리를 다시 잡는다. 눈금자 새로 그리기와 별개다. */
let refreshTableHandles: (() => void) | null = null;

function tableHandleLayer(): HTMLDivElement {
  if (tableHandleBox == null) {
    tableHandleBox = document.createElement('div');
    tableHandleBox.id = 'tableHandles';
    tableHandleBox.style.position = 'fixed';
    tableHandleBox.style.left = '0';
    tableHandleBox.style.top = '0';
    tableHandleBox.style.width = '0';
    tableHandleBox.style.height = '0';
    // 판 자체는 글자 입력을 가로채면 안 된다. 손잡이만 마우스를 받는다 -
    // 눈금자에서 겪은 함정이다(2026-08-11: 자가 글자 입력을 삼켰다).
    tableHandleBox.style.pointerEvents = 'none';
    tableHandleBox.style.zIndex = '1070';
    document.body.appendChild(tableHandleBox);
  }
  return tableHandleBox;
}

/** 줄 높이를 "적어도 이만큼" 으로 보는 규칙. 화면 엔진이 쓰는 값이다. */
const ROW_HEIGHT_AT_LEAST = 1;

/** 손잡이 두께. 잡기 쉬우면서 글자 고르기를 덜 방해하는 굵기다. */
const HANDLE_THICKNESS = 7;

/**
 * 마우스가 얹힌 표에만 손잡이를 단다.
 *
 * <p>문서 안 모든 표에 손잡이를 깔면 표 위에서 글자를 고를 수 없고, 손잡이가
 * 수십 개로 늘면 화면이 흔들린다(2026-08-12 실측). 한 번에 표 하나만 다룬다.
 */
function placeTableHandles(unitId: string, page: PagePlace) {
  if (draggingHandle != null) {
    return;
  }
  const layer = tableHandleLayer();
  const places = hoveredTableId == null
    ? [] : tableEdges(unitId, page, hoveredTableId);

  while (layer.childElementCount > places.length) {
    layer.lastElementChild?.remove();
  }
  while (layer.childElementCount < places.length) {
    layer.appendChild(newTableHandle());
  }
  const half = HANDLE_THICKNESS / 2;
  places.forEach((place, at) => {
    const handle = layer.children[at] as HTMLDivElement;
    handle.style.left = `${place.x - (place.axis === 'row' ? 0 : half)}px`;
    handle.style.top = `${place.top - (place.axis === 'row' ? half : 0)}px`;
    handle.style.width = `${place.width}px`;
    handle.style.height = `${place.height}px`;
    handle.style.cursor = place.axis === 'row' ? 'row-resize' : 'col-resize';
    handle.dataset.tableId = place.tableId;
    handle.dataset.edge = String(place.edge);
    handle.dataset.axis = place.axis;
  });
}

function newTableHandle(): HTMLDivElement {
  const handle = document.createElement('div');
  handle.style.position = 'fixed';
  handle.style.pointerEvents = 'auto';
  handle.addEventListener('pointerdown', (event) => startTableDrag(handle, event));
  return handle;
}

/**
 * 눈금자 위 여백 손잡이.
 *
 * <p>워드·한글처럼 자에서 좌우 여백을 끌어 옮긴다. 여백이 바뀌면 본문 폭이 바뀌고
 * 표와 그림 자리도 함께 움직인다.
 */
let marginHandleBox: HTMLDivElement | null = null;

/** 지금 끌고 있는 여백. 끄는 동안에는 자리를 다시 잡지 않는다. */
let draggingMargin: 'left' | 'right' | null = null;

function marginHandleLayer(): HTMLDivElement {
  if (marginHandleBox == null) {
    marginHandleBox = document.createElement('div');
    marginHandleBox.id = 'marginHandles';
    marginHandleBox.style.position = 'fixed';
    marginHandleBox.style.left = '0';
    marginHandleBox.style.top = '0';
    marginHandleBox.style.width = '0';
    marginHandleBox.style.height = '0';
    marginHandleBox.style.pointerEvents = 'none';
    marginHandleBox.style.zIndex = '1071';
    document.body.appendChild(marginHandleBox);

    for (const side of ['left', 'right'] as const) {
      marginHandleBox.appendChild(newMarginHandle(side));
    }
  }
  return marginHandleBox;
}

function newMarginHandle(side: 'left' | 'right'): HTMLDivElement {
  const handle = document.createElement('div');
  handle.dataset.side = side;
  handle.style.position = 'fixed';
  handle.style.width = '11px';
  handle.style.height = '11px';
  handle.style.cursor = 'col-resize';
  handle.style.pointerEvents = 'auto';
  // 자 위에 놓인 작은 집게. 워드의 들여쓰기 표식과 같은 자리다.
  handle.style.background = '#5f6b73';
  handle.style.clipPath = side === 'left'
    ? 'polygon(50% 0, 100% 100%, 0 100%)'
    : 'polygon(50% 0, 100% 100%, 0 100%)';
  handle.title = side === 'left' ? '왼쪽 여백' : '오른쪽 여백';
  handle.addEventListener('pointerdown', (event) => startMarginDrag(handle, side, event));
  return handle;
}

/** 여백 손잡이를 자 위 제자리에 놓는다. */
function placeMarginHandles(page: PagePlace) {
  if (openDocSeq == null || draggingMargin != null) {
    return;
  }
  const layer = marginHandleLayer();
  const style = documentPageStyle();
  const top = page.canvas.top + 9;
  const left = page.x + (style.marginLeft ?? 0) * page.scale;
  const right = page.x + (style.pageWidth - (style.marginRight ?? 0)) * page.scale;

  (layer.children[0] as HTMLDivElement).style.left = `${left - 5}px`;
  (layer.children[0] as HTMLDivElement).style.top = `${top}px`;
  (layer.children[1] as HTMLDivElement).style.left = `${right - 5}px`;
  (layer.children[1] as HTMLDivElement).style.top = `${top}px`;
}

/**
 * 눈금자 위 들여쓰기 손잡이 셋.
 *
 * <p>워드·구글과 같은 구성이다 - 위는 첫 줄, 아래 왼쪽은 문단 전체, 아래 오른쪽은
 * 오른쪽 끝. 글머리표 문단에서는 위·아래 사이가 곧 "글머리와 글 사이" 다
 * (2026-08-19 지적).
 *
 * <p>커서가 있는 문단을 따라간다. 여러 문단을 고르면 명령이 그 전부에 적용된다.
 */
let indentHandleBox: HTMLDivElement | null = null;

/** 지금 끌고 있는 손잡이. 끄는 동안에는 자리를 다시 잡지 않는다. */
let draggingIndent: IndentHandle | null = null;

/**
 * 커서가 놓인 자리(글 안 몇 번째 글자인가).
 *
 * <p>선택 서비스에 물으면 빈 목록을 준다 - 커서를 두어도 그렇다(2026-08-19 실측).
 * 화면이 커서를 옮길 때 내보내는 명령을 받아 우리가 들고 있는다.
 */
let docCaretAt: number | null = null;

/** 화면이 커서를 옮길 때 내보내는 명령. */
const SET_SELECTIONS_OPERATION = 'doc.operation.set-selections';

/** 화면이 등록해 둔 문단 서식 명령. 이름이 밖으로 나와 있지 않아 id 로 부른다. */
const PARAGRAPH_SETTING_COMMAND = 'doc-paragraph-setting.command';

function indentHandleLayer(): HTMLDivElement {
  if (indentHandleBox == null) {
    indentHandleBox = document.createElement('div');
    indentHandleBox.id = 'indentHandles';
    indentHandleBox.style.position = 'fixed';
    indentHandleBox.style.left = '0';
    indentHandleBox.style.top = '0';
    indentHandleBox.style.width = '0';
    indentHandleBox.style.height = '0';
    // 판은 마우스를 통과시킨다. 손잡이만 받는다 - 자가 글자 입력을 삼킨 적이
    // 있다(2026-08-11 실측).
    indentHandleBox.style.pointerEvents = 'none';
    // 1080 아래여야 한다. 도구 띠에서 펼친 목록이 1080 이라 그보다 높으면
    // 손잡이가 목록 위에 떠 글자를 가린다(2026-08-21 지적).
    indentHandleBox.style.zIndex = '1072';
    document.body.appendChild(indentHandleBox);

    for (const kind of ['firstLine', 'left', 'right'] as const) {
      indentHandleBox.appendChild(newIndentHandle(kind));
    }
  }
  return indentHandleBox;
}

/**
 * 손잡이 하나. 모양과 크기는 구글 문서를 따른다.
 *
 * <p>구글은 첫 줄을 <b>직사각형</b>, 왼쪽을 <b>아래를 보는 삼각형</b>으로 그리고
 * 둘을 위아래로 겹쳐 둔다. 오른쪽도 같은 삼각형이다. 우리도 같게 한다 -
 * 쓰던 사람이 다시 익힐 것이 없다(2026-08-19 요청).
 */
/** 글 문서가 쓰는 강조색. 초록은 시트 몫이다(2026-08-19 요청). */
const DOC_ACCENT = '#1a5fc8';

/** 끌 때 진해지는 강조색. */
const DOC_ACCENT_DARK = '#103f7c';

function newIndentHandle(kind: IndentHandle): HTMLDivElement {
  const handle = document.createElement('div');
  handle.dataset.kind = kind;
  handle.style.position = 'fixed';
  handle.style.cursor = 'col-resize';
  handle.style.pointerEvents = 'auto';
  handle.style.background = DOC_ACCENT;
  if (kind === 'firstLine') {
    handle.style.width = '10px';
    handle.style.height = '6px';
    handle.style.borderRadius = '1px';
  } else {
    handle.style.width = '12px';
    handle.style.height = '8px';
    handle.style.clipPath = 'polygon(0 0, 100% 0, 50% 100%)';
  }
  handle.title = kind === 'firstLine' ? '첫 줄 들여쓰기'
    : kind === 'left' ? '왼쪽 들여쓰기 (문단 전체)' : '오른쪽 들여쓰기';
  handle.addEventListener('pointerdown', (event) => startIndentDrag(handle, kind, event));
  return handle;
}

/**
 * 커서가 있는 문단의 들여쓰기. 문단을 못 찾으면 빈 값이다.
 *
 * <p>목록 문단은 들여쓰기를 제 몸에 안 적는다 - 목록 규칙이 정한다. 그것까지
 * 합쳐야 손잡이가 글과 같은 자리에 선다(2026-08-19 지적).
 */
function currentIndents(): Indents {
  const selection = univer.__getInjector().get(DocSelectionManagerService);
  // 선택 서비스가 빈 목록을 주는 경우가 있어 명령에서 받아 둔 자리를 먼저 본다.
  const at = docCaretAt ?? selection.getActiveTextRange()?.startOffset
    ?? selection.getDocRanges()?.[0]?.startOffset;
  const body = univerAPI.getActiveDocument()?.getDocumentDataModel()?.getSnapshot()?.body as unknown as {
    paragraphs?: {
      startIndex: number;
      paragraphStyle?: Record<string, { v?: number } | undefined>;
      bullet?: { listType?: string; nestingLevel?: number };
    }[];
  } | undefined;
  if (at == null || body?.paragraphs == null) {
    return {};
  }
  const paragraph = body.paragraphs.find((one) => one.startIndex >= at);
  if (paragraph == null) {
    return {};
  }
  const style = paragraph.paragraphStyle ?? {};
  const own: Indents = {
    indentStart: style.indentStart?.v,
    indentEnd: style.indentEnd?.v,
    indentFirstLine: style.indentFirstLine?.v,
    hanging: style.hanging?.v,
  };
  return effectiveIndents(own, listIndents(paragraph.bullet));
}

/** 목록 규칙이 정한 들여쓰기. 목록이 아니면 없다. */
function listIndents(bullet?: { listType?: string; nestingLevel?: number }): Indents | undefined {
  if (bullet?.listType == null) {
    return undefined;
  }
  const lists = docLists() as Record<string, {
    nestingLevel: { paragraphProperties: { hanging: { v: number }; indentStart: { v: number } } }[];
  }>;
  const level = lists[bullet.listType]?.nestingLevel?.[bullet.nestingLevel ?? 0];
  if (level == null) {
    return undefined;
  }
  return {
    indentStart: level.paragraphProperties.indentStart.v,
    hanging: level.paragraphProperties.hanging.v,
  };
}

/** 화면이 글머리 글자에 붙이는 종류. 일반 글자는 0 이다(실측). */
const BULLET_GLYPH = 2;

/**
 * 커서가 있는 문단이 실제로 그려진 자리.
 *
 * <p>들여쓰기 값을 우리가 다시 셈하면 어긋난다 - 글머리표는 화면이 걸어쓰기를
 * 글머리 글자 폭으로 바꾸고, 표 안 문단은 칸을 기준으로 잰다(2026-08-19 지적).
 * 화면이 셈해 둔 줄 자리를 그대로 읽는다.
 *
 * <p>표 안이면 아무것도 돌려주지 않는다. 표 칸은 제 쪽(page)을 따로 두어 이
 * 걸음으로는 닿지 않는다 - 그때는 손잡이를 감춘다.
 */
function caretPlaces(unitId: string, caret: number | null): {
  first: number; left: number; right: number;
} | null {
  if (caret == null) {
    return null;
  }
  const render = univer.__getInjector().get(IRenderManagerService).getRenderUnitById(unitId);
  const paper = render?.mainComponent as unknown as
    { getSkeleton?: () => { getSkeletonData?: () => DocSkeletonData } } | undefined;
  const data = paper?.getSkeleton?.()?.getSkeletonData?.();
  if (data == null) {
    return null;
  }

  for (const page of data.pages) {
    for (const section of page.sections ?? []) {
      for (const column of section.columns ?? []) {
        const lines = column.lines ?? [];
        const found = lines.find((line) => caret >= line.st && caret <= line.ed);
        if (found == null) {
          continue;
        }
        const mine = lines.filter((line) => line.paragraphIndex === found.paragraphIndex);
        const startOf = (line: typeof found) => {
          const divide = line.divides?.[0];
          return divide == null ? null
            : page.marginLeft + column.left + divide.left + divide.paddingLeft;
        };
        const first = mine.find((line) => line.paragraphStart) ?? mine[0];
        const edge = first.divides?.[0];
        if (edge == null) {
          return null;
        }
        // 글머리표는 줄 맨 앞 글자로 그려진다. 그래서 첫 줄 손잡이가 곧 글머리
        // 자리이고, 글이 시작하는 자리는 그 폭만큼 뒤다(2026-08-19 지적).
        //
        // 줄에는 글머리 표시가 없다(line.bullet 은 비어 있었다 - 실측). 맨 앞
        // 글자의 종류로 가른다. 2 가 글머리 글자다.
        const head = edge.glyphGroup?.[0];
        const bulletWidth = head?.glyphType === BULLET_GLYPH ? head.width : 0;
        const other = mine.find((line) => !line.paragraphStart);
        const firstAt = startOf(first) ?? 0;
        return {
          first: firstAt,
          left: other == null ? firstAt + bulletWidth : startOf(other) ?? firstAt,
          right: page.marginLeft + column.left + edge.left + edge.width,
        };
      }
    }
  }
  return null;
}

/**
 * 눈금자 자리를 막는 투명 덮개.
 *
 * <p>화면이 문단 옆에 띄우는 단추(문단 설정·문단 옮기기)가 눈금자 자리까지
 * 올라온다. 그것을 잡으면 문단이 통째로 끌려가 목록이 흐트러진다(2026-08-19
 * 지적: 자를 조정하려다 문단이 옮겨졌다).
 *
 * <p>덮개는 눈에 보이지 않는다 - 단추 설명 글은 그대로 비치고, 마우스만
 * 가로챈다. 우리 손잡이는 덮개보다 위에 있어 그대로 잡힌다.
 */
/**
 * 마우스가 도구 띠에 있으면 자 위 손잡이를 잠시 감춘다.
 *
 * <p>단추 설명 글(툴팁)이 자 자리로 내려오는데, 손잡이가 그보다 위에 있어
 * 글을 가렸다(2026-08-19 지적). 도구 띠를 지날 때는 손잡이를 잡을 일이 없다.
 */
function watchToolbarHover() {
  window.addEventListener('pointermove', (event) => {
    if (draggingIndent != null || openDocSeq == null) {
      return;
    }
    const top = rulerHEl.getBoundingClientRect().top;
    const hide = event.clientY < top ? 'hidden' : 'visible';
    indentHandleLayer().style.visibility = hide;
    marginHandleLayer().style.visibility = hide;
    tableHandleLayer().style.visibility = hide;
    rulerShield().style.visibility = hide;
  }, { passive: true });
}

let rulerShieldBox: HTMLDivElement | null = null;

function rulerShield(): HTMLDivElement {
  if (rulerShieldBox == null) {
    rulerShieldBox = document.createElement('div');
    rulerShieldBox.id = 'rulerShield';
    rulerShieldBox.style.position = 'fixed';
    rulerShieldBox.style.background = 'transparent';
    rulerShieldBox.style.zIndex = '1075';
    rulerShieldBox.style.cursor = 'default';
    // 눌러도 아무 일도 없다. 편집기 포커스를 뺏지 않는다.
    rulerShieldBox.addEventListener('pointerdown', (event) => event.preventDefault());
    document.body.appendChild(rulerShieldBox);
  }
  return rulerShieldBox;
}

/** 덮개를 눈금자 자리에 맞춘다. */
function placeRulerShield(page: PagePlace) {
  const shield = rulerShield();
  if (openDocSeq == null) {
    shield.hidden = true;
    return;
  }
  shield.hidden = false;
  shield.style.left = `${page.canvas.left}px`;
  shield.style.top = `${page.canvas.top}px`;
  shield.style.width = `${page.canvas.width}px`;
  shield.style.height = '20px';
}

/** 들여쓰기 손잡이를 자 위 제자리에 놓는다. */
function placeIndentHandles(page: PagePlace) {
  const layer = indentHandleLayer();
  if (openDocSeq == null) {
    layer.hidden = true;
    return;
  }
  layer.hidden = false;
  if (draggingIndent != null) {
    return;
  }
  const style = documentPageStyle();
  const width = style.pageWidth - style.marginLeft - style.marginRight;
  // 화면이 그린 자리를 먼저 본다. 없으면(표 안이면) 손잡이를 감춘다.
  const drawn = caretPlaces(`wo-doc-${openDocSeq}`, docCaretAt);
  if (drawn == null && docCaretAt != null) {
    // 표 칸 안이면 여기로 온다. 엉뚱한 자리에 두느니 감춘다.
    layer.hidden = true;
    return;
  }
  const guess = indentHandles(currentIndents(), width);
  const at = drawn ?? {
    first: style.marginLeft + guess.firstLine,
    left: style.marginLeft + guess.left,
    right: style.marginLeft + guess.right,
  };
  const top = page.canvas.top;

  const put = (index: number, x: number, y: number, half: number) => {
    const handle = layer.children[index] as HTMLDivElement;
    handle.style.left = `${page.x + x * page.scale - half}px`;
    handle.style.top = `${y}px`;
  };
  // 구글과 같은 층: 첫 줄(사각형)이 위, 왼쪽(삼각형)이 그 아래.
  put(0, at.first, top + 2, 5);
  put(1, at.left, top + 10, 6);
  put(2, at.right, top + 10, 6);
}

/** 들여쓰기를 끌기 시작한다. 놓을 때 한 번만 문단을 고친다. */
function startIndentDrag(handle: HTMLDivElement, kind: IndentHandle, event: PointerEvent) {
  if (openDocSeq == null) {
    return;
  }
  // 편집기에서 포커스를 뺏지 않는다. 뺏기면 놓은 뒤에 글자가 입력되지 않는다.
  event.preventDefault();
  draggingIndent = kind;
  const startX = event.clientX;
  const startLeft = handle.offsetLeft;
  const page = pageOnScreen(`wo-doc-${openDocSeq}`);
  const scale = page?.scale ?? 1;
  handle.style.background = DOC_ACCENT_DARK;
  const guide = showIndentGuide(page, startLeft + 6);

  // 왼쪽 손잡이는 문단째 옮긴다. 구글처럼 첫 줄 손잡이도 함께 따라간다.
  const partner = kind === 'left'
    ? indentHandleLayer().children[0] as HTMLDivElement : null;
  const partnerLeft = partner == null ? 0 : partner.offsetLeft;

  const move = (moved: PointerEvent) => {
    const by = moved.clientX - startX;
    handle.style.left = `${startLeft + by}px`;
    if (partner != null) {
      partner.style.left = `${partnerLeft + by}px`;
    }
    guide.style.left = `${startLeft + by + 6}px`;
  };
  const end = (moved: PointerEvent) => {
    window.removeEventListener('pointermove', move);
    window.removeEventListener('pointerup', end);
    handle.style.background = DOC_ACCENT;
    guide.remove();
    draggingIndent = null;
    applyIndent(kind, (moved.clientX - startX) / Math.max(scale, 0.01), moved.altKey);
  };
  window.addEventListener('pointermove', move);
  window.addEventListener('pointerup', end);
}

/**
 * 끄는 동안 종이에 세로 선을 긋는다.
 *
 * <p>선이 없으면 손잡이가 글의 어디에 닿는지 눈으로 못 잰다(2026-08-19 지적).
 * 구글 문서도 같은 선을 보인다.
 */
function showIndentGuide(page: PagePlace | null, left: number): HTMLDivElement {
  const guide = document.createElement('div');
  guide.style.position = 'fixed';
  guide.style.width = '1px';
  guide.style.background = DOC_ACCENT;
  guide.style.opacity = '0.6';
  guide.style.pointerEvents = 'none';
  guide.style.zIndex = '1073';
  guide.style.left = `${left}px`;
  guide.style.top = `${(page?.canvas.top ?? 0) + 20}px`;
  guide.style.height = `${Math.max(0, (page?.canvas.height ?? 0) - 20)}px`;
  document.body.appendChild(guide);
  return guide;
}

/**
 * 끈 만큼 문단 들여쓰기를 고친다.
 *
 * <p>Alt 를 누른 채 놓으면 눈금에 붙이지 않는다 - 원본 문서의 어중간한 값을
 * 지켜야 할 때가 있다.
 */
function applyIndent(kind: IndentHandle, movedPx: number, free: boolean) {
  if (Math.abs(movedPx) < 0.5) {
    return;
  }
  const style = documentPageStyle();
  const width = style.pageWidth - style.marginLeft - style.marginRight;
  // 지금 그려진 자리에서 잰다. 값으로 다시 셈하면 글머리표 문단에서 어긋난다
  // (화면이 걸어쓰기를 글머리 글자 폭으로 바꾼다).
  const drawn = caretPlaces(`wo-doc-${openDocSeq}`, docCaretAt);
  // 화면 셈법: 줄 왼쪽 = indentStart, 첫 줄 += indentFirstLine, 나머지 += hanging.
  // 그린 자리를 그대로 이 셋으로 옮겨 적는다.
  const before = drawn == null ? currentIndents() : {
    indentStart: drawn.first - style.marginLeft,
    indentFirstLine: 0,
    hanging: Math.max(0, drawn.left - drawn.first),
    indentEnd: width - (drawn.right - style.marginLeft),
  };
  const after = movedIndent(kind, before, movedPx, width, free);

  // 명령은 "지금 고른 곳" 에 적용된다. 우리가 손잡이로 보고 있는 문단에 커서를
  // 다시 두어 어긋나지 않게 한다 - 엉뚱한 문단이 움직인 적이 있다(2026-08-19 지적).
  const caret = docCaretAt;
  if (caret != null && openDocSeq != null) {
    const unitId = `wo-doc-${openDocSeq}`;
    univer.__getInjector().get(DocSelectionManagerService)
      .replaceDocRanges([{ startOffset: caret, endOffset: caret }],
        { unitId, subUnitId: unitId }, false);
  }

  void commandService().executeCommand(PARAGRAPH_SETTING_COMMAND, {
    paragraph: {
      indentStart: { v: after.indentStart ?? 0 },
      indentEnd: { v: after.indentEnd ?? 0 },
      indentFirstLine: { v: after.indentFirstLine ?? 0 },
      hanging: { v: after.hanging ?? 0 },
    },
  }).then(() => status(indentText(kind, after)))
    .catch((error) => status(messageOf(error)));
}

/**
 * 커서가 든 문단의 줄 간격(배수). 배수가 아니면 null 이다.
 *
 * <p>가져온 워드 문서는 줄 높이를 길이로 못박아 온다. 그런 문단은 배수 목록에서
 * 고를 수 없으므로 아무것도 짚지 않는다.
 */
function currentLineSpacing(): number | null {
  return lineSpacingOf(caretParagraphs(), caretOffset());
}

/** 사람에게 보여 줄 지금 줄 간격. 길이로 못박힌 문단은 배수로 환산해 보여 준다. */
function shownSpacing(): number | null {
  const style = univerAPI.getActiveDocument()?.getDocumentDataModel()?.getSnapshot()
    ?.documentStyle as { textStyle?: { fs?: number } } | undefined;
  return shownLineSpacing(caretParagraphs(), caretOffset(), style?.textStyle?.fs ?? 11);
}

function caretOffset(): number | undefined {
  const selection = univer.__getInjector().get(DocSelectionManagerService);
  return docCaretAt ?? selection.getActiveTextRange()?.startOffset
    ?? selection.getDocRanges()?.[0]?.startOffset;
}

function caretParagraphs(): SpacedParagraph[] {
  const body = univerAPI.getActiveDocument()?.getDocumentDataModel()?.getSnapshot()?.body as unknown as {
    paragraphs?: SpacedParagraph[];
  } | undefined;
  return body?.paragraphs ?? [];
}

/**
 * 고른 문단의 줄 간격을 바꾼다.
 *
 * <p>배수로 준다. 워드와 같은 셈이고, 글자 크기를 바꿔도 줄이 따라 늘어난다.
 */
function setLineSpacing(spacing: number): void {
  void commandService().executeCommand(PARAGRAPH_SETTING_COMMAND,
    lineSpacingSetting(spacing))
    .then((done) => status(done ? `줄 간격 ${spacing.toFixed(2).replace(/0$/, '')}`
      : '줄 간격을 바꾸지 못했습니다'))
    .catch((error) => status(messageOf(error)));
}

/** 상태줄에 지금 값을 센티미터로 알린다. */
function indentText(kind: IndentHandle, indents: Indents): string {
  const px = kind === 'right' ? indents.indentEnd ?? 0
    : kind === 'left' ? indents.indentStart ?? 0
      : (indents.indentStart ?? 0) + (indents.indentFirstLine ?? 0) - (indents.hanging ?? 0);
  const name = kind === 'right' ? '오른쪽 들여쓰기'
    : kind === 'left' ? '왼쪽 들여쓰기' : '첫 줄 들여쓰기';
  return `${name} ${(px / (96 / 2.54)).toFixed(2)}cm`;
}

/**
 * 지금 문서의 본문 폭.
 *
 * <p>표는 이 폭을 넘지 못한다. 우리 기본 폭으로 재면 레터 문서에서 표를 원본
 * 너비까지 늘릴 수 없다(2026-08-13 실측: 원본 719px, 우리 기본 606px).
 */
function bodyWidth(): number {
  const style = documentPageStyle();
  return style.pageWidth - style.marginLeft - style.marginRight;
}

/** 지금 문서의 종이와 여백. 문서가 없으면 우리 기본이다. */
function documentPageStyle(): {
  pageWidth: number; pageHeight: number;
  marginLeft: number; marginRight: number;
  marginTop: number; marginBottom: number;
} {
  const snapshot = univerAPI.getActiveDocument()?.getDocumentDataModel()?.getSnapshot() as unknown as {
    documentStyle?: {
      pageSize?: { width?: number; height?: number };
      marginLeft?: number;
      marginRight?: number;
      marginTop?: number;
      marginBottom?: number;
    };
  } | undefined;
  const style = snapshot?.documentStyle;
  return {
    pageWidth: style?.pageSize?.width ?? PAGE.width,
    pageHeight: style?.pageSize?.height ?? PAGE.height,
    marginLeft: style?.marginLeft ?? PAGE.marginLeft,
    marginRight: style?.marginRight ?? PAGE.marginRight,
    marginTop: style?.marginTop ?? PAGE.marginTop,
    marginBottom: style?.marginBottom ?? PAGE.marginBottom,
  };
}

/** 여백을 끌기 시작한다. 놓을 때 한 번만 문서를 고친다. */
function startMarginDrag(handle: HTMLDivElement, side: 'left' | 'right',
  event: PointerEvent) {
  if (openDocSeq == null) {
    return;
  }
  event.preventDefault();
  draggingMargin = side;
  const startX = event.clientX;
  const startLeft = handle.offsetLeft;
  handle.style.background = DOC_ACCENT;

  const move = (moved: PointerEvent) => {
    handle.style.left = `${startLeft + (moved.clientX - startX)}px`;
  };
  const end = (moved: PointerEvent) => {
    window.removeEventListener('pointermove', move);
    window.removeEventListener('pointerup', end);
    handle.style.background = '#5f6b73';
    draggingMargin = null;
    applyMargin(side, moved.clientX - startX);
  };
  window.addEventListener('pointermove', move);
  window.addEventListener('pointerup', end);
}

/**
 * 끈 만큼 여백을 고친다.
 *
 * <p>오른쪽 여백은 자 위에서 끈 방향과 반대로 자란다 - 왼쪽으로 끌면 여백이
 * 넓어지고 본문이 좁아진다.
 */
function applyMargin(side: 'left' | 'right', movedPx: number) {
  const workbookSeq = openDocSeq;
  if (workbookSeq == null || Math.abs(movedPx) < 1) {
    return;
  }
  const style = documentPageStyle();
  const before = side === 'left' ? style.marginLeft : style.marginRight;
  const other = side === 'left' ? style.marginRight : style.marginLeft;
  const after = resizeMargin(before, side === 'left' ? movedPx : -movedPx,
    style.pageWidth, other);
  if (after === before) {
    return;
  }

  const jsonX = JSONX.getInstance();
  const key = side === 'left' ? 'marginLeft' : 'marginRight';
  runTableEdit(workbookSeq, jsonX.replaceOp(['documentStyle', key], before, after));
}

/** 표 하나의 경계 자리. */
interface EdgePlace {
  tableId: string;
  edge: number;
  /** 칸 너비·줄 높이·표 오른쪽·표 왼쪽 가운데 무엇을 바꾸는 손잡이인가. */
  axis: 'column' | 'row' | 'table' | 'tableLeft';
  x: number;
  top: number;
  width: number;
  height: number;
}

/** 화면에 놓인 표 하나. 손잡이를 놓을 때 쓰는 자리와 크기다. */
interface TablePlace extends TableRect {
  widths: number[];
  heights: number[];
}

/**
 * 화면에 보이는 표들의 자리.
 *
 * <p>쪽마다 표가 어디에 놓였는지는 화면 엔진이 셈해 둔 것(스켈레톤)을 읽는다.
 * 우리가 다시 세면 줄 높이·쪽 넘김에서 어긋난다.
 */
function tablesOnScreen(unitId: string, page: PagePlace): TablePlace[] {
  const render = univer.__getInjector().get(IRenderManagerService).getRenderUnitById(unitId);
  const paper = render?.mainComponent as unknown as
    { getSkeleton?: () => { getSkeletonData?: () => DocSkeletonData } } | undefined;
  const data = paper?.getSkeleton?.()?.getSkeletonData?.();
  if (!data?.pages) {
    return [];
  }

  const places: TablePlace[] = [];
  let pageTop = page.y;
  for (const one of data.pages) {
    for (const table of one.skeTables?.values() ?? []) {
      const widths = (table.tableSource?.tableColumns ?? [])
        .map((column) => column.size?.width?.v ?? 0);
      const heights = (table.rows ?? []).map((row) => row.height ?? 0);
      const top = pageTop + (one.marginTop + table.top) * page.scale;
      const height = table.height * page.scale;
      // 쪽 밖으로 나간 표는 셈하지 않는다. 잡아도 보이지 않는다.
      if (widths.length === 0
        || top + height < page.canvas.top
        || top > page.canvas.top + page.canvas.height) {
        continue;
      }
      places.push({
        // 스켈레톤은 표 이름 뒤에 쪽 번호를 붙인다. 문서가 아는 이름만 쓴다.
        tableId: String(table.tableId).split('#-#')[0],
        left: page.x + (one.marginLeft + table.left) * page.scale,
        top,
        width: widths.reduce((sum, one2) => sum + one2, 0) * page.scale,
        height,
        widths,
        heights,
      });
    }
    pageTop += (one.pageHeight + PAGE_GAP) * page.scale;
  }
  return places;
}

/** 표 하나의 칸·줄 경계 자리. */
function tableEdges(unitId: string, page: PagePlace, tableId: string): EdgePlace[] {
  const table = tablesOnScreen(unitId, page).find((one) => one.tableId === tableId);
  if (table == null) {
    return [];
  }
  const places: EdgePlace[] = [];
  for (const [at, edge] of columnEdges(table.widths, 0).entries()) {
    places.push({
      tableId,
      edge: at,
      axis: 'column',
      x: table.left + edge * page.scale,
      top: table.top,
      width: HANDLE_THICKNESS,
      height: table.height,
    });
  }
  for (const [at, edge] of rowEdges(table.heights, 0).entries()) {
    places.push({
      tableId,
      edge: at,
      axis: 'row',
      x: table.left,
      top: table.top + edge * page.scale,
      width: table.width,
      height: HANDLE_THICKNESS,
    });
  }
  // 표 왼쪽 끝. 여기를 끌면 들여쓰기가 함께 움직인다.
  places.push({
    tableId,
    edge: 0,
    axis: 'tableLeft',
    x: table.left,
    top: table.top,
    width: HANDLE_THICKNESS,
    height: table.height,
  });
  // 표 오른쪽 끝. 여기를 끌면 표 전체가 넓어지거나 좁아진다.
  places.push({
    tableId,
    edge: table.widths.length - 1,
    axis: 'table',
    x: table.left + table.width,
    top: table.top,
    width: HANDLE_THICKNESS,
    height: table.height,
  });
  return places;
}

/**
 * 마우스를 따라 손잡이를 옮긴다.
 *
 * <p>표 위에 있을 때만 그 표에 손잡이를 단다. 표 밖으로 나가면 걷는다 - 없으면
 * 표 위에서 글자를 고를 수 없다.
 */
function followTableHover(unitId: string) {
  const onMove = (event: PointerEvent) => {
    if (openDocSeq == null || draggingHandle != null) {
      return;
    }
    const page = pageOnScreen(unitId);
    if (page == null) {
      return;
    }
    const hit = hitTable(tablesOnScreen(unitId, page), event.clientX, event.clientY);
    const next = hit?.tableId ?? null;
    if (next === hoveredTableId) {
      return;
    }
    hoveredTableId = next;
    placeTableHandles(unitId, page);
  };
  window.addEventListener('pointermove', onMove, { passive: true });
}

/** 쪽과 쪽 사이. 화면 엔진이 쓰는 값과 같다. */
const PAGE_GAP = 40;

/** 스켈레톤에서 우리가 읽는 것만 적는다. */
interface DocSkeletonData {
  pages: {
    marginLeft: number;
    marginTop: number;
    pageHeight: number;
    sections?: {
      columns?: {
        left: number;
        lines?: {
          paragraphIndex: number;
          paragraphStart: boolean;
          st: number;
          ed: number;
          bullet?: unknown;
          divides?: {
            left: number; paddingLeft: number; width: number;
            glyphGroup?: { width: number; glyphType?: number }[];
          }[];
        }[];
      }[];
    }[];
    skeTables?: Map<string, {
      tableId: string;
      left: number;
      top: number;
      height: number;
      rows?: { height?: number }[];
      tableSource?: { tableColumns?: { size?: { width?: { v?: number } } }[] };
    }>;
  }[];
}

/** 손잡이를 끌기 시작한다. 놓을 때 한 번만 문서를 고친다. */
function startTableDrag(handle: HTMLDivElement, event: PointerEvent) {
  const tableId = handle.dataset.tableId;
  const edge = Number(handle.dataset.edge);
  const axis = handle.dataset.axis === 'row' ? 'row'
    : handle.dataset.axis === 'table' ? 'table'
      : handle.dataset.axis === 'tableLeft' ? 'tableLeft' : 'column';
  if (openDocSeq == null || tableId == null || Number.isNaN(edge)) {
    return;
  }
  event.preventDefault();
  draggingHandle = { tableId, edge };
  const start = axis === 'row' ? event.clientY : event.clientX;
  const startAt = axis === 'row' ? handle.offsetTop : handle.offsetLeft;
  handle.style.background = DOC_ACCENT;

  const movedBy = (moved: PointerEvent) =>
    (axis === 'row' ? moved.clientY : moved.clientX) - start;
  const move = (moved: PointerEvent) => {
    const at = `${startAt + movedBy(moved)}px`;
    if (axis === 'row') {
      handle.style.top = at;
    } else {
      handle.style.left = at;
    }
  };
  const end = (moved: PointerEvent) => {
    window.removeEventListener('pointermove', move);
    window.removeEventListener('pointerup', end);
    handle.style.background = '';
    draggingHandle = null;
    if (axis === 'row') {
      applyRowResize(tableId, edge, movedBy(moved));
    } else if (axis === 'table') {
      applyTableWidth(tableId, movedBy(moved));
    } else if (axis === 'tableLeft') {
      applyTableLeft(tableId, movedBy(moved));
    } else {
      applyColumnResize(tableId, edge, movedBy(moved));
    }
    // 끌어 놓은 뒤 크기가 바뀌었으니 나머지 손잡이도 자리를 다시 잡는다.
    window.setTimeout(() => refreshTableHandles?.(), 60);
  };
  window.addEventListener('pointermove', move);
  window.addEventListener('pointerup', end);
}

/**
 * 끈 만큼 줄 높이를 고친다.
 *
 * <p>아래 줄에서 뺏지 않는다 - 늘린 만큼 표가 길어지면 된다.
 */
function applyRowResize(tableId: string, rowIndex: number, movedPx: number) {
  const workbookSeq = openDocSeq;
  const snapshot = univerAPI.getActiveDocument()?.getDocumentDataModel()?.getSnapshot() as unknown as {
    tableSource?: Record<string, {
      tableRows?: { trHeight?: { val?: { v?: number }; hRule?: number } }[];
    }>;
  } | undefined;
  const rows = snapshot?.tableSource?.[tableId]?.tableRows;
  if (workbookSeq == null || rows == null || Math.abs(movedPx) < 1) {
    return;
  }
  const before = rows.map((row) => row.trHeight?.val?.v ?? 0);
  const after = resizeRow(before, rowIndex, movedPx);
  if (after[rowIndex] === before[rowIndex]) {
    return;
  }

  const jsonX = JSONX.getInstance();
  const height = jsonX.replaceOp(
    ['tableSource', tableId, 'tableRows', rowIndex, 'trHeight', 'val', 'v'],
    before[rowIndex], after[rowIndex]);
  // 줄 높이 규칙이 "알아서"(0) 면 적어 둔 높이를 안 본다. 사람이 끌어 정한
  // 높이는 지켜야 하므로 "적어도 이만큼"(1) 으로 바꾼다.
  const rule = rows[rowIndex]?.trHeight?.hRule ?? 0;
  const actions = rule === ROW_HEIGHT_AT_LEAST
    ? height
    : JSONX.compose(height as never, jsonX.replaceOp(
      ['tableSource', tableId, 'tableRows', rowIndex, 'trHeight', 'hRule'],
      rule, ROW_HEIGHT_AT_LEAST) as never);
  runTableEdit(workbookSeq, actions);
}

/**
 * 끈 만큼 칸 너비를 고친다.
 *
 * <p>양옆 칸만 바꾸고 표 폭은 그대로 둔다. 표까지 넓히면 종이 밖으로 나간다.
 */
function applyColumnResize(tableId: string, edge: number, movedPx: number) {
  const workbookSeq = openDocSeq;
  const snapshot = univerAPI.getActiveDocument()?.getDocumentDataModel()?.getSnapshot() as unknown as {
    tableSource?: Record<string, { tableColumns?: { size?: { width?: { v?: number } } }[] }>;
  } | undefined;
  const columns = snapshot?.tableSource?.[tableId]?.tableColumns;
  if (workbookSeq == null || columns == null || columns.length < 2 || Math.abs(movedPx) < 1) {
    return;
  }
  const before = columns.map((column) => column.size?.width?.v ?? 0);
  const after = resizeColumns(before, edge, movedPx);

  const jsonX = JSONX.getInstance();
  const actions = before
    .map((width, at) => jsonX.replaceOp(
      ['tableSource', tableId, 'tableColumns', at, 'size', 'width', 'v'], width, after[at]))
    .reduce((all: unknown, one: unknown) => JSONX.compose(all as never, one as never), null);
  runTableEdit(workbookSeq, actions);
}

/**
 * 끈 만큼 표 폭을 고친다.
 *
 * <p>마지막 칸만 늘어난다. 표 폭(size)도 함께 고쳐야 한다 - 칸 너비만 고치면
 * 화면이 옛 폭으로 표를 그려 칸과 테두리가 어긋난다.
 */
function applyTableWidth(tableId: string, movedPx: number) {
  const workbookSeq = openDocSeq;
  const snapshot = univerAPI.getActiveDocument()?.getDocumentDataModel()?.getSnapshot() as unknown as {
    tableSource?: Record<string, {
      tableColumns?: { size?: { width?: { v?: number } } }[];
      size?: { width?: { v?: number } };
    }>;
  } | undefined;
  const table = snapshot?.tableSource?.[tableId];
  const columns = table?.tableColumns;
  if (workbookSeq == null || columns == null || columns.length === 0
    || Math.abs(movedPx) < 1) {
    return;
  }
  const before = columns.map((column) => column.size?.width?.v ?? 0);
  const after = resizeTable(before, movedPx, bodyWidth());
  const last = before.length - 1;
  if (after[last] === before[last]) {
    return;
  }

  const jsonX = JSONX.getInstance();
  const width = jsonX.replaceOp(
    ['tableSource', tableId, 'tableColumns', last, 'size', 'width', 'v'],
    before[last], after[last]);
  const total = jsonX.replaceOp(
    ['tableSource', tableId, 'size', 'width', 'v'],
    table?.size?.width?.v ?? before.reduce((sum, one) => sum + one, 0),
    after.reduce((sum, one) => sum + one, 0));
  runTableEdit(workbookSeq, JSONX.compose(width as never, total as never));
}

/**
 * 끈 만큼 표 왼쪽 끝을 옮긴다.
 *
 * <p>오른쪽 끝은 제자리에 두고 들여쓰기와 첫 칸만 바꾼다. 표 폭(size)도 함께
 * 고쳐야 화면이 칸과 테두리를 같은 자리에 그린다.
 */
function applyTableLeft(tableId: string, movedPx: number) {
  const workbookSeq = openDocSeq;
  const snapshot = univerAPI.getActiveDocument()?.getDocumentDataModel()?.getSnapshot() as unknown as {
    tableSource?: Record<string, {
      tableColumns?: { size?: { width?: { v?: number } } }[];
      size?: { width?: { v?: number } };
      indent?: { v?: number };
    }>;
  } | undefined;
  const table = snapshot?.tableSource?.[tableId];
  const columns = table?.tableColumns;
  if (workbookSeq == null || columns == null || columns.length === 0
    || Math.abs(movedPx) < 1) {
    return;
  }
  const before = columns.map((column) => column.size?.width?.v ?? 0);
  const indentBefore = table?.indent?.v ?? 0;
  const moved = resizeTableLeft(before, indentBefore, movedPx, bodyWidth());
  if (moved.widths[0] === before[0] && moved.indent === indentBefore) {
    return;
  }

  const jsonX = JSONX.getInstance();
  const actions = [
    jsonX.replaceOp(['tableSource', tableId, 'tableColumns', 0, 'size', 'width', 'v'],
      before[0], moved.widths[0]),
    jsonX.replaceOp(['tableSource', tableId, 'indent', 'v'],
      indentBefore, moved.indent),
    jsonX.replaceOp(['tableSource', tableId, 'size', 'width', 'v'],
      table?.size?.width?.v ?? before.reduce((sum, one) => sum + one, 0),
      moved.widths.reduce((sum, one) => sum + one, 0)),
  ].reduce((all: unknown, one: unknown) => JSONX.compose(all as never, one as never), null);
  runTableEdit(workbookSeq, actions);
}

/**
 * 표 얼개 고치기를 문서에 싣는다.
 *
 * <p>화면 엔진이 칸을 넣을 때 쓰는 길과 같다 - 글 고치기 변경에 얼개 바꾸기를
 * 실어 보낸다. 그래야 되돌리기가 함께 따라온다. 명령은 이름(문자열)으로 부른다:
 * RichTextEditingMutation 을 정적으로 들여오면 문서가 백지가 됐다(2026-08-12 실측).
 *
 * <p>칸 너비는 글자가 아니라 문서 얼개라 문단 저장으로는 남지 않는다. 문서 한
 * 벌을 남겨야 다시 열었을 때 끌어 놓은 크기가 살아난다.
 */
function runTableEdit(workbookSeq: number, actions: unknown) {
  void univerAPI.executeCommand('doc.mutation.rich-text-editing', {
    unitId: `wo-doc-${workbookSeq}`,
    actions,
    textRanges: [],
    trigger: 'wo.table-resize',
  } as never).then(() => saveDocSnapshot(workbookSeq));
}

interface PagePlace {
  /** 종이 왼쪽 위 모서리의 화면 좌표. */
  x: number;
  y: number;
  scale: number;
  /** 편집 캔버스가 화면에서 차지하는 자리. 눈금자를 여기에 붙인다. */
  canvas: { left: number; top: number; width: number; height: number };
}

/** 종이가 지금 화면 어디에 있는가. 렌더러가 아는 것을 그대로 받는다. */
function pageOnScreen(unitId: string): PagePlace | null {
  const render = univer.__getInjector().get(IRenderManagerService).getRenderUnitById(unitId);
  const canvas = appEl.querySelector('canvas');
  const paper = render?.mainComponent as { left?: number; top?: number } | undefined;
  if (!render || !canvas || paper?.left == null || paper.top == null) {
    return null;
  }
  // 이름은 viewportScrollX/Y 다. actualScroll* 은 없다(2026-08-11 실측: 스크롤해도
  // 세로 눈금이 따라오지 않았다).
  const viewport = render.scene.getViewports()[0] as unknown as
    { viewportScrollX?: number; viewportScrollY?: number };
  const box = canvas.getBoundingClientRect();
  const scale = render.scene.scaleX || 1;

  // 종이는 캔버스 한가운데 놓인다. 화면 엔진이 들고 있는 자리(paper.left)를
  // 그대로 쓰지 않는 이유: 문서에 따라 -10000 같은 안쪽 자리가 그대로 나와
  // 눈금자와 표 손잡이가 화면 밖으로 나갔다(2026-08-12 실측).
  const paperLeft = box.left + paperOffset(box.width, documentPageStyle().pageWidth, scale);
  const paperTop = box.top + (PAGE_GAP / 2 - (viewport?.viewportScrollY ?? 0)) * scale;

  return {
    x: paperLeft - (viewport?.viewportScrollX ?? 0) * scale,
    y: paperTop,
    scale,
    canvas: { left: box.left, top: box.top, width: box.width, height: box.height },
  };
}

/**
 * 왼쪽 아래에 "지금 몇 쪽 / 모두 몇 쪽" 을 보인다.
 *
 * <p>쪽 수를 모르면 인쇄해서 올릴 문서인지 가늠할 수 없다(2026-08-19 지적).
 * 워드도 왼쪽 아래에 같은 것을 보인다.
 */
let pageCountBox: HTMLDivElement | null = null;

function pageCountLabel(): HTMLDivElement {
  if (pageCountBox == null) {
    pageCountBox = document.createElement('div');
    pageCountBox.id = 'pageCount';
    pageCountBox.style.position = 'fixed';
    pageCountBox.style.zIndex = '1074';
    pageCountBox.style.padding = '2px 8px';
    pageCountBox.style.borderRadius = '3px';
    pageCountBox.style.background = 'transparent';
    pageCountBox.style.color = DOC_ACCENT_DARK;
    pageCountBox.style.fontWeight = '600';
    pageCountBox.style.font = '12px sans-serif';
    pageCountBox.style.pointerEvents = 'none';
    document.body.appendChild(pageCountBox);
  }
  return pageCountBox;
}

/** 쪽 수를 세어 왼쪽 아래에 적는다. 문서가 아니면 감춘다. */
function showPageCount(unitId: string, place: PagePlace) {
  const label = pageCountLabel();
  if (openDocSeq == null) {
    label.hidden = true;
    return;
  }
  const render = univer.__getInjector().get(IRenderManagerService).getRenderUnitById(unitId);
  const paper = render?.mainComponent as unknown as {
    pageMarginTop?: number;
    getSkeleton?: () => { getSkeletonData?: () => DocSkeletonData };
  } | undefined;
  const pages = paper?.getSkeleton?.()?.getSkeletonData?.()?.pages;
  if (pages == null || pages.length === 0) {
    label.hidden = true;
    return;
  }

  // 쪽은 세로로 쌓인다. 화면 한가운데가 어느 쪽에 걸리는지 센다.
  const gap = paper?.pageMarginTop ?? 17;
  const middle = place.canvas.top + place.canvas.height / 2;
  let top = place.y;
  let current = 1;
  for (const [at, page] of pages.entries()) {
    const height = page.pageHeight * place.scale;
    if (middle < top + height || at === pages.length - 1) {
      current = at + 1;
      break;
    }
    top += height + gap * place.scale;
  }

  label.hidden = false;
  label.textContent = `${current} / ${pages.length}쪽`;

  // 아래 띠(footer) 왼쪽에 앉힌다. 종이 위에 띄우면 글을 가린다(2026-08-19 요청).
  const foot = document.querySelector('#app footer')?.getBoundingClientRect();
  if (foot == null) {
    label.hidden = true;
    return;
  }
  label.style.left = `${foot.left + 12}px`;
  label.style.top = `${foot.top + foot.height / 2 - 10}px`;
}

/** 눈금자를 편집 캔버스 가장자리에 붙인다. */
function placeRulers(page: PagePlace) {
  const size = 20;
  rulerHEl.style.left = `${page.canvas.left}px`;
  rulerHEl.style.top = `${page.canvas.top}px`;
  rulerHEl.style.width = `${page.canvas.width}px`;

  rulerVEl.style.left = `${page.canvas.left}px`;
  rulerVEl.style.top = `${page.canvas.top + size}px`;
  rulerVEl.style.height = `${Math.max(0, page.canvas.height - size)}px`;
}

/** 눈금 하나를 그린다. 가로·세로가 같은 자를 방향만 바꿔 쓴다. */
function drawRuler(canvas: HTMLCanvasElement, axis: 'H' | 'V', page: PagePlace) {
  const box = canvas.getBoundingClientRect();
  const dpr = window.devicePixelRatio || 1;
  // 크기가 그대로면 건드리지 않는다. width 를 넣는 것만으로 캔버스가 다시 만들어진다.
  const width = Math.max(1, Math.round(box.width * dpr));
  const height = Math.max(1, Math.round(box.height * dpr));
  if (canvas.width !== width || canvas.height !== height) {
    canvas.width = width;
    canvas.height = height;
  }

  const context = canvas.getContext('2d');
  if (!context) {
    return;
  }
  context.setTransform(dpr, 0, 0, dpr, 0, 0);
  context.clearRect(0, 0, box.width, box.height);

  const horizontal = axis === 'H';
  const thickness = horizontal ? box.height : box.width;
  // 종이 시작점을 눈금자 안 좌표로 옮긴다.
  const paperAt = (horizontal ? page.x - box.left : page.y - box.top);
  // 문서에 적힌 종이와 여백으로 잰다. 우리 기본(A4)으로 재면 레터 문서에서 눈금이
  // 종이와 어긋난다(2026-08-13 지적).
  const paper = documentPageStyle();
  const paperLength = (horizontal ? paper.pageWidth : paper.pageHeight) * page.scale;
  const margin = (horizontal ? paper.marginLeft : paper.marginTop) * page.scale;

  // 여백은 흐리게 깔아 본문 폭이 한눈에 보이게 한다. 워드·한글의 눈금자가 그렇다.
  const tailMargin = (horizontal ? paper.marginRight : paper.marginBottom) * page.scale;
  const tailAt = paperAt + paperLength - tailMargin;
  context.fillStyle = '#e6e9eb';
  if (horizontal) {
    context.fillRect(paperAt, 0, margin, thickness);
    context.fillRect(tailAt, 0, tailMargin, thickness);
  } else {
    context.fillRect(0, paperAt, thickness, margin);
    context.fillRect(0, tailAt, thickness, tailMargin);
  }

  context.strokeStyle = '#9aa3ab';
  context.fillStyle = '#5f6b73';
  context.font = '9px sans-serif';
  context.textAlign = 'center';
  context.textBaseline = 'middle';

  for (const tick of rulerTicks(paperLength, margin, page.scale)) {
    const at = paperAt + tick.px;
    if (at < 0 || at > (horizontal ? box.width : box.height)) {
      continue;
    }
    if (tick.label != null) {
      // 0 자리에는 숫자를 적지 않는다 - 본문 시작은 눈으로 이미 보인다.
      if (tick.label !== '0') {
        context.fillText(tick.label, horizontal ? at : thickness / 2,
          horizontal ? thickness / 2 : at);
      }
      continue;
    }
    context.beginPath();
    if (horizontal) {
      context.moveTo(at, thickness / 2 - 2);
      context.lineTo(at, thickness / 2 + 2);
    } else {
      context.moveTo(thickness / 2 - 2, at);
      context.lineTo(thickness / 2 + 2, at);
    }
    context.stroke();
  }
}

function blankDoc(workbookSeq: number) {
  return {
    id: `wo-doc-${workbookSeq}`,
    body: toBody([]),
    documentStyle: pageStyle(),
    tableSource: {},
    drawings: {},
    drawingsOrder: [],
    headers: {},
    footers: {},
  };
}

/**
 * 종이 모양으로 그린다.
 *
 * <p>기본은 폭이 화면을 꽉 채우는 모양(MODERN)이다. 인쇄해서 결재를 올리는 문서는
 * 화면에서도 종이여야 한다 - 한 쪽에 어디까지 들어가는지가 곧 문서의 모양이다.
 */
/** 머릿글·꼬릿글이 종이 가장자리에서 떨어지는 거리(px). 워드 기본은 0.5인치다. */
const EDGE_MARGIN = 48;

function pageStyle(page?: DocPageSetup | null) {
  // 가져온 문서에 적힌 종이와 여백을 그대로 쓴다. 없으면 우리 기본(A4)이다 -
  // 여백이 다르면 본문 폭이 달라져 표와 그림이 원본과 다른 자리에 앉는다
  // (2026-08-13 지적: 표 크기가 다르다).
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

async function fetchSheets(workbookSeq: number): Promise<SheetSummary[]> {
  const response = await fetch(`/api/workbooks/${workbookSeq}/sheets`,
    { credentials: 'same-origin' });
  if (!response.ok) {
    throw new Error('문서를 열 수 없습니다.');
  }
  return response.json();
}

// iframe 임베드는 선택이다(스펙 3절 재결정). 독립 창이 기본이고 부모와 통신할
// 일이 없다. iframe 으로 띄운 경우에만 알리며, 대상 origin 을 반드시 명시한다.
// '*' 로 보내면 어느 사이트가 감싸도 메시지를 받는다.
/**
 * 우리가 만든 명령의 id.
 *
 * <p>쓰는 곳보다 <b>앞</b>에 둔다. 뒤에 두면 번들러가 const 를 끌어올려 등록 시점에
 * undefined 가 되고, 둘 다 undefined 라 "이미 등록된 명령" 오류가 났다
 * (2026-08-11 콘솔 실측). 시트 이동·시트 넣기 단축키가 통째로 죽어 있었다.
 */
const LINE_SPACING_MENU = 'weboffice.menu.line-spacing';
const LINE_SPACING_ICON = 'WoLineSpacingIcon';

const SWITCH_SHEET_COMMAND = 'weboffice.command.switch-sheet';
const ADD_SHEET_COMMAND = 'weboffice.command.add-sheet';

/** 조건부 서식 패널을 여는 Univer 명령. value 로 어느 규칙인지 고른다. */
const CF_PANEL = 'sheet.operation.open.conditional.formatting.panel';

/**
 * 문서 종류가 정해진 뒤의 준비.
 *
 * <p>Univer 를 만들고, 그 종류에 맞는 메뉴·단축키·구독을 건다. 시트 단축키는
 * 시트에서만 건다 - 글 문서에 걸면 엔터와 글자 입력을 가로챈다.
 */
function startEditor(kind: DocKind) {
  // 그리드를 먼저 보인 뒤에 만든다. 숨긴 채로 만들면 Univer 가 캔버스를 0 크기로
  // 잡아 흰 화면만 남는다(2026-08-11 실측).
  documentListEl.hidden = true;
  appEl.hidden = false;
  toolbarLeft.hidden = false;
  workbookTitleEl.hidden = false;

  startUniver(kind);
  if (kind === 'SHEET') {
    // 빈 워크북을 먼저 만든다. 시트 UI(리본·수식 입력줄)는 유닛이 하나 있어야
    // 뜬다 - 없이 두면 문서를 열어도 흰 화면에 되돌리기 단추만 남는다
    // (2026-08-11 실측).
    univerAPI.createWorkbook({});
    installSheetEvents();
    installSheetCommandWatch();
    installSizeHint();
    installShortcuts();
    installOneMenuAtATime();
  } else {
    installDocCommandWatch();
    installLineSpacingMenu();
    installOneMenuAtATime();
    installWordPick();
    // 색 팝업이 닫히면서 포커스를 body 에 떨군다. 그 상태에서는 키보드가 아무
    // 데도 닿지 않아 글자가 입력되지 않는다(2026-08-11 실측).
    keepEditorFocus(() => (openDocSeq == null ? null : `__editor_wo-doc-${openDocSeq}`));
    // 색을 고르고 나면 팔레트는 닫히는데 마우스가 단추 위에 남아 말풍선만 떠
    // 있었다. 단추에 떠났다고 대신 알린다.
    dismissTooltipAfterClick(() => appEl.querySelector('[class*="univer-toolbar"]'));
  }
  renderMenuBar(kind);
  installSaveShortcut();
}

/**
 * 상단 메뉴바를 채운다.
 *
 * <p>[홈] 은 메뉴가 아니라 단추다 - 자리를 뜨는 한 가지 일만 한다. 나머지는
 * 펼치는 메뉴다.
 *
 * <p>Univer 명령을 그대로 부른다. 우리가 같은 일을 다시 구현하면 서식·권한·되돌리기
 * 같은 것이 저쪽과 어긋난다.
 *
 * <p>쓸 수 있는지는 펼칠 때마다 다시 묻는다(menuBar.ts). 시작 화면에서는 문서에
 * 대고 하는 것들이 흐려진다.
 */
function renderMenuBar(kind: DocKind) {
  // 메뉴를 갈아 끼우는 자리가 곧 종류가 바뀌는 자리다. 표시를 여기서 함께 맞춘다.
  showDocKind(kind);

  const run = (id: string, params?: () => object) =>
    () => { void commandService().executeCommand(id, params?.()); };

  // 파일 메뉴는 종류를 안 가린다. 새로 만들고 여는 일은 어느 문서에서나 같다.
  const fileMenu: MenuDefinition = {
    key: 'file',
    label: '파일',
    items: [
      { label: '새 시트', icon: menuIcons.newDoc, run: () => createWorkbookAndOpen('SHEET') },
      { label: '새 문서', icon: menuIcons.newDoc, run: () => createWorkbookAndOpen('DOC') },
      { label: '열기…', icon: menuIcons.open, run: () => { void leaveDocument('/'); } },
      { label: '시트 가져오기 (xlsx)…', icon: menuIcons.upload, run: () => fileInput.click() },
      { label: '문서 가져오기 (docx, hwp)…', icon: menuIcons.upload,
        run: () => docInput.click() },
      { label: '버전 기록', icon: menuIcons.find, separated: true, keepFocus: true,
        enabled: () => (kind === 'DOC' ? openDocSeq != null : open != null),
        run: () => { void showVersions(); } },
      { label: kind === 'DOC' ? '다운로드 (docx)' : '다운로드 (xlsx)',
        icon: menuIcons.download, separated: true,
        run: () => (kind === 'DOC' ? downloadDoc() : downloadWorkbook()),
        enabled: () => (kind === 'DOC' ? openDocSeq != null : open != null) },
      ...(kind === 'DOC' ? [{ label: '다운로드 (hwpx)', icon: menuIcons.download,
        run: downloadDocHwpx, enabled: () => openDocSeq != null }] : []),
    ],
  };

  if (kind === 'DOC') {
    createMenuBar(menuBarEl, docMenus(run, fileMenu), { afterRun: focusGrid });
    return;
  }

  const menus: MenuDefinition[] = [
    {
      key: 'file',
      label: '파일',
      items: [
        { label: '새 문서', icon: menuIcons.newDoc, run: createWorkbookAndOpen },
        { label: '열기…', icon: menuIcons.open, run: () => { void leaveDocument('/'); } },
        { label: '시트 가져오기 (xlsx)…', icon: menuIcons.upload, run: () => fileInput.click() },
        { label: '버전 기록', icon: menuIcons.find, enabled: () => open != null,
          separated: true, keepFocus: true, run: () => { void showVersions(); } },
        { label: '다운로드 (xlsx)', icon: menuIcons.download, run: downloadWorkbook,
          enabled: () => open != null, separated: true },
      ],
    },
    {
      key: 'edit',
      label: '수정',
      items: [
        { label: '실행 취소', icon: menuIcons.undo, run: run(UndoCommand.id),
          enabled: () => open != null },
        { label: '다시 실행', icon: menuIcons.redo, run: run(RedoCommand.id),
          enabled: () => open != null },
        // 그냥 '붙여넣기' 는 넣지 않는다. 브라우저가 시스템 클립보드 읽기를 막으면
        // 눌러도 아무 일이 없고, 그러면 고장으로 보인다. Ctrl+V 는 그대로 된다.
        { label: '값만 붙여넣기', icon: menuIcons.paste, enabled: canWrite, separated: true,
          run: run(SheetPasteValueCommand.id) },
        { label: '서식만 붙여넣기', icon: menuIcons.paste, enabled: canWrite,
          run: run(SheetPasteFormatCommand.id) },
        { label: '내용 지우기', icon: menuIcons.clear, enabled: canWrite, separated: true,
          run: run(ClearSelectionContentCommand.id) },
        { label: '서식 지우기', icon: menuIcons.clear, enabled: canWrite,
          run: run(ClearSelectionFormatCommand.id) },
        { label: '셀 지우고 왼쪽으로 밀기', icon: menuIcons.colRemove, enabled: canWrite,
          separated: true, run: run(DeleteRangeMoveLeftCommand.id) },
        { label: '셀 지우고 위로 밀기', icon: menuIcons.rowRemove, enabled: canWrite,
          run: run(DeleteRangeMoveUpCommand.id) },
        { label: '이 칸 수정 기록', icon: menuIcons.find, enabled: () => open != null,
          separated: true, keepFocus: true, run: () => { void showCellHistory(); } },
        { label: '찾기  (Ctrl+F)', icon: menuIcons.find, enabled: () => open != null,
          separated: true, run: run('ui.operation.open-find-dialog') },
        { label: '찾기 및 바꾸기  (Ctrl+H)', icon: menuIcons.find, enabled: canWrite,
          run: run('ui.operation.open-replace-dialog') },
      ],
    },
    {
      key: 'view',
      label: '보기',
      items: [
        { label: '첫 행 고정', icon: menuIcons.freezeRow, enabled: canWrite,
          run: () => freezeTo(1, null) },
        { label: '두 행 고정', icon: menuIcons.freezeRow, enabled: canWrite,
          run: () => freezeTo(2, null) },
        { label: '고른 칸 위까지 행 고정', icon: menuIcons.freezeRow, enabled: canWrite,
          run: () => freezeTo(selectedStart().row, null) },
        { label: '행 고정 없음', icon: menuIcons.freezeNone, enabled: canWrite,
          run: () => freezeTo(0, null) },
        { label: '첫 열 고정', icon: menuIcons.freezeColumn, enabled: canWrite, separated: true,
          run: () => freezeTo(null, 1) },
        { label: '두 열 고정', icon: menuIcons.freezeColumn, enabled: canWrite,
          run: () => freezeTo(null, 2) },
        { label: '고른 칸 왼쪽까지 열 고정', icon: menuIcons.freezeColumn, enabled: canWrite,
          run: () => freezeTo(null, selectedStart().column) },
        { label: '열 고정 없음', icon: menuIcons.freezeNone, enabled: canWrite,
          run: () => freezeTo(null, 0) },
        { label: '숨긴 행·열 모두 보이기', icon: menuIcons.unhide, enabled: canWrite,
          separated: true, run: unhideAll },
        { label: '전체 화면', icon: menuIcons.fullscreen, run: toggleFullscreen,
          separated: true },
      ],
    },
    {
      key: 'insert',
      label: '삽입',
      items: [
        { label: '그림…', icon: menuIcons.image, enabled: canWrite,
          run: run('sheet.command.insert-float-image') },
        { label: '시트  (Shift+F11)', icon: menuIcons.sheet, run: () => { void addSheet(); },
          enabled: canWrite },
        // '위'·'왼쪽' 은 몇 줄을 넣을지 인자로 받는다. 안 주면 터진다
        // (2026-08-10 지적: 위에 행 넣기가 안 먹었다). '아래'·'오른쪽' 은 선택에서
        // 스스로 셈하므로 인자가 없다.
        { label: '위에 행 넣기', icon: menuIcons.rowAbove, enabled: canWrite, separated: true,
          run: run(InsertRowBeforeCommand.id, () => ({ value: selectedSize().rows })) },
        { label: '아래에 행 넣기', icon: menuIcons.rowBelow,
          run: run(InsertRowAfterCommand.id), enabled: canWrite },
        { label: '왼쪽에 열 넣기', icon: menuIcons.colLeft, enabled: canWrite,
          run: run(InsertColBeforeCommand.id, () => ({ value: selectedSize().columns })) },
        { label: '오른쪽에 열 넣기', icon: menuIcons.colRight,
          run: run(InsertColAfterCommand.id), enabled: canWrite },
        { label: '행 지우기', icon: menuIcons.rowRemove, run: run(RemoveRowCommand.id),
          enabled: canWrite, separated: true },
        { label: '열 지우기', icon: menuIcons.colRemove, run: run(RemoveColCommand.id),
          enabled: canWrite },
        { label: '링크…', icon: menuIcons.link, enabled: canWrite, separated: true,
          run: run('sheet.operation.insert-hyper-link') },
        { label: '메모', icon: menuIcons.note, enabled: canWrite,
          run: run('sheet.operation.add-note-popup') },
        { label: '댓글', icon: menuIcons.comment, enabled: canWrite,
          run: run('sheet.operation.show-comment-modal') },
        { label: '댓글 목록', icon: menuIcons.comment, enabled: () => open != null,
          run: run('sheet.operation.toggle-comment-panel') },
      ],
    },
    {
      key: 'format',
      label: '서식',
      items: [
        { label: '왼쪽 맞춤', icon: menuIcons.alignLeft, enabled: canWrite,
          run: run(SetHorizontalTextAlignCommand.id, () => ({ value: HorizontalAlign.LEFT })) },
        { label: '가운데 맞춤', icon: menuIcons.alignCenter, enabled: canWrite,
          run: run(SetHorizontalTextAlignCommand.id, () => ({ value: HorizontalAlign.CENTER })) },
        { label: '오른쪽 맞춤', icon: menuIcons.alignRight, enabled: canWrite,
          run: run(SetHorizontalTextAlignCommand.id, () => ({ value: HorizontalAlign.RIGHT })) },
        { label: '위로 맞춤', icon: menuIcons.alignTop, enabled: canWrite, separated: true,
          run: run(SetVerticalTextAlignCommand.id, () => ({ value: VerticalAlign.TOP })) },
        { label: '가운데로 맞춤', icon: menuIcons.alignMiddle, enabled: canWrite,
          run: run(SetVerticalTextAlignCommand.id, () => ({ value: VerticalAlign.MIDDLE })) },
        { label: '아래로 맞춤', icon: menuIcons.alignBottom, enabled: canWrite,
          run: run(SetVerticalTextAlignCommand.id, () => ({ value: VerticalAlign.BOTTOM })) },
        { label: '자동 줄바꿈', icon: menuIcons.wrap, enabled: canWrite, separated: true,
          run: run(SetTextWrapCommand.id, () => ({ value: WrapStrategy.WRAP })) },
        { label: '넘치게 두기', icon: menuIcons.overflow, enabled: canWrite,
          run: run(SetTextWrapCommand.id, () => ({ value: WrapStrategy.OVERFLOW })) },
        { label: '넘치면 자르기', icon: menuIcons.clip, enabled: canWrite,
          run: run(SetTextWrapCommand.id, () => ({ value: WrapStrategy.CLIP })) },
        { label: '45도 기울이기', icon: menuIcons.rotate, enabled: canWrite, separated: true,
          run: run(SetTextRotationCommand.id, () => ({ value: 45 })) },
        { label: '세로로 쓰기', icon: menuIcons.rotate, enabled: canWrite,
          run: run(SetTextRotationCommand.id, () => ({ value: 'v' })) },
        { label: '기울이기 없음', icon: menuIcons.rotate, enabled: canWrite,
          run: run(SetTextRotationCommand.id, () => ({ value: 0 })) },
        // value 로 어느 규칙을 들고 패널을 여는지 고른다. 1=새 규칙, 2=규칙 목록,
        // 6=색 눈금, 7=데이터 막대, 9=고른 칸의 규칙 지우기.
        { label: '표로 만들기', icon: menuIcons.table, enabled: canWrite, separated: true,
          run: addTable },
        { label: '조건부 서식 넣기…', icon: menuIcons.conditional, enabled: canWrite,
          separated: true, run: run(CF_PANEL, () => ({ value: 1 })) },
        { label: '색 눈금…', icon: menuIcons.conditional, enabled: canWrite,
          run: run(CF_PANEL, () => ({ value: 6 })) },
        { label: '데이터 막대…', icon: menuIcons.conditional, enabled: canWrite,
          run: run(CF_PANEL, () => ({ value: 7 })) },
        { label: '조건부 서식 목록…', icon: menuIcons.conditional, enabled: canWrite,
          run: run(CF_PANEL, () => ({ value: 2 })) },
        { label: '고른 칸의 조건부 서식 지우기', icon: menuIcons.clear, enabled: canWrite,
          run: run(CF_PANEL, () => ({ value: 9 })) },
      ],
    },
    {
      key: 'formula',
      label: '수식',
      items: [
        // 함수 전체 목록 대화상자. Univer 의 수식 탭이 여는 것과 같은 것이다.
        { label: '함수…', icon: menuIcons.formula, enabled: canWrite,
          run: run('formula-ui.operation.more-functions') },
      ],
    },
    {
      key: 'data',
      label: '데이터',
      items: [
        { label: '오름차순 정렬', icon: menuIcons.sortAsc, enabled: canWrite,
          run: run('sheet.command.sort-range-asc') },
        { label: '내림차순 정렬', icon: menuIcons.sortDesc, enabled: canWrite,
          run: run('sheet.command.sort-range-desc') },
        { label: '머리글 빼고 오름차순', icon: menuIcons.sortAsc, enabled: canWrite,
          run: run('sheet.command.sort-range-asc-ext') },
        { label: '머리글 빼고 내림차순', icon: menuIcons.sortDesc, enabled: canWrite,
          run: run('sheet.command.sort-range-desc-ext') },
        { label: '정렬 기준 정하기…', icon: menuIcons.sortAsc, enabled: canWrite,
          run: run('sheet.command.sort-range-custom') },
        { label: '필터 켜고 끄기', icon: menuIcons.filter, enabled: canWrite, separated: true,
          run: run('sheet.command.smart-toggle-filter') },
        { label: '필터 조건 지우기', icon: menuIcons.filter, enabled: canWrite,
          run: run('sheet.command.clear-filter-criteria') },
        // 인자가 없으면 조용히 아무 일도 안 한다(if (!params) return false).
        // isAdd 는 "새 규칙을 만드는 중" 이라는 뜻이다.
        { label: '데이터 확인…', icon: menuIcons.check, enabled: canWrite, separated: true,
          run: run('data-validation.operation.open-validation-panel', () => ({ isAdd: true })) },
        { label: '데이터 확인 모두 지우기', icon: menuIcons.clear, enabled: canWrite,
          run: run('sheet.command.remove-all-data-validation') },
        { label: '텍스트를 숫자로', icon: menuIcons.toNumber, run: run(TextToNumberCommand.id),
          enabled: canWrite, separated: true },
      ],
    },
  ];
  createMenuBar(menuBarEl, menus, { afterRun: focusGrid });
}

/**
 * 포커스를 격자(또는 글)로 돌려준다.
 *
 * <p>메뉴 단추에 남으면 Univer 단축키가 안 듣는다 - 그쪽은 포커스가 편집 화면 안일
 * 때만 키를 받는다(2026-08-10 지적: 행을 넣고 Ctrl+Z 를 눌러도 아무 일이 없었다).
 */
function focusGrid() {
  univer.__getInjector().get(ILayoutService).focus();
}

/**
 * 글 문서 메뉴.
 *
 * <p>구글 독스의 얼개를 따른다 - 파일·수정·보기·삽입·서식. 시트 것을 그대로 두면
 * "행 넣기"·"정렬"·"조건부 서식" 처럼 글에서 뜻이 없는 것이 남는다(2026-08-11 지적).
 *
 * <p>Univer 문서 명령 가운데 <b>실제로 있는 것만</b> 넣는다. 이미지·링크·댓글은
 * 시트 쪽 플러그인이라 글에서는 붙지 않는다 - 넣어 두면 눌러 보고 고장으로 여긴다.
 */
function docMenus(run: (id: string, params?: () => object) => () => void,
                  fileMenu: MenuDefinition): MenuDefinition[] {
  return [
    fileMenu,
    {
      key: 'edit',
      label: '수정',
      items: [
        { label: '실행 취소', icon: menuIcons.undo, run: run(UndoCommand.id),
          enabled: () => openDocSeq != null },
        { label: '다시 실행', icon: menuIcons.redo, run: run(RedoCommand.id),
          enabled: () => openDocSeq != null },
        { label: '전체 선택', icon: menuIcons.check, run: run('doc.command.select-all'),
          enabled: () => openDocSeq != null, separated: true },
        { label: '찾기  (Ctrl+F)', icon: menuIcons.find, enabled: () => openDocSeq != null,
          separated: true, run: run('ui.operation.open-find-dialog') , keepFocus: true },
        { label: '찾기 및 바꾸기  (Ctrl+H)', icon: menuIcons.find, enabled: canWrite,
          run: run('ui.operation.open-replace-dialog') , keepFocus: true },
      ],
    },
    {
      key: 'view',
      label: '보기',
      items: [
        { label: '머리글·바닥글', icon: menuIcons.note, enabled: canWrite,
          run: run('doc.command.open-header-footer-panel') , keepFocus: true },
        { label: '페이지 설정…', icon: menuIcons.sheet, enabled: canWrite,
          run: run('docs.operation.open-page-setting') , keepFocus: true },
        { label: '전체 화면', icon: menuIcons.fullscreen, run: toggleFullscreen,
          separated: true },
      ],
    },
    {
      key: 'insert',
      label: '삽입',
      items: [
        // 표 얼개는 문단 표에 담기지 않는다. 문서 한 벌을 통째로 담게 된 뒤에야
        // 다시 열 수 있었다 - 그 전에는 칸 경계 제어문자만 글자로 남았다.
        { label: '표 (3×3)', icon: menuIcons.table, enabled: canWrite,
          run: run('doc.command.create-table', () => ({ rowCount: 3, colCount: 3 })) },
        { label: '그림…', icon: menuIcons.image, enabled: canWrite,
          run: run('doc.command.insert-float-image') },
        { label: '가로줄', icon: menuIcons.overflow, enabled: canWrite,
          run: run('doc.command.horizontal-line') },
        // 표를 늘리고 줄이는 자리. 커서가 표 안에 있을 때만 먹는다 - 밖에서
        // 눌러도 아무 일도 안 일어난다. 표 안인지를 우리가 다시 세지 않는 이유다.
        { label: '위에 줄 넣기', icon: menuIcons.rowAbove, enabled: canWrite, separated: true,
          run: run('doc.command.table-insert-row-above') },
        { label: '아래에 줄 넣기', icon: menuIcons.rowBelow, enabled: canWrite,
          run: run('doc.command.table-insert-row-bellow') },
        { label: '왼쪽에 칸 넣기', icon: menuIcons.colLeft, enabled: canWrite,
          run: run('doc.command.table-insert-column-left') },
        { label: '오른쪽에 칸 넣기', icon: menuIcons.colRight, enabled: canWrite,
          run: run('doc.command.table-insert-column-right') },
        { label: '줄 지우기', icon: menuIcons.rowRemove, enabled: canWrite, separated: true,
          run: run('doc.table.delete-rows') },
        { label: '칸 지우기', icon: menuIcons.colRemove, enabled: canWrite,
          run: run('doc.table.delete-columns') },
        { label: '표 지우기', icon: menuIcons.clear, enabled: canWrite,
          run: run('doc.table.delete-table') },
      ],
    },
    {
      key: 'format',
      label: '서식',
      items: [
        { label: '굵게', icon: menuIcons.alignLeft, enabled: canWrite,
          run: run('doc.command.set-inline-format-bold') },
        { label: '기울임', icon: menuIcons.rotate, enabled: canWrite,
          run: run('doc.command.set-inline-format-italic') },
        { label: '밑줄', icon: menuIcons.alignBottom, enabled: canWrite,
          run: run('doc.command.set-inline-format-underline') },
        { label: '취소선', icon: menuIcons.clear, enabled: canWrite,
          run: run('doc.command.set-inline-format-strikethrough') },
        { label: '제목 1', icon: menuIcons.newDoc, enabled: canWrite, separated: true,
          run: run('doc.command.h1-heading') },
        { label: '제목 2', icon: menuIcons.newDoc, enabled: canWrite,
          run: run('doc.command.h2-heading') },
        { label: '제목 3', icon: menuIcons.newDoc, enabled: canWrite,
          run: run('doc.command.h3-heading') },
        { label: '본문', icon: menuIcons.newDoc, enabled: canWrite,
          run: run('doc.command.normal-text-heading') },
        { label: '왼쪽 맞춤', icon: menuIcons.alignLeft, enabled: canWrite, separated: true,
          run: run('doc.command.align-left') },
        { label: '가운데 맞춤', icon: menuIcons.alignCenter, enabled: canWrite,
          run: run('doc.command.align-center') },
        { label: '오른쪽 맞춤', icon: menuIcons.alignRight, enabled: canWrite,
          run: run('doc.command.align-right') },
        { label: '양쪽 맞춤', icon: menuIcons.alignCenter, enabled: canWrite,
          run: run('doc.command.align-justify') },
        ...LINE_SPACINGS.map((one, at) => ({
          label: `줄 간격 ${one.label}`,
          icon: menuIcons.lineSpacing,
          enabled: canWrite,
          separated: at === 0,
          // 지금 값에 표시를 남긴다. 없으면 무엇이 걸려 있는지 알 수 없다.
          checked: () => currentLineSpacing() === one.value,
          run: () => setLineSpacing(one.value),
        })),
        { label: '글머리 기호', icon: menuIcons.wrap, enabled: canWrite, separated: true,
          run: run('doc.command.bullet-list') },
        { label: '번호 매기기', icon: menuIcons.toNumber, enabled: canWrite,
          run: run('doc.command.order-list') },
        { label: '확인 목록', icon: menuIcons.check, enabled: canWrite,
          run: run('doc.command.check-list') },
      ],
    },
  ];
}

/**
 * 지금 고른 칸이 몇 행 몇 열인지.
 *
 * <p>엑셀은 세 줄을 고르고 "위에 넣기" 를 하면 세 줄을 넣는다. 고른 것이 없으면
 * 한 줄로 본다.
 */
function selectedSize(): { rows: number; columns: number } {
  const range = univerAPI.getActiveWorkbook()?.getActiveSheet()?.getSelection()?.getActiveRange();
  return {
    rows: Math.max(1, range?.getHeight() ?? 1),
    columns: Math.max(1, range?.getWidth() ?? 1),
  };
}

/** 서버 값을 Univer 스냅샷 모양으로. 붙박이가 없으면 startRow/Column 을 -1 로 둔다. */
function toFreeze(rowNo: number, columnNo: number) {
  return {
    ySplit: rowNo,
    xSplit: columnNo,
    startRow: rowNo > 0 ? rowNo : -1,
    startColumn: columnNo > 0 ? columnNo : -1,
  };
}

/** 지금 고른 칸의 시작 자리. "여기까지 고정" 을 셈하는 데 쓴다. */
function selectedStart(): { row: number; column: number } {
  const range = univerAPI.getActiveWorkbook()?.getActiveSheet()?.getSelection()?.getActiveRange();
  const at = range?.getRange();
  return { row: at?.startRow ?? 0, column: at?.startColumn ?? 0 };
}

/**
 * 틀 고정을 걸고 서버에 남긴다.
 *
 * <p>{@code null} 은 "그쪽은 그대로" 다. 행만 고정하려다 열 고정이 풀리면 사용자는
 * 왜 풀렸는지 모른다.
 *
 * <p>화면을 먼저 바꾸고 저장한다. 저장이 늦어도 누른 대로 보여야 한다.
 */
function freezeTo(rowNo: number | null, columnNo: number | null) {
  if (!open) return;
  const sheet = open.sheet;
  const rows = rowNo ?? sheet.freezeRowNo ?? 0;
  const columns = columnNo ?? sheet.freezeColumnNo ?? 0;

  univerAPI.getActiveWorkbook()?.getActiveSheet()?.setFreeze(toFreeze(rows, columns));
  sheet.freezeRowNo = rows;
  sheet.freezeColumnNo = columns;

  void api.setFreeze(sheet.sheetSeq, rows, columns)
    .then(() => status(rows === 0 && columns === 0
      ? '틀 고정을 풀었습니다' : `틀 고정 ${rows}행 ${columns}열`))
    .catch((error) => status(messageOf(error)));
}

/**
 * 고른 칸을 표로 만든다.
 *
 * <p>Univer 명령이 어느 시트의 어느 칸인지 전부 인자로 받는다. 선택으로 떨어지지
 * 않으므로 우리가 채워 준다.
 */
function addTable() {
  const range = univerAPI.getActiveWorkbook()?.getActiveSheet()?.getSelection()?.getActiveRange();
  const at = range?.getRange();
  if (!at) {
    status('표로 만들 칸을 먼저 고르세요');
    return;
  }
  void commandService().executeCommand('sheet.command.add-table', {
    unitId: WORKBOOK_ID, subUnitId: SUB_UNIT_ID, range: at,
  });
}

/**
 * 전체 화면. 브라우저가 하는 일이라 Univer 명령이 없다.
 *
 * <p>실패해도 조용히 넘긴다 - 사용자 동작 없이 부르면 브라우저가 거절하는데,
 * 메뉴에서 부르는 것은 동작이 맞으므로 거절은 정책(iframe 등) 때문이다.
 */
function toggleFullscreen() {
  if (document.fullscreenElement) {
    void document.exitFullscreen().catch(() => status('전체 화면을 끄지 못했습니다'));
  } else {
    void document.documentElement.requestFullscreen()
      .catch(() => status('전체 화면으로 갈 수 없습니다'));
  }
}

/** 빈 문서를 만들어 연다. 시작 화면 타일과 같은 길을 쓴다. */
function createWorkbookAndOpen(docType: 'SHEET' | 'DOC' = 'SHEET') {
  busy('만드는 중…');
  void api.createWorkbook(undefined, docType)
    // 주소를 바꿔 들어간다. #app 을 숨긴 채로 그리면 Univer 가 캔버스를 0 크기로 잡는다.
    .then((created) => { location.href = `/?workbookSeq=${created.workbookSeq}`; })
    .catch((error) => status(messageOf(error)));
}

/**
 * 엑셀에서 손에 익은 단축키를 붙인다.
 *
 * <p>Univer 는 이동·선택·복사·되돌리기·채우기까지 이미 갖고 있다. 없는 것만 채운다
 * (2026-08-10 실측: 시트에는 굵게/기울임/밑줄 명령은 있는데 키가 안 걸려 있고,
 * 시트 오가기는 아예 없었다).
 *
 * <p>브라우저 기본 동작을 뺏는 것이라 Univer 의 단축키 서비스로 등록한다. 직접
 * keydown 을 듣지 않는다 - 그쪽은 셀을 편집 중인지, 대화상자가 떠 있는지, 포커스가
 * 이 앱에 있는지를 이미 가려 준다.
 */
/**
 * 도구 띠에 줄 간격을 붙인다.
 *
 * <p>Univer 리본에는 줄 간격이 없다. 「서식」 무리 끝에 우리 것을 하나 얹는다 -
 * 값마다 명령을 하나씩 둔다(메뉴 항목은 제 id 로 실행되므로 값을 따로 실을 수
 * 없다).
 */
function installLineSpacingMenu() {

  // 「서식」 메뉴와 같은 아이콘을 도구 띠에도 쓴다. 이름으로 부르려면 등록해야
  // 한다. 등록처는 IconManager 다 - Univer 1.0 부터 메뉴·도구 띠가 아이콘을
  // 여기서 찾는다(0.25 는 ComponentManager 였다). 안 바꾸면 자리는 잡히는데
  // 그림이 빈 채로 뜨고 "Icon ... does not exist" 만 남는다(2026-10-16 실측).
  univer.__getInjector().get(IconManager)
    .register(LINE_SPACING_ICON, (props: IIconProps) => createElement('span', {
      className: props.className,
      style: { display: 'inline-flex', ...props.style },
      onClick: props.onClick,
      dangerouslySetInnerHTML: { __html: menuIcons.lineSpacing },
    }));

  for (const one of LINE_SPACINGS) {
    commandService().registerCommand({
      id: lineSpacingCommandId(one.value),
      type: CommandType.COMMAND,
      handler: () => { setLineSpacing(one.value); return true; },
    });
  }

  const menus = univer.__getInjector().get(IMenuManagerService);
  let painted: number | null | undefined;
  const paint = () => {
    const now = shownSpacing();
    // 값이 그대로면 손대지 않는다. 명령이 돌 때마다 메뉴를 다시 얹으면 도구 띠가
    // 다시 그려지고, 그때 펼쳐져 있던 다른 목록이 닫히지 않아 두 개가 겹쳐
    // 보였다(2026-08-21 지적: 번호와 글머리가 같이 떴다).
    if (now === painted) {
      return;
    }
    painted = now;
    const children: Record<string, unknown> = {};
    for (const [at, one] of LINE_SPACINGS.entries()) {
      children[lineSpacingCommandId(one.value)] = {
        order: at,
        menuItemFactory: () => ({
          id: lineSpacingCommandId(one.value),
          type: MenuItemType.BUTTON,
          // 지금 값에 표시를 남긴다. Univer 하위 메뉴는 활성 표시를 그려 주지
          // 않아(2026-08-21 실측) 이름에 적는다.
          title: `줄 간격 ${one.label}${now === one.value ? '  ✓' : ''}`,
        }),
      };
    }
    menus.mergeMenu({
      [RibbonStartGroup.FORMAT]: {
        [LINE_SPACING_MENU]: {
          order: 20,
          menuItemFactory: () => ({
            id: LINE_SPACING_MENU,
            type: MenuItemType.SUBITEMS,
            // 도구 띠에는 아이콘만 둔다. 지금 값은 펼친 목록 안에서 짚어
            // 보인다(2026-08-21 지적).
            tooltip: '줄 간격',
            icon: LINE_SPACING_ICON,
          }),
          ...children,
        },
      },
    });
  };

  paint();
  // 커서를 옮기거나 줄 간격을 바꿀 때마다 다시 짚는다.
  commandService().onCommandExecuted(() => paint());
}

/** 값마다 명령을 하나씩 둔다. 메뉴 항목은 제 id 로 실행된다. */
function lineSpacingCommandId(spacing: number): string {
  return `weboffice.command.line-spacing.${spacing}`;
}

function installShortcuts() {
  const shortcuts = univer.__getInjector().get(IShortcutService);
  const CTRL = MetaKeys.CTRL_COMMAND;

  // 서식. 명령은 Univer 것을 그대로 쓰고 키만 잇는다.
  const format: Array<[string, number]> = [
    [SetRangeBoldCommand.id, KeyCode.B | CTRL],
    [SetRangeItalicCommand.id, KeyCode.I | CTRL],
    [SetRangeUnderlineCommand.id, KeyCode.U | CTRL],
    [SetRangeStrickThroughCommand.id, KeyCode.X | CTRL | MetaKeys.SHIFT],
  ];
  for (const [id, binding] of format) {
    shortcuts.registerShortcut({ id, binding, preconditions: () => canWrite() });
  }

  // 시트 넣기. 엑셀과 같은 Shift+F11 이다.
  commandService().registerCommand({
    id: ADD_SHEET_COMMAND,
    type: CommandType.COMMAND,
    handler: () => { void addSheet(); return true; },
  });
  shortcuts.registerShortcut({
    id: ADD_SHEET_COMMAND, binding: KeyCode.F11 | MetaKeys.SHIFT,
    preconditions: () => canWrite(),
  });

  // 시트 오가기. 엑셀과 같은 Ctrl+PageUp/PageDown 이다.
  //
  // KeyCode 에 PageUp/PageDown 이 없어 값을 그대로 쓴다. Univer 는 바인딩을
  // event.keyCode 로 만들므로 33·34 가 그 키다.
  const PAGE_UP = 33;
  const PAGE_DOWN = 34;
  commandService().registerCommand({
    id: SWITCH_SHEET_COMMAND,
    type: CommandType.COMMAND,
    handler: (_accessor: unknown, params?: { step?: number }) => {
      const next = open
        ? neighborSheet(open.sheets, open.sheet.sheetSeq, params?.step ?? 1)
        : null;
      if (!next) {
        return false;
      }
      // 탭을 누른 것과 같은 길로 보낸다. Univer 가 화면을 바꾸고 우리 onChangeTab 이
      // 뒤따라 구독을 옮긴다 - 두 길이 갈라지면 한쪽만 고쳐지는 함정이 생긴다.
      univerAPI.getActiveWorkbook()?.setActiveSheet(subUnitIdOf(next.sheetSeq));
      return true;
    },
  });
  shortcuts.registerShortcut({
    id: SWITCH_SHEET_COMMAND, binding: PAGE_DOWN | CTRL,
    staticParameters: { step: 1 }, preconditions: () => open != null,
  });
  shortcuts.registerShortcut({
    id: SWITCH_SHEET_COMMAND, binding: PAGE_UP | CTRL,
    staticParameters: { step: -1 }, preconditions: () => open != null,
  });
}

/**
 * Ctrl+S 를 가로챈다.
 *
 * <p>안 잡으면 브라우저의 "페이지 저장" 창이 뜬다. 표를 쓰던 사람이 습관으로 누르는
 * 키인데 엉뚱한 창이 뜨고, 그 창은 네이티브라 화면이 통째로 멈춘 것처럼 보인다.
 *
 * <p>문서는 이미 자동으로 저장된다. 그래도 아무 일도 안 일어나면 "저장이 안 됐나"
 * 싶으므로, 밀린 것을 즉시 밀어내고 저장됐다고 말해 준다.
 *
 * <p>Univer 단축키 서비스가 아니라 창에 직접 건다. 저쪽은 포커스가 격자 안에 있을
 * 때만 듣는다 - 문서 목록 화면이나 제목 칸에서 누른 Ctrl+S 는 그대로 새어 나간다.
 */
/**
 * 도구 띠 목록은 한 번에 하나만 보인다.
 *
 * <p>Univer 는 펼친 목록을 서로 닫아 주지 않는다. 하나를 펼친 채 옆 것을 누르면
 * 둘이 겹쳐 보였다(2026-08-21 지적: 번호와 글머리가 같이 떴다). 바깥을 눌러도
 * 안 닫힌다 - 캔버스가 눌림을 먹어 팝업 라이브러리(Radix)가 못 듣는다.
 *
 * <p>새로 열린 것만 남기고 나머지는 감춘다. 지우지는 않는다 - 지웠더니 도구 띠가
 * 통째로 사라졌다(React 가 들고 있는 자리다). 감춘 것이 다시 열리면 되살린다.
 */
function installOneMenuAtATime() {
  const isPopup = (node: Node): node is HTMLElement =>
    node instanceof HTMLElement && node.parentElement === document.body
    && node.firstElementChild?.hasAttribute('data-state') === true;

  const isOpen = (box: HTMLElement) =>
    box.firstElementChild?.getAttribute('data-state') === 'open';

  const keepOnly = (newest: HTMLElement) => {
    for (const box of [...document.body.children]) {
      if (isPopup(box) && box !== newest) {
        box.style.display = 'none';
      }
    }
    newest.style.display = '';
  };

  const watchState = (box: HTMLElement) => {
    new MutationObserver(() => {
      if (isOpen(box)) {
        keepOnly(box);
      }
    }).observe(box.firstElementChild as Element, {
      attributes: true, attributeFilter: ['data-state'],
    });
  };

  new MutationObserver((changes) => {
    for (const change of changes) {
      for (const node of change.addedNodes) {
        if (!isPopup(node)) {
          continue;
        }
        watchState(node);
        if (isOpen(node)) {
          keepOnly(node);
        }
      }
    }
  }).observe(document.body, { childList: true });
}

function installSaveShortcut() {
  window.addEventListener('keydown', (event) => {
    if (!(event.ctrlKey || event.metaKey) || event.key.toLowerCase() !== 's' || event.altKey) {
      return;
    }
    event.preventDefault();
    if (!open) {
      return;
    }
    void Promise.all([flushSave(), flushDrawingSave(), flushSizeSave(), flushMergeSave()])
      .then(() => status('저장했습니다'))
      .catch((error) => status(messageOf(error)));
  }, { capture: true });
}

/** 읽기 전용이면 서식 단축키가 듣지 않아야 한다. 눌리는데 아무 일도 안 나면 고장으로 보인다. */
/**
 * 지금 고칠 수 있는가.
 *
 * <p>연 것이 시트든 글이든 같다. 시트 상태(open)만 보다가 글 문서에서 메뉴가
 * 통째로 흐렸다 - 표·가로줄·머리글이 모두 눌리지 않았다(2026-08-11 실측).
 */
function canWrite(): boolean {
  return (open != null || openDocSeq != null) && myRole !== 'VIEWER';
}



const parentOrigin = params.get('parentOrigin');
if (parentOrigin) {
  parent.postMessage({ type: 'WO_READY' }, parentOrigin);
}
