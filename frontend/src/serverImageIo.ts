/**
 * Univer 의 IImageIoService 를 우리 서버로 돌린다.
 *
 * OSS 기본 구현(@univerjs/drawing 의 ImageIoService)은 FileReader 로 base64
 * data URL 을 만들어 그대로 문서 모델에 넣는다. 그러면 이미지 바이트가 그림
 * 메타데이터를 읽고 쓸 때마다 통째로 오가고, 브라우저 캐시가 먹지 않고,
 * 같은 그림을 여러 번 붙여넣으면 그만큼 복사된다.
 *
 * 여기서는 업로드 후 URL 만 모델에 남긴다. 바이트는 /api/images/{seq} 가
 * 1년 immutable 캐시로 내보낸다.
 */
import { ImageSourceType, ImageUploadStatusType } from '@univerjs/core';
import { Subject } from 'rxjs';

import type { Api } from './api';

/**
 * 서버 ImageService 가 바이트로 판별하는 것과 같은 집합이다. svg 는 없다 -
 * SVG 안의 script 는 우리 오리진에서 실행된다.
 */
const ALLOWED = ['image/png', 'image/jpeg', 'image/jpg', 'image/gif', 'image/bmp'];

export interface ServerImageIoDeps {
  /** 문서를 열기 전이면 null. 그때는 올릴 곳이 없다. */
  getWorkbookSeq: () => number | null;
  api: Pick<Api, 'uploadImage'>;
}

export function createServerImageIoService({ getWorkbookSeq, api }: ServerImageIoDeps) {
  const change$ = new Subject<number>();
  const cache = new Map<string, HTMLImageElement>();
  let waitCount = 0;

  function decreaseWaiting() {
    waitCount = Math.max(0, waitCount - 1);
    change$.next(waitCount);
  }

  return {
    change$,

    setWaitCount(count: number) {
      waitCount = count;
      change$.next(count);
    },

    getImageSourceCache(source: string, imageSourceType: ImageSourceType) {
      // base64 는 캐시하지 않는다. 우리 경로에서는 나오지 않지만, 남이 만든
      // 문서를 열면 들어올 수 있다. 기본 구현과 같은 처리를 한다.
      if (imageSourceType === ImageSourceType.BASE64) {
        const image = new Image();
        image.src = source;
        return image;
      }
      return cache.get(source);
    },

    addImageSourceCache(source: string, imageSourceType: ImageSourceType,
                        imageSource: HTMLImageElement | null) {
      if (imageSourceType === ImageSourceType.BASE64 || imageSource == null) return;
      cache.set(source, imageSource);
    },

    async getImage(imageId: string): Promise<string> {
      return imageId;
    },

    async saveImage(file: File) {
      const workbookSeq = getWorkbookSeq();
      try {
        if (workbookSeq === null) {
          throw new Error('문서를 먼저 열어야 그림을 넣을 수 있습니다.');
        }
        // 서버가 바이트로 다시 판별한다. 여기서 막는 것은 왕복을 아끼고
        // 사용자에게 즉시 알리기 위해서다. 방어선은 서버다.
        if (!ALLOWED.includes(file.type)) {
          throw new Error('png, jpeg, gif, bmp 만 넣을 수 있습니다.');
        }

        const uploaded = await api.uploadImage(workbookSeq, file);
        return {
          imageId: String(uploaded.imageSeq),
          imageSourceType: ImageSourceType.URL,
          source: uploaded.url,
          /*
           * 이름은 base64Cache 지만 base64 를 넣지 않는다. Univer 는 이 값을
           * getImageSize(base64Cache || '') 에 그대로 넘겨 <img src> 로 크기를
           * 잰다 - 주소든 data URL 이든 상관없다. 실제로 같은 파일의
           * insertCellImageByUrl 은 URL 을 넘긴다.
           *
           * null 을 주면 안 된다. 빈 문자열이 돼 img.src = '' 가 onerror 를
           * 내고 promise 가 reject 되는데, 그 호출은 saveImage 를 감싼
           * try/catch 밖이라 붙여넣기가 통째로 조용히 죽는다(2026-08-05 실측).
           *
           * base64 를 만들지 않는 이유: 바이트를 한 번 더 읽어 메모리에 얹을
           * 이유가 없다. 주소는 어차피 서버가 1년 immutable 로 내보낸다.
           */
          base64Cache: uploaded.url,
          // 철자는 Univer 쪽 오타다. 고치면 안 된다.
          status: ImageUploadStatusType.SUCCUSS,
        };
      } finally {
        decreaseWaiting();
      }
    },
  };
}

export type ServerImageIoService = ReturnType<typeof createServerImageIoService>;

/** URL 에서 IMAGE_SEQ 를 되꺼낸다. 그림 델타를 만들 때 서버로 되돌려 보낼 값이다. */
export function imageSeqOf(source: string): number | null {
  const matched = /\/api\/images\/(\d+)$/.exec(source);
  return matched ? Number(matched[1]) : null;
}
