import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createApi } from './api';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const anyRange = { fromRowNo: 0, fromColumnNo: 0, toRowNo: 1, toColumnNo: 1 };

describe('api', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('셀 조회는 범위를 쿼리 파라미터로 넘긴다', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse({ styles: [], cells: [] }));

    await createApi().readCells(7, { fromRowNo: 0, fromColumnNo: 0, toRowNo: 9, toColumnNo: 4 });

    const url = fetchSpy.mock.calls[0][0] as string;
    expect(url).toContain('/api/sheets/7/cells');
    expect(url).toContain('fromRowNo=0');
    expect(url).toContain('toColumnNo=4');
  });

  it('hwpx 내려받기 주소를 만든다', () => {
    expect(createApi().docExportHwpxUrl(7)).toBe('/api/docs/7/export/hwpx');
  });

  it('세션 쿠키가 함께 가도록 credentials 를 붙인다', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse({ styles: [], cells: [] }));

    await createApi().readCells(7, anyRange);

    const init = fetchSpy.mock.calls[0][1] as RequestInit;
    expect(init.credentials).toBe('same-origin');
  });

  it('쓰기 요청에도 credentials 를 붙인다', async () => {
    // 빠뜨리면 조회만 되고 저장은 전부 401 이 난다. 조회 테스트만으로는 안 잡힌다.
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse({ savedCount: 0 }));

    await createApi().saveDelta(7, [], []);

    const init = fetchSpy.mock.calls[0][1] as RequestInit;
    expect(init.credentials).toBe('same-origin');
  });

  it('401 은 인증 오류로 구분한다', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ message: '인증 필요' }, 401));

    await expect(createApi().readCells(7, anyRange)).rejects.toThrow('인증');
  });

  it('403 은 권한 오류로 구분한다', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ message: '권한 없음' }, 403));

    await expect(createApi().readCells(7, anyRange)).rejects.toThrow('권한');
  });

  it('자리 알림은 겹쳐도 성공한다', async () => {
    // 잠금이 아니라 표시다. 서버가 409 를 주지 않는다(2026-08-06 변경).
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      lockSeq: 3, sheetSeq: 7, userId: 'hong', displayName: '홍길동',
      fromRowNo: 0, fromColumnNo: 0, toRowNo: 9, toColumnNo: 9,
    }));

    const seat = await createApi()
      .acquireLock(7, { fromRowNo: 0, fromColumnNo: 0, toRowNo: 9, toColumnNo: 9 }, 'tab-1');

    expect(seat.lockSeq).toBe(3);
    expect(seat.userId).toBe('hong');
  });

  it('저장은 변경분과 삭제분을 나눠 보낸다', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse({ savedCount: 2 }));

    const saved = await createApi().saveDelta(7,
      [{ rowNo: 0, columnNo: 0, valueType: 'NUMBER', numberValue: 1 }],
      [{ rowNo: 1, columnNo: 1, valueType: 'STRING' }]);

    expect(saved).toBe(2);
    const body = JSON.parse((fetchSpy.mock.calls[0][1] as RequestInit).body as string);
    expect(body.changed).toHaveLength(1);
    expect(body.deleted).toHaveLength(1);
  });

  it('이미지를 multipart 로 올린다', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({ imageSeq: 3, url: '/api/images/3', mimeType: 'image/png' }));

    const result = await createApi().uploadImage(
      7, new File(['x'], 'a.png', { type: 'image/png' }));

    expect(result.url).toBe('/api/images/3');
    expect(fetchSpy.mock.calls[0][0]).toBe('/api/workbooks/7/images');

    const init = fetchSpy.mock.calls[0][1] as RequestInit;
    // Content-Type 을 직접 넣으면 boundary 가 빠져 서버가 파싱하지 못한다.
    expect(init.headers).not.toHaveProperty('Content-Type');
    // 어느 창이 보냈는지는 실어야 한다. 그래야 자기 알림을 건너뛴다.
    expect(init.headers).toHaveProperty('X-WO-Client');
    expect(init.credentials).toBe('same-origin');
    expect(init.body).toBeInstanceOf(FormData);
  });

  it('그림 목록을 읽는다', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse([]));

    await createApi().readDrawings(7);

    expect(fetchSpy.mock.calls[0][0]).toBe('/api/sheets/7/drawings');
    expect((fetchSpy.mock.calls[0][1] as RequestInit).credentials).toBe('same-origin');
  });

  it('그림 델타를 PUT 으로 보낸다', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse({ savedCount: 2 }));

    const saved = await createApi().saveDrawings(7, [], ['dw-1']);

    expect(saved).toBe(2);
    const init = fetchSpy.mock.calls[0][1] as RequestInit;
    expect(init.method).toBe('PUT');
    expect(init.credentials).toBe('same-origin');
    expect(JSON.parse(init.body as string)).toEqual({ upserted: [], deletedIds: ['dw-1'] });
  });

  it('서식 사전을 셀마다 되돌려 붙인다', async () => {
    // 서버는 같은 서식을 한 번만 싣고 셀에는 번호만 준다. 화면 쪽은 예전처럼
    // 셀마다 서식 문자열을 들고 있어야 한다.
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      styles: ['{"bl":1}'],
      cells: [
        { rowNo: 0, columnNo: 0, valueType: 'STRING', textValue: '가', styleNo: 0 },
        { rowNo: 0, columnNo: 1, valueType: 'STRING', textValue: '나' },
      ],
    }));

    const cells = await createApi().readCells(7, anyRange);

    expect(cells[0].styleValue).toBe('{"bl":1}');
    expect(cells[1].styleValue ?? null).toBeNull();
  });

  it('걸러 보기 자리를 시트에 담는다', async () => {
    // 화면 필터를 파일에도 남기려면 자리를 서버가 알아야 한다(2026-08-26).
    const calls: { url: string; init: RequestInit }[] = [];
    globalThis.fetch = (async (url: string, init: RequestInit) => {
      calls.push({ url, init });
      return new Response('{}', { status: 200 });
    }) as never;

    await createApi().setFilterRange(7, 'A1:D20');

    expect(calls[0].url).toBe('/api/sheets/7');
    expect(calls[0].init.method).toBe('PATCH');
    expect(String(calls[0].init.body)).toContain('A1:D20');
  });

});
