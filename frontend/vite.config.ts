import path from 'node:path';

import react from '@vitejs/plugin-react';
import { defineConfig, type Plugin } from 'vite';

import { gateCjkSpacing } from './src/cjkSpacing';
import { putTextAboveDrawings } from './src/textAboveDrawing';

/**
 * Univer 번들을 고치는 패치 하나.
 *
 * <p>{@code mark} 는 "이 파일이 고칠 파일인가" 를 가리는 표다. {@code patch} 가
 * 실제로 고친다 - 안쪽 자리를 못 찾으면 스스로 빌드를 세운다.
 */
interface UniverPatch {
  /** 플러그인 이름. 오류에 그대로 나온다. */
  name: string;
  /** 사람 말로 무엇을 고치는지. 판올림 뒤 무엇이 빠졌는지 알아보려고 적는다. */
  what: string;
  /** 고칠 파일을 가리는 표. */
  mark: string;
  patch: (code: string) => string;
}

/**
 * 패치를 Vite 플러그인으로.
 *
 * <p>한 번도 안 붙었으면 빌드를 세운다. 표(mark)가 사라지면 transform 이 그냥
 * 지나가므로, 판올림으로 자리가 바뀌어도 빌드는 성공하고 고침만 조용히 사라진다
 * (2026-10-16 실측: Univer 1.0 에서 이 일이 났다). 그래서 붙은 수를 센다.
 */
function univerPatch({ name, what, mark, patch }: UniverPatch): Plugin {
  let applied = 0;
  return {
    name,
    enforce: 'pre',
    transform(code, id) {
      if (!id.includes('@univerjs/') || !code.includes(mark)) {
        return null;
      }
      applied += 1;
      return { code: patch(code), map: null };
    },
    buildEnd(error) {
      if (error != null || applied > 0) {
        return;
      }
      throw new Error(`${what}: 고칠 자리를 Univer 안에서 찾지 못했습니다`
        + ` (표 "${mark}"). 판올림으로 자리가 바뀐 것 같습니다 - ${name}`);
    },
  };
}

/**
 * 우리가 Univer 번들에 넣는 고침 전부.
 *
 * <p>왜 번들을 고치는지는 각 함수에 적었다. 차례는 뜻이 없다.
 */
const UNIVER_PATCHES: UniverPatch[] = [
  {
    name: 'wo-cjk-spacing-switch',
    what: '한글·영문 자동 간격 스위치',
    mark: 'addCJKLatinSpacing',
    patch: gateCjkSpacing,
  },
  {
    name: 'wo-text-above-drawing',
    what: '글을 그림보다 위층에',
    mark: 'scene.addObjects([documents], 2);',
    patch: putTextAboveDrawings,
  },
];

export default defineConfig({
  plugins: [react(), ...UNIVER_PATCHES.map(univerPatch)],
  build: {
    // Maven 정적 리소스 경로로 직접 산출한다.
    //
    // emptyOutDir 가 이 폴더를 통째로 비운다. 그래서 손으로 쓴 페이지(관리자 첫
    // 화면, 연동 안내)는 src/main/resources/static 이 아니라 여기 public/ 에 둔다 -
    // Maven 은 process-resources(앞)에서 복사하고 Vite 는 prepare-package(뒤)에서
    // 비우므로, 저쪽에 두면 배포 jar 에서 소리 없이 사라진다(2026-10-14 실측).
    outDir: '../target/classes/static',
    emptyOutDir: true,
    // 화면이 넷이다. 적지 않으면 index.html 만 나오고 /v/{token} 이 여는
    // viewer.html, 관리자 화면 둘이 통째로 빠진다.
    rollupOptions: {
      input: {
        index: path.resolve(__dirname, 'index.html'),
        viewer: path.resolve(__dirname, 'viewer.html'),
        admin: path.resolve(__dirname, 'admin.html'),
        adminUsers: path.resolve(__dirname, 'admin-users.html'),
        oss: path.resolve(__dirname, 'oss.html'),
      },
    },
  },
  test: {
    // 제목 편집기·공유 대화상자는 실제 DOM 이벤트(blur, keydown)로 검증한다.
    // 기본 node 환경에서는 document 자체가 없다.
    environment: 'jsdom',
  },
  server: {
    // 개발 중에는 Vite dev 서버에서 Boot 로 프록시한다.
    proxy: {
      '/api': 'http://localhost:8710',
    },
  },
});
