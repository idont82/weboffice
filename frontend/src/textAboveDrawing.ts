/**
 * 글을 그림보다 위층에 그린다.
 *
 * <p>워드는 표지 배경을 "글 뒤"(behindDoc)로 적는다. 화면(Univer 0.25.1)은 글을
 * 2번 층, 그림을 4번 층에 그려 배경이 표지 제목을 덮었다(2026-08-26 지적).
 * 그림 층을 낮추면 아예 그려지지 않아, 글 층을 그림 위로 올린다.
 *
 * <p>머릿글·꼬릿글 층(4)보다도 위여야 본문 글이 배경 위에 선다.
 */
const ANCHOR = 'scene.addObjects([documents], 2);';

const ABOVE = 'scene.addObjects([documents], 6);';

export function putTextAboveDrawings(code: string): string {
  if (!code.includes(ANCHOR)) {
    return code;
  }
  return code.replace(ANCHOR, ABOVE);
}
