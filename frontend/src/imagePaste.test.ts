import { describe, it, expect } from 'vitest';

import { imageCommandFor, INSERT_DOC_IMAGE, INSERT_SHEET_IMAGE } from './imagePaste';

function png(): File {
  return new File([new Uint8Array([1, 2, 3])], 'capture.png', { type: 'image/png' });
}

describe('imageCommandFor', () => {
  it('시트는 떠 있는 그림으로 넣는다', () => {
    // 칸 그림(sheet.command.insert-cell-image)은 칸의 서식글 안에 들어가는데,
    // 우리 저장 길은 칸에서 글자만 뽑아 보낸다(adapter.ts fromUniverCell) -
    // 다시 열면 그림이 사라진다. 떠 있는 그림은 drawingSync 가 왕복시킨다.
    const command = imageCommandFor('SHEET', png());

    expect(command.id).toBe(INSERT_SHEET_IMAGE);
    expect(command.id).not.toContain('cell-image');
  });

  it('시트 명령에는 파일을 함께 넘긴다', () => {
    const image = png();

    const command = imageCommandFor('SHEET', image);

    expect(command.params).toEqual({ files: [image] });
  });

  it('글은 글 그림 명령으로 넣는다', () => {
    expect(imageCommandFor('DOC', png()).id).toBe(INSERT_DOC_IMAGE);
  });

  it('글 명령에는 파일을 넘기지 않는다', () => {
    // 글의 그림 넣기 명령은 인자를 안 받는다 - 스스로 파일 고르기 창을 연다.
    // 그래서 부르는 쪽이 창을 여는 자리에 파일을 끼운다(main.ts insertDocImage).
    expect(imageCommandFor('DOC', png()).params).toBeUndefined();
  });
});
