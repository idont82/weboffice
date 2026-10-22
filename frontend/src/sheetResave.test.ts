import { describe, expect, it } from 'vitest';

import { movedCells } from './sheetResave';

/** 좌표→값. 실제로는 셀 객체를 JSON 으로 굳힌 글자다. */
function map(entries: Record<string, string>) {
  return new Map(Object.entries(entries));
}

describe('movedCells', () => {
  it('값이 위로 밀리면 바뀐 자리를 모두 알려 준다', () => {
    // 행을 지우면 아래 값이 위로 온다. 그 자리를 다시 저장해야 서버가 따라온다.
    const done = movedCells(
      map({ '0:0': '"머리"', '1:0': '"가"', '2:0': '"나"' }),
      map({ '0:0': '"머리"', '1:0': '"나"' }));

    expect(done.changed).toEqual(['1:0']);
    expect(done.deleted).toEqual(['2:0']);
  });

  it('꼬리가 비면 지울 자리로 알려 준다', () => {
    const done = movedCells(map({ '5:1': '"끝"' }), map({}));

    expect(done.changed).toEqual([]);
    expect(done.deleted).toEqual(['5:1']);
  });

  it('새로 생긴 자리는 바뀐 것으로 본다', () => {
    const done = movedCells(map({}), map({ '3:2': '"새 값"' }));

    expect(done.changed).toEqual(['3:2']);
    expect(done.deleted).toEqual([]);
  });

  it('같으면 아무것도 안 보낸다', () => {
    const same = map({ '0:0': '"가"', '1:1': '"나"' });

    expect(movedCells(same, map({ '0:0': '"가"', '1:1': '"나"' })))
      .toEqual({ changed: [], deleted: [] });
  });
});
