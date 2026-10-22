import { describe, it, expect, vi } from 'vitest';

import { showUnit } from './showUnit';

/** 가짜 Univer 한 벌. 어느 토큰으로 무엇을 꺼내 갔는지 적어 둔다. */
function fakeUniver(options: { stageWait?: Promise<void> } = {}) {
  const asked: unknown[] = [];
  const focused: Array<string | null> = [];
  let resolveStage: () => void = () => {};
  const stage = options.stageWait ?? new Promise<void>((done) => { resolveStage = done; });
  const univer = {
    __getInjector: () => ({
      get: (token: unknown) => {
        asked.push(token);
        return {
          onStage: () => stage,
          focusUnit: (id: string | null) => { focused.push(id); },
        };
      },
    }),
  };
  return { univer, focused, resolveStage, asked };
}

describe('showUnit', () => {
  it('그릴 준비가 되기 전에는 집지 않는다', async () => {
    // 작업대가 유닛과 캔버스를 잇는 구독을 걸기 전에 집으면 아무 일도 안 난다.
    const { univer, focused, resolveStage } = fakeUniver();

    const done = showUnit(univer as never, 'wo-doc-1');
    await Promise.resolve();

    expect(focused).toEqual([]);

    resolveStage();
    await done;

    expect(focused).toEqual(['wo-doc-1']);
  });

  it('이미 준비가 지났으면 바로 집는다', async () => {
    const { univer, focused } = fakeUniver({ stageWait: Promise.resolve() });

    await showUnit(univer as never, 'wo-doc-2');

    expect(focused).toEqual(['wo-doc-2']);
  });

  it('기다리다 실패해도 터지지 않는다', async () => {
    // 그 단계에 영영 못 가는 경우가 있다(화면이 닫혔다). 문서 여는 길을 함께
    // 무너뜨리면 안 된다.
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const { univer, focused } = fakeUniver({ stageWait: Promise.reject(new Error('안 됨')) });

    await expect(showUnit(univer as never, 'wo-doc-3')).resolves.toBeUndefined();

    expect(focused).toEqual([]);
    warn.mockRestore();
  });
});
