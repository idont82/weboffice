import { describe, expect, it } from 'vitest';

import { DOC_ICON, SHEET_ICON, faviconOf, kindLabel } from './docIcons';

describe('docIcons', () => {
  it('종류마다 다른 그림이다', () => {
    // 같은 그림이면 탭이 여럿일 때 어느 것이 글이고 어느 것이 시트인지 모른다.
    expect(SHEET_ICON).toContain('data-kind="sheet"');
    expect(DOC_ICON).toContain('data-kind="doc"');
    expect(SHEET_ICON).not.toEqual(DOC_ICON);
  });

  it('글 문서는 파랑, 시트는 초록이다', () => {
    expect(DOC_ICON).toContain('#1a56a8');
    expect(SHEET_ICON).toContain('#0e7c4a');
  });

  it('탭 아이콘은 그려 넣을 수 있는 주소로 준다', () => {
    // 파일을 종류마다 두지 않는다. 그림이 이미 여기 있으니 그대로 감싼다.
    const url = faviconOf('DOC');

    expect(url.startsWith('data:image/svg+xml,')).toBe(true);
    expect(decodeURIComponent(url)).toContain('#1a56a8');
  });

  it('탭 아이콘 주소에 따옴표를 그대로 두지 않는다', () => {
    // 인용하지 않으면 href 안에서 속성이 끊긴다.
    expect(faviconOf('SHEET')).not.toContain('"');
  });

  it('종류를 사람 말로 적는다', () => {
    expect(kindLabel('DOC')).toBe('문서');
    expect(kindLabel('SHEET')).toBe('시트');
  });
});
