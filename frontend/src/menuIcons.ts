/**
 * 메뉴 항목 아이콘.
 *
 * <p>Univer 의 아이콘(@univerjs/icons)은 React 컴포넌트라 우리 DOM 에 그대로 넣을 수
 * 없다. 16픽셀 단색 SVG 를 직접 그린다 - 개수가 적고 모양이 단순해서 라이브러리를
 * 더 얹을 값이 안 된다.
 *
 * <p>색은 {@code currentColor} 다. 못 쓰는 항목이 흐려질 때 글자와 함께 흐려진다.
 */

/** 모든 아이콘이 같은 상자·같은 선 굵기를 쓴다. 제각각이면 목록이 들쭉날쭉해 보인다. */
function svg(body: string): string {
  return '<svg viewBox="0 0 16 16" width="16" height="16" fill="none" '
    + 'stroke="currentColor" stroke-width="1.3" stroke-linecap="round" '
    + `stroke-linejoin="round" aria-hidden="true">${body}</svg>`;
}

export const menuIcons = {
  /** 빈 종이. 새 문서. */
  newDoc: svg('<path d="M9 2H4.5A1.5 1.5 0 0 0 3 3.5v9A1.5 1.5 0 0 0 4.5 14h7a1.5 1.5 0 0 0 1.5-1.5V6z"/>'
    + '<path d="M9 2v4h4"/>'),

  /** 폴더. 열기. */
  open: svg('<path d="M2 4.5A1.5 1.5 0 0 1 3.5 3h2.3l1.2 1.5h5.5A1.5 1.5 0 0 1 14 6v5.5A1.5 1.5 0 0 1 12.5 13h-9A1.5 1.5 0 0 1 2 11.5z"/>'),

  /** 위로 향한 화살표. 올려서 가져오기. */
  upload: svg('<path d="M8 11V3"/><path d="M5 6l3-3 3 3"/><path d="M3 11v1.5A1.5 1.5 0 0 0 4.5 14h7a1.5 1.5 0 0 0 1.5-1.5V11"/>'),

  /** 아래로 향한 화살표. 내려받기. */
  download: svg('<path d="M8 3v8"/><path d="M5 8l3 3 3-3"/><path d="M3 11v1.5A1.5 1.5 0 0 0 4.5 14h7a1.5 1.5 0 0 0 1.5-1.5V11"/>'),

  /** 산과 해가 든 액자. 그림. */
  image: svg('<rect x="2.5" y="3.5" width="11" height="9" rx="1.5"/>'
    + '<circle cx="6" cy="6.5" r="1"/><path d="M3 11l3-2.5 2.5 2 2-1.5L13 11"/>'),

  /** 격자. 시트. */
  sheet: svg('<rect x="2.5" y="2.5" width="11" height="11" rx="1.5"/>'
    + '<path d="M2.5 6.5h11M6.5 2.5v11"/>'),

  /** 줄 위의 화살표. 위에 행 넣기. */
  rowAbove: svg('<rect x="2.5" y="8.5" width="11" height="5" rx="1"/><path d="M8 6.5V2.5"/><path d="M6 4.5l2-2 2 2"/>'),

  /** 줄 아래의 화살표. 아래에 행 넣기. */
  rowBelow: svg('<rect x="2.5" y="2.5" width="11" height="5" rx="1"/><path d="M8 9.5v4"/><path d="M6 11.5l2 2 2-2"/>'),

  /** 칸 왼쪽의 화살표. 왼쪽에 열 넣기. */
  colLeft: svg('<rect x="8.5" y="2.5" width="5" height="11" rx="1"/><path d="M6.5 8h-4"/><path d="M4.5 6l-2 2 2 2"/>'),

  /** 칸 오른쪽의 화살표. 오른쪽에 열 넣기. */
  colRight: svg('<rect x="2.5" y="2.5" width="5" height="11" rx="1"/><path d="M9.5 8h4"/><path d="M11.5 6l2 2-2 2"/>'),

  /** 가로줄에 그은 빼기. 행 지우기. */
  rowRemove: svg('<rect x="2.5" y="5.5" width="11" height="5" rx="1"/><path d="M5 8h6"/>'),

  /** 세로줄에 그은 빼기. 열 지우기. */
  colRemove: svg('<rect x="5.5" y="2.5" width="5" height="11" rx="1"/><path d="M8 5v6"/>'),

  /** fx. 함수. */
  formula: svg('<path d="M3.5 13V6a2.5 2.5 0 0 1 2.5-2.5h.8"/><path d="M2.8 7.6h4.2"/>'
    + '<path d="M9 7.6l4 5M13 7.6l-4 5"/>'),

  /** 123. 텍스트를 숫자로. */
  toNumber: svg('<path d="M2.6 5.2l1.2-.8v6"/>'
    + '<path d="M6.6 5.2a1.4 1.4 0 1 1 2.2 1.7L6.6 10.4h2.5"/>'
    + '<path d="M11.2 4.6h2.4l-1.4 2a1.6 1.6 0 1 1-1 2.9"/>'),

  /** 왼쪽으로 굽은 화살표. 실행 취소. */
  undo: svg('<path d="M3 8h7a3 3 0 0 1 0 6H7"/><path d="M5.5 5.5 3 8l2.5 2.5"/>'),

  /** 오른쪽으로 굽은 화살표. 다시 실행. */
  redo: svg('<path d="M13 8H6a3 3 0 0 0 0 6h3"/><path d="M10.5 5.5 13 8l-2.5 2.5"/>'),

  /** 클립보드. 붙여넣기. */
  paste: svg('<rect x="3.5" y="3" width="9" height="11" rx="1.5"/>'
    + '<path d="M6 3V2.2h4V3"/><path d="M6 8h4M6 11h3"/>'),

  /** 지우개. 내용·서식 지우기. */
  clear: svg('<path d="M6.5 13H13"/><path d="m9.5 3.2 3.3 3.3a1 1 0 0 1 0 1.4l-4.4 4.4a1 1 0 0 1-1.4 0L3.7 9a1 1 0 0 1 0-1.4l4.4-4.4a1 1 0 0 1 1.4 0Z"/>'),

  /** 왼쪽 맞춤. */
  alignLeft: svg('<path d="M2.5 4h11M2.5 7h7M2.5 10h11M2.5 13h7"/>'),
  /** 가운데 맞춤. */
  alignCenter: svg('<path d="M2.5 4h11M4.5 7h7M2.5 10h11M4.5 13h7"/>'),
  /** 오른쪽 맞춤. */
  alignRight: svg('<path d="M2.5 4h11M6.5 7h7M2.5 10h11M6.5 13h7"/>'),
  /** 위 맞춤. */
  alignTop: svg('<path d="M2.5 3h11"/><path d="M8 6v7"/><path d="M5.5 8.5 8 6l2.5 2.5"/>'),
  /** 가운데(세로) 맞춤. */
  alignMiddle: svg('<path d="M2.5 8h11"/><path d="M8 3v3M8 10v3"/>'),
  /** 아래 맞춤. */
  alignBottom: svg('<path d="M2.5 13h11"/><path d="M8 3v7"/><path d="M5.5 7.5 8 10l2.5-2.5"/>'),

  /** 굽어 내려가는 화살표. 자동 줄바꿈. */
  wrap: svg('<path d="M2.5 4h11"/><path d="M2.5 8h8a2 2 0 0 1 0 4H7"/><path d="M8.5 10.5 7 12l1.5 1.5"/>'),
  /** 줄 셋과 위아래 화살표. 줄 간격. */
  lineSpacing: svg('<path d="M6.5 4h7"/><path d="M6.5 8h7"/><path d="M6.5 12h7"/>'
    + '<path d="M3 3.5v9"/><path d="M1.5 5 3 3.5 4.5 5"/><path d="M1.5 11 3 12.5 4.5 11"/>'),

  /** 오른쪽으로 넘치는 글. 넘치게 두기. */
  overflow: svg('<path d="M2.5 4h11"/><path d="M2.5 8h6"/><path d="M10 8h3.5"/><path d="M2.5 12h11"/>'),
  /** 잘린 글. 넘치면 자르기. */
  clip: svg('<path d="M2.5 4h11"/><path d="M2.5 8h6"/><path d="M2.5 12h11"/><path d="M9 5.5v5"/>'),

  /** 기울어진 글자. 글자 회전. */
  rotate: svg('<path d="M3 12.5 8 3l5 9.5"/><path d="M5.2 9h5.6"/>'),

  /** 아래로 향한 화살표와 짧아지는 줄. 오름차순 정렬. */
  sortAsc: svg('<path d="M3.5 4h3M3.5 8h5M3.5 12h7"/><path d="M12 3v10"/><path d="M10 11l2 2 2-2"/>'),
  /** 위로 향한 화살표와 길어지는 줄. 내림차순 정렬. */
  sortDesc: svg('<path d="M3.5 4h7M3.5 8h5M3.5 12h3"/><path d="M12 13V3"/><path d="M10 5l2-2 2 2"/>'),
  /** 깔때기. 필터. */
  filter: svg('<path d="M2.5 3.5h11l-4.2 5v4.2l-2.6 1.3V8.5z"/>'),

  /** 칸 안의 눈금과 물방울. 조건에 따라 칠하기. */
  conditional: svg('<rect x="2.5" y="2.5" width="11" height="11" rx="1.5"/>'
    + '<path d="M8 5.2c1.4 1.6 2.3 2.7 2.3 3.7a2.3 2.3 0 0 1-4.6 0c0-1 .9-2.1 2.3-3.7Z"/>'),

  /** 네모 안의 체크. 데이터 확인. */
  check: svg('<rect x="2.5" y="2.5" width="11" height="11" rx="2"/><path d="M5.3 8.2l2 2 3.4-4"/>'),

  /** 사슬 고리. 링크. */
  link: svg('<path d="M6.6 9.4a2.6 2.6 0 0 1 0-3.7l2-2a2.6 2.6 0 1 1 3.7 3.7l-1 1"/>'
    + '<path d="M9.4 6.6a2.6 2.6 0 0 1 0 3.7l-2 2a2.6 2.6 0 1 1-3.7-3.7l1-1"/>'),
  /** 귀퉁이가 접힌 쪽지. 메모. */
  note: svg('<path d="M3 3.5h10v6L9.5 13H3.5a.5.5 0 0 1-.5-.5z"/><path d="M13 9.5H9.5V13"/>'),
  /** 말풍선. 댓글. */
  comment: svg('<path d="M2.5 4a1.5 1.5 0 0 1 1.5-1.5h8A1.5 1.5 0 0 1 13.5 4v5a1.5 1.5 0 0 1-1.5 1.5H7l-3 3v-3a1.5 1.5 0 0 1-1.5-1.5z"/>'),

  /** 머리줄 있는 격자. 표. */
  table: svg('<rect x="2.5" y="2.5" width="11" height="11" rx="1.5"/>'
    + '<path d="M2.5 6h11M2.5 9.75h11M6.5 6v7.5M10 6v7.5"/>'),

  /** 돋보기. 찾기. */
  find: svg('<circle cx="7.2" cy="7.2" r="4"/><path d="M10.2 10.2 13.5 13.5"/>'),

  /** 가로 붙박이 줄. 행 고정. */
  freezeRow: svg('<rect x="2.5" y="2.5" width="11" height="11" rx="1.5"/>'
    + '<path d="M2.5 6.5h11"/><path d="M5 9.5h6"/>'),
  /** 세로 붙박이 줄. 열 고정. */
  freezeColumn: svg('<rect x="2.5" y="2.5" width="11" height="11" rx="1.5"/>'
    + '<path d="M6.5 2.5v11"/><path d="M9.5 5v6"/>'),
  /** 빈 격자. 고정 없음. */
  freezeNone: svg('<rect x="2.5" y="2.5" width="11" height="11" rx="1.5"/>'),

  /** 눈. 숨긴 줄을 다시 보이게 한다. */
  unhide: svg('<path d="M1.5 8s2.6-4 6.5-4 6.5 4 6.5 4-2.6 4-6.5 4S1.5 8 1.5 8Z"/>'
    + '<circle cx="8" cy="8" r="1.8"/>'),

  /** 네 귀퉁이. 전체 화면. */
  fullscreen: svg('<path d="M6 2.5H3.5a1 1 0 0 0-1 1V6"/><path d="M10 2.5h2.5a1 1 0 0 1 1 1V6"/>'
    + '<path d="M6 13.5H3.5a1 1 0 0 1-1-1V10"/><path d="M10 13.5h2.5a1 1 0 0 0 1-1V10"/>'),

  /** 집. 시작 화면으로. */
  home: svg('<path d="M2.8 7.2 8 3l5.2 4.2"/><path d="M4.2 8.4v4.1a1 1 0 0 0 1 1h5.6a1 1 0 0 0 1-1V8.4"/>'),
};
