import { describe, expect, it, vi } from 'vitest';

import { createServerImageIoService, imageSeqOf } from './serverImageIo';

function fakeApi(url = '/api/images/9') {
  return {
    uploadImage: vi.fn().mockResolvedValue({ imageSeq: 9, url, mimeType: 'image/png' }),
  };
}

function pngFile() {
  return new File(['x'], 'a.png', { type: 'image/png' });
}

describe('createServerImageIoService', () => {
  it('올린 이미지를 URL 로 돌려준다', async () => {
    const service = createServerImageIoService({ getWorkbookSeq: () => 7, api: fakeApi() });

    const result = await service.saveImage(pngFile());

    // base64 면 바이트가 문서 모델에 눌러앉아 저장할 때마다 통째로 오간다.
    expect(result.source).toBe('/api/images/9');
    expect(result.imageSourceType).toBe('URL');
    expect(result.imageId).toBe('9');
  });

  it('크기를 잴 수 있는 이미지 주소를 base64Cache 로 돌려준다', async () => {
    // Univer 의 insertFloatImageByFile 이 getImageSize(base64Cache || '') 로
    // 크기를 재는데, 그 호출은 saveImage 를 감싼 try/catch 밖에 있다.
    // 빈 문자열을 주면 img.src = '' 가 onerror 를 내고 promise 가 reject 돼
    // 붙여넣기가 통째로 조용히 죽는다(2026-08-05 실측).
    const service = createServerImageIoService({ getWorkbookSeq: () => 7, api: fakeApi() });

    const result = await service.saveImage(pngFile());

    expect(result.base64Cache).toBeTruthy();
    expect(result.base64Cache).toBe(result.source);
  });

  it('문서를 열기 전에는 올리지 못한다', async () => {
    const api = fakeApi();
    const service = createServerImageIoService({ getWorkbookSeq: () => null, api });

    await expect(service.saveImage(pngFile())).rejects.toThrow('문서를 먼저');
    expect(api.uploadImage).not.toHaveBeenCalled();
  });

  it('허용하지 않는 형식은 서버에 보내지 않는다', async () => {
    const api = fakeApi();
    const service = createServerImageIoService({ getWorkbookSeq: () => 7, api });

    await expect(service.saveImage(
      new File(['<svg/>'], 'a.svg', { type: 'image/svg+xml' }))).rejects.toThrow();
    expect(api.uploadImage).not.toHaveBeenCalled();
  });

  it('올린 문서 번호를 그대로 넘긴다', async () => {
    const api = fakeApi();
    const service = createServerImageIoService({ getWorkbookSeq: () => 42, api });

    await service.saveImage(pngFile());

    expect(api.uploadImage).toHaveBeenCalledWith(42, expect.any(File));
  });

  it('실패해도 대기 수가 줄어든다', async () => {
    // 줄지 않으면 Univer 의 진행 표시가 영영 남는다.
    const service = createServerImageIoService({ getWorkbookSeq: () => null, api: fakeApi() });
    const seen: number[] = [];
    service.change$.subscribe((count) => seen.push(count));

    service.setWaitCount(1);
    await service.saveImage(pngFile()).catch(() => undefined);

    expect(seen).toEqual([1, 0]);
  });
});

describe('imageSeqOf', () => {
  it('URL 에서 번호를 꺼낸다', () => {
    expect(imageSeqOf('/api/images/12')).toBe(12);
  });

  it('우리 주소가 아니면 null 이다', () => {
    // 남이 만든 문서를 열면 base64 나 외부 URL 이 들어올 수 있다.
    expect(imageSeqOf('data:image/png;base64,AAAA')).toBeNull();
    expect(imageSeqOf('https://example.com/a.png')).toBeNull();
  });
});
