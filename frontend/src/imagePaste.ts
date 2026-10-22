/**
 * 붙여넣은 그림을 어느 명령으로 넣는가.
 *
 * <p>시트에는 <b>떠 있는 그림</b>으로 넣는다. 칸 그림은 칸의 서식글(cell.p) 안에
 * 그림으로 들어가는데, 우리 저장 길은 칸에서 글자만 뽑아 보내므로(adapter.ts 의
 * {@code fromUniverCell}) 다시 열면 그림이 사라진다. 떠 있는 그림은
 * ISheetDrawingService 에 남고 drawingSync 가 서버와 왕복시킨다 - 메뉴로 넣는
 * 그림과 같은 길이다.
 *
 * <p>글에는 글 그림 명령을 쓴다. 그 명령은 인자를 안 받고 스스로 파일 고르기 창을
 * 열기 때문에, 부르는 쪽이 창을 여는 자리에 파일을 끼운다.
 */

/** 글에 그림을 넣는 명령. facade 에는 없어 이름으로 부른다. */
export const INSERT_DOC_IMAGE = 'doc.command.insert-float-image';

/** 시트에 떠 있는 그림을 넣는 명령. 고른 칸 왼쪽 위에 얹힌다. */
export const INSERT_SHEET_IMAGE = 'sheet.command.insert-float-image';

/** 부를 명령과 인자. 인자가 없으면 명령이 스스로 파일을 받는다. */
export interface ImageCommand {
  id: string;
  params?: { files: File[] };
}

export function imageCommandFor(where: 'DOC' | 'SHEET', image: File): ImageCommand {
  if (where === 'DOC') {
    return { id: INSERT_DOC_IMAGE };
  }
  return { id: INSERT_SHEET_IMAGE, params: { files: [image] } };
}
