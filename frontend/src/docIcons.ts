/**
 * 문서 종류 그림.
 *
 * <p>같은 그림을 시작 화면 타일, 문서 목록, 편집 화면 왼쪽 위, 브라우저 탭이
 * 함께 쓴다. 자리마다 다른 표시를 쓰면 같은 종류인 줄 모른다.
 *
 * <p>색은 종류를 가르는 첫 신호다 - 시트는 초록, 글은 파랑. 화면 테마(초록)와
 * 부딪히지 않게 파랑은 한 가지만 쓴다.
 */

export type DocKind = 'SHEET' | 'DOC' | 'SLIDE';

/** 파란 종이에 글줄. 글 문서 표시. */
export const DOC_ICON = '<svg viewBox="0 0 16 16" width="16" height="16"'
  + ' aria-hidden="true" data-kind="doc">'
  + '<rect x="1.5" y="1.5" width="13" height="13" rx="2.5" fill="#1a56a8"/>'
  + '<rect x="4" y="4.4" width="8" height="1.5" rx="0.6" fill="#ffffff"/>'
  + '<rect x="4" y="7.2" width="8" height="1.5" rx="0.6" fill="#ffffff" opacity="0.85"/>'
  + '<rect x="4" y="10" width="5" height="1.5" rx="0.6" fill="#ffffff" opacity="0.7"/></svg>';

/** 초록 격자. 시트 문서 표시. */
export const SHEET_ICON = '<svg viewBox="0 0 16 16" width="16" height="16"'
  + ' aria-hidden="true" data-kind="sheet">'
  + '<rect x="1.5" y="1.5" width="13" height="13" rx="2.5" fill="#0e7c4a"/>'
  + '<rect x="4" y="4.5" width="8" height="2" rx="0.6" fill="#ffffff" opacity="0.55"/>'
  + '<rect x="4" y="7.6" width="3.4" height="1.9" rx="0.6" fill="#ffffff"/>'
  + '<rect x="8.6" y="7.6" width="3.4" height="1.9" rx="0.6" fill="#ffffff"/>'
  + '<rect x="4" y="10.4" width="3.4" height="1.9" rx="0.6" fill="#ffffff"/>'
  + '<rect x="8.6" y="10.4" width="3.4" height="1.9" rx="0.6" fill="#ffffff"/></svg>';

/**
 * 네모 넷을 겹친 표시. 「전체」 거르개에 쓴다.
 *
 * <p>종류 그림과 달리 색이 없다 - 특정 종류를 뜻하지 않는다.
 */
export const ALL_ICON = '<svg viewBox="0 0 16 16" width="16" height="16"'
  + ' aria-hidden="true" data-kind="all" fill="none" stroke="currentColor"'
  + ' stroke-width="1.4">'
  + '<rect x="2.2" y="2.2" width="5" height="5" rx="1.2"/>'
  + '<rect x="8.8" y="2.2" width="5" height="5" rx="1.2"/>'
  + '<rect x="2.2" y="8.8" width="5" height="5" rx="1.2"/>'
  + '<rect x="8.8" y="8.8" width="5" height="5" rx="1.2"/></svg>';

/** 주황 종이에 슬라이드 틀. 발표 자료 표시. 시트(초록)·글(파랑)과 색으로 갈린다. */
export const SLIDE_ICON = '<svg viewBox="0 0 16 16" width="16" height="16"'
  + ' aria-hidden="true" data-kind="slide">'
  + '<rect x="1.5" y="1.5" width="13" height="13" rx="2.5" fill="#c2570f"/>'
  + '<rect x="3.6" y="4.4" width="8.8" height="5.6" rx="1" fill="#ffffff"/>'
  + '<rect x="5.6" y="11.2" width="4.8" height="1.2" rx="0.6" fill="#ffffff"'
  + ' opacity="0.8"/></svg>';

export function iconOf(kind: DocKind): string {
  if (kind === 'DOC') {
    return DOC_ICON;
  }
  return kind === 'SLIDE' ? SLIDE_ICON : SHEET_ICON;
}

/**
 * 브라우저 탭 아이콘 주소.
 *
 * <p>종류마다 파일을 두지 않는다. 그림이 이미 여기 있으니 그대로 감싼다 - 파일로
 * 나누면 그림을 고칠 때 한쪽만 고치고 지나간다.
 */
export function faviconOf(kind: DocKind): string {
  const svg = iconOf(kind).replace('<svg', '<svg xmlns="http://www.w3.org/2000/svg"');
  return `data:image/svg+xml,${encodeURIComponent(svg)}`;
}

/** 종류를 사람 말로. 그림만으로는 못 읽는 사람이 있다. */
export function kindLabel(kind: DocKind): string {
  return kind === 'DOC' ? '문서' : '시트';
}
