/** 관리자 화면 진입점. 그리는 일은 viewAdmin.ts 가 한다(시험할 수 있게 나눴다). */
import { renderAdmin, type AdminEvent, type AdminStats } from './viewAdmin';

const root = document.getElementById('adminRoot')!;

async function load(type: string | null): Promise<void> {
  const query = type == null ? '' : `?type=${type}`;
  const [events, stats] = await Promise.all([
    fetch(`/api/view/admin/events${query}`, { credentials: 'same-origin' })
      .then((response) => response.json() as Promise<AdminEvent[]>),
    fetch('/api/view/admin/stats', { credentials: 'same-origin' })
      .then((response) => response.json() as Promise<AdminStats>),
  ]);
  renderAdmin(root, { events, stats, onPick: (picked) => { void load(picked); } });
}

void load(null).catch(() => {
  root.textContent = '이력을 읽지 못했습니다. 관리자 계정으로 로그인했는지 확인해 주세요.';
});
