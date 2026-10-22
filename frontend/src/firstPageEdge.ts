/**
 * 첫 쪽만 다른 머릿글·바닥글(워드의 titlePg).
 *
 * <p>문서 전체 설정({@code documentStyle.useFirstPageHeaderFooter})만 켜 두면
 * 모자란다. Univer 1.0 은 워드 호환 모드(TRADITIONAL)에서 본문에 구역
 * ({@code body.sectionBreaks})이 있으면 그 구역이 제 값을 안 들고 있는 한 깃발을
 * <b>꺼진 것으로</b> 친다 - 첫 쪽에도 쪽마다 쓰는 머릿글이 찍힌다(2026-10-16
 * 실측: 표지에 머릿글·바닥글이 나왔다. 0.25 는 문서 설정만 봤다).
 *
 * <p>그래서 문서 설정에 켜져 있으면 구역에도 같이 적어 준다. 구역이 제 값을
 * 들고 있으면 그것이 참이다 - 덮지 않는다.
 */

interface SectionBreak {
  useFirstPageHeaderFooter?: number;
  [key: string]: unknown;
}

interface DocLike {
  documentStyle?: { useFirstPageHeaderFooter?: number; [key: string]: unknown };
  body?: { sectionBreaks?: SectionBreak[]; [key: string]: unknown };
  [key: string]: unknown;
}

export function withFirstPageEdge<T extends DocLike>(doc: T): T {
  const wanted = doc.documentStyle?.useFirstPageHeaderFooter;
  const breaks = doc.body?.sectionBreaks;
  if (wanted !== 1 || breaks == null || breaks.length === 0) {
    return doc;
  }
  return {
    ...doc,
    body: {
      ...doc.body,
      sectionBreaks: breaks.map((one) => (one.useFirstPageHeaderFooter == null
        ? { ...one, useFirstPageHeaderFooter: 1 } : one)),
    },
  };
}
