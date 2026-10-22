/**
 * 공유 대화상자.
 *
 * 사람을 지정해 공유하는 것과 링크로 공개하는 것 둘을 한 자리에서 다룬다.
 *
 * <dialog> 를 쓴다. 직접 만든 오버레이보다 Esc 닫기와 포커스 가둠이 브라우저
 * 기본으로 따라온다.
 *
 * 판단(누구를 어떻게 적는가, 언제 찾는가)은 순수 함수로 빼서 테스트한다. 조립은
 * 브라우저 실측에서 본다.
 */
import type { Api, UserSummary, WorkbookMember } from './api';

export function describeMember(member: WorkbookMember): string {
  const who = [member.displayName?.trim(), member.rank?.trim()].filter(Boolean).join(' ');
  const dept = member.deptName?.trim();
  if (who && dept) return `${who} · ${dept}`;
  // 이름을 못 찾아도 줄은 남아야 한다. 사라지면 제거 버튼도 같이 사라진다.
  return who || dept || '(알 수 없는 사용자)';
}

export function needsSearch(keyword: string): boolean {
  // 서버도 막지만, 한 글자마다 요청을 보내면 인명부를 훑는 꼴이 된다.
  return keyword.trim().length >= 2;
}

export interface ShareDialogOptions {
  dialog: HTMLDialogElement;
  workbookSeq: number;
  /** 소유자만 사람을 넣고 빼고 공개할 수 있다. 나머지는 누가 함께 쓰는지만 본다. */
  isOwner: boolean;
  api: Api;
  onError?: (message: string) => void;
}

const SEARCH_DEBOUNCE_MS = 250;

export async function openShareDialog(options: ShareDialogOptions): Promise<void> {
  const { dialog, workbookSeq, isOwner, api } = options;
  const fail = options.onError ?? (() => {});

  dialog.replaceChildren();
  const body = document.createElement('div');
  body.className = 'share-body';

  const memberList = document.createElement('ul');
  memberList.className = 'share-members';

  async function reloadMembers() {
    try {
      const members = await api.listMembers(workbookSeq);
      memberList.replaceChildren(...members.map(memberRow));
    } catch (error) {
      fail(messageOf(error));
    }
  }

  function memberRow(member: WorkbookMember): HTMLElement {
    const line = document.createElement('li');
    const who = document.createElement('span');
    who.textContent = describeMember(member);
    line.append(who);

    // USER_ID 를 잃은 줄은 바꿀 수도 뺄 수도 없다. 서버가 아이디로만 받는다.
    if (!isOwner || !member.userId) {
      const role = document.createElement('span');
      role.className = 'note';
      role.textContent = member.role === 'VIEWER' ? '열람' : '편집';
      line.append(role);
      return line;
    }

    const userId = member.userId;
    const role = document.createElement('select');
    for (const [value, label] of [['EDITOR', '편집'], ['VIEWER', '열람']]) {
      const option = document.createElement('option');
      option.value = value;
      option.textContent = label;
      role.append(option);
    }
    role.value = member.role;
    role.addEventListener('change', () => {
      void api.putMember(workbookSeq, userId, role.value)
        .catch((error) => fail(messageOf(error)));
    });

    const remove = document.createElement('button');
    remove.type = 'button';
    remove.textContent = '제거';
    remove.addEventListener('click', () => {
      void api.removeMember(workbookSeq, userId)
        .then(reloadMembers)
        .catch((error) => fail(messageOf(error)));
    });

    line.append(role, remove);
    return line;
  }

  if (isOwner) {
    body.append(searchBox());
  }
  body.append(memberList);
  if (isOwner) {
    body.append(await publicSection());
  }

  const close = document.createElement('button');
  close.type = 'button';
  close.className = 'share-close';
  close.textContent = '닫기';
  close.addEventListener('click', () => dialog.close());
  body.append(close);

  dialog.append(body);
  await reloadMembers();
  dialog.showModal();

  function searchBox(): HTMLElement {
    const box = document.createElement('div');
    box.className = 'share-search';

    const input = document.createElement('input');
    input.type = 'search';
    input.placeholder = '이름으로 찾기 (두 글자 이상)';

    const results = document.createElement('ul');
    results.className = 'share-results';

    let timer: ReturnType<typeof setTimeout> | undefined;
    input.addEventListener('input', () => {
      clearTimeout(timer);
      if (!needsSearch(input.value)) {
        results.replaceChildren();
        return;
      }
      // 글자마다 보내면 타이핑 한 번에 인명부를 여러 번 훑는다.
      timer = setTimeout(() => {
        void api.searchUsers(input.value.trim())
          .then((users) => results.replaceChildren(...users.map(resultRow)))
          .catch((error) => fail(messageOf(error)));
      }, SEARCH_DEBOUNCE_MS);
    });

    box.append(input, results);
    return box;
  }

  function resultRow(user: UserSummary): HTMLElement {
    const line = document.createElement('li');
    const who = document.createElement('span');
    who.textContent = describeMember({ ...user, role: 'EDITOR' });
    const add = document.createElement('button');
    add.type = 'button';
    add.textContent = '추가';
    add.addEventListener('click', () => {
      void api.putMember(workbookSeq, user.userId, 'EDITOR')
        .then(reloadMembers)
        .catch((error) => fail(messageOf(error)));
    });
    line.append(who, add);
    return line;
  }

  /** 공개 링크. 발급한 순간 옛 링크가 죽으므로 "새로 발급" 은 회수를 겸한다. */
  async function publicSection(): Promise<HTMLElement> {
    const box = document.createElement('div');
    box.className = 'share-public';

    const label = document.createElement('label');
    const toggle = document.createElement('input');
    toggle.type = 'checkbox';
    const text = document.createElement('span');
    text.textContent = '링크를 아는 사내 사용자는 편집할 수 있음';
    label.append(toggle, text);

    const link = document.createElement('input');
    link.type = 'text';
    link.readOnly = true;
    link.hidden = true;

    const copy = document.createElement('button');
    copy.type = 'button';
    copy.textContent = '링크 복사';
    copy.hidden = true;

    const reissue = document.createElement('button');
    reissue.type = 'button';
    reissue.textContent = '새로 발급';
    reissue.hidden = true;

    function showLink(url: string | null) {
      // 주소는 발급 응답에만 있다. 대화상자를 다시 열면 켜짐만 알고 주소는 모른다 -
      // 토큰을 목록·조회로 흘리지 않기로 했기 때문이다(설계 6절).
      link.hidden = url === null;
      copy.hidden = url === null;
      link.value = url === null ? '' : location.origin + url;
    }

    async function issue() {
      try {
        showLink(await api.issueShareLink(workbookSeq));
        toggle.checked = true;
        reissue.hidden = false;
      } catch (error) {
        toggle.checked = false;
        fail(messageOf(error));
      }
    }

    toggle.addEventListener('change', () => {
      if (toggle.checked) {
        void issue();
        return;
      }
      void api.revokeShareLink(workbookSeq)
        .then(() => { showLink(null); reissue.hidden = true; })
        .catch((error) => { toggle.checked = true; fail(messageOf(error)); });
    });

    copy.addEventListener('click', () => {
      void navigator.clipboard?.writeText(link.value);
    });
    reissue.addEventListener('click', () => { void issue(); });

    try {
      const summary = await api.getWorkbook(workbookSeq);
      toggle.checked = summary.shared;
      reissue.hidden = !summary.shared;
    } catch {
      // 상태를 못 읽어도 켜고 끄는 것은 된다.
    }

    box.append(label, link, copy, reissue);
    return box;
  }
}

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : '요청이 실패했습니다.';
}
