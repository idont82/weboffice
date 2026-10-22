/** 오픈소스 현황 진입점. 그리는 일은 ossView.ts 가 한다(시험할 수 있게 나눴다). */
import { renderOss, type OssPayload } from './ossView';

const root = document.getElementById('ossRoot')!;

async function notice(): Promise<string> {
  const response = await fetch('/api/admin/oss/notice', { credentials: 'same-origin' });
  if (!response.ok) {
    throw new Error(String(response.status));
  }
  return response.text();
}

async function load(): Promise<void> {
  const response = await fetch('/api/admin/oss', { credentials: 'same-origin' });
  if (!response.ok) {
    throw new Error(String(response.status));
  }
  const payload = await response.json() as OssPayload;
  renderOss(root, { ...payload, onNotice: notice });
}

void load().catch(() => {
  root.textContent = '목록을 읽지 못했습니다. 관리자 계정으로 로그인했는지 확인해 주세요.';
});
