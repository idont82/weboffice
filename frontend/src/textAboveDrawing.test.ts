import { describe, expect, it } from 'vitest';

import { putTextAboveDrawings } from './textAboveDrawing';

describe('putTextAboveDrawings', () => {
  it('글 층을 그림 위로 올린다', () => {
    const code = 'scene.addObjects([documents], 2);';

    expect(putTextAboveDrawings(code)).toContain('[documents], 6);');
  });

  it('그 자리가 없으면 그대로 둔다', () => {
    expect(putTextAboveDrawings('const a = 1;')).toBe('const a = 1;');
  });
});
