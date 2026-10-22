/**
 * 보기 전용 화면.
 *
 * <p>스냅샷을 한 번 받아 Univer 를 세운다. 저장·잠금·실시간·메뉴가 없다 - 읽기만
 * 하는 화면에 그 왕복은 값어치가 없다(설계 6절).
 *
 * <p>몸통은 변환 결과 JSON 그것뿐이고, 무슨 문서인지·누가 보는지는 응답 머리에
 * 실려 온다({@link ./viewerSnapshot} 의 snapshotMeta).
 */
import { snapshotMeta } from './viewerSnapshot';
import { watermark } from './watermark';

const rootEl = document.getElementById('viewerRoot')!;
const barEl = document.getElementById('viewerName')!;
const appEl = document.getElementById('viewerApp')!;
const noticeEl = document.getElementById('viewerNotice')!;

function tokenOf(): string {
  const at = location.pathname.lastIndexOf('/');
  return location.pathname.slice(at + 1);
}

function notice(word: string): void {
  noticeEl.textContent = word;
  noticeEl.style.display = 'flex';
}

async function open(): Promise<void> {
  const response = await fetch(`/api/view/${tokenOf()}/snapshot`, { credentials: 'same-origin' });
  if (!response.ok) {
    notice('문서를 열 수 없습니다. 보내 준 시스템에서 다시 열어 주세요.');
    return;
  }
  const meta = snapshotMeta(response.headers);
  const data: unknown = await response.json();

  barEl.textContent = meta.fileName;
  // 창 제목도 파일 이름으로 바꾼다. 여러 문서를 열어 두면 탭 제목이 곧 이름표다.
  if (meta.fileName !== '') {
    document.title = `${meta.fileName} — Web Office`;
  }

  // 잠겼던 문서에만 워터마크를 얹는다. 모든 문서에 얹으면 읽기가 나빠진다.
  if (meta.encrypted) {
    const when = new Date().toLocaleString('ko-KR');
    watermark(rootEl, `${meta.viewerName ?? '열람자'} · ${when}`);
  }

  // 문서 종류마다 화면을 세우는 길이 다르다. Univer 는 무겁다 - 여기서 나눠
  // 부르면 문서를 못 여는 사람(만료된 링크)이 그것을 내려받지 않는다.
  const { showSnapshot } = await import('./viewerRender');
  showSnapshot(appEl, meta, data);
}

// 오른쪽 클릭 메뉴를 막는다. 보기 전용 화면에 "잘라내기·붙여넣기" 가 뜰 이유가
// 없고, 그 메뉴는 눌러도 명령이 취소돼 되는 것처럼 보이기만 한다(2026-08-20 지적).
// 내려받기를 막는 장치는 아니다 - 그림 저장 같은 길은 브라우저가 따로 준다.
rootEl.addEventListener('contextmenu', (event) => event.preventDefault());

void open().catch(() => notice('문서를 열 수 없습니다.'));
