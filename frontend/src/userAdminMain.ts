/**
 * 사용자 관리 화면을 그린다.
 *
 * <p>셈은 {@link ./userAdmin} 이 한다. 여기는 DOM 과 서버 왕복만 맡는다.
 */
import {
  canRegister, expiryText, sortedLines, type AdminUser, type Invite, type UserLine,
} from './userAdmin';

const root = document.getElementById('usersRoot')!;

function el<K extends keyof HTMLElementTagNameMap>(
  tag: K, className?: string, text?: string,
): HTMLElementTagNameMap[K] {
  const made = document.createElement(tag);
  if (className != null) made.className = className;
  if (text != null) made.textContent = text;
  return made;
}

async function ask(path: string, init?: RequestInit): Promise<unknown> {
  const response = await fetch(path, {
    credentials: 'same-origin',
    headers: init?.body == null ? {} : { 'Content-Type': 'application/json' },
    ...init,
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({ message: '' })) as { message?: string };
    throw new Error(body.message || `요청이 실패했습니다 (${response.status})`);
  }
  return response.status === 204 ? null : response.json();
}

const errorEl = el('p', 'users-error');
errorEl.hidden = true;

function fail(word: string): void {
  errorEl.textContent = word;
  errorEl.hidden = word === '';
}

/** 발급된 초대. 한 번만 보이므로 지우지 않고 쌓지도 않는다 - 늘 마지막 것만. */
const inviteEl = el('div', 'users-invite');
inviteEl.hidden = true;

function showInvite(invited: Invite): void {
  inviteEl.replaceChildren();
  inviteEl.append(el('strong', undefined,
    `${invited.email} 에게 전할 링크 (${expiryText(invited.expiresAt)} 까지)`));

  const urlEl = el('code', undefined, invited.inviteUrl);
  inviteEl.append(urlEl);

  const copy = el('button', undefined, '복사');
  copy.type = 'button';
  copy.addEventListener('click', () => {
    void navigator.clipboard.writeText(invited.inviteUrl)
      .then(() => { copy.textContent = '복사했다'; })
      .catch(() => { copy.textContent = '복사하지 못했다'; });
  });
  inviteEl.append(copy);
  inviteEl.hidden = false;
}

function registerForm(reload: () => void): HTMLFormElement {
  const form = el('form', 'users-form');

  const email = el('input');
  email.name = 'email';
  email.type = 'email';
  email.placeholder = 'name@example.com';
  email.required = true;

  const name = el('input');
  name.name = 'displayName';
  name.placeholder = '표시 이름 (안 적으면 이메일 앞부분)';

  const role = el('select');
  role.name = 'role';
  for (const [value, label] of [['USER', '사용자'], ['ADMIN', '관리자']]) {
    const option = el('option', undefined, label);
    option.value = value;
    role.append(option);
  }

  const submit = el('button', undefined, '등록하고 초대 링크 받기');
  submit.type = 'submit';

  form.append(email, name, role, submit);
  form.addEventListener('submit', (event) => {
    event.preventDefault();
    if (!canRegister(email.value)) {
      fail('이메일 모양이 아닙니다');
      return;
    }
    fail('');
    submit.disabled = true;
    void ask('/api/admin/users', {
      method: 'POST',
      body: JSON.stringify({
        email: email.value.trim(),
        displayName: name.value.trim(),
        role: role.value,
      }),
    })
      .then((made) => {
        showInvite(made as Invite);
        email.value = '';
        name.value = '';
        reload();
      })
      .catch((error: Error) => fail(error.message))
      .finally(() => { submit.disabled = false; });
  });
  return form;
}

function rowOf(line: UserLine, reload: () => void): HTMLElement {
  const row = el('div', 'users-row');
  row.append(
    el('div', 'users-cell', line.email),
    el('div', 'users-cell', line.displayName),
    el('div', 'users-cell', line.role),
    el('div', line.state === '막힘' ? 'users-cell users-off' : 'users-cell', line.state),
  );

  const actions = el('div', 'users-cell');
  if (line.canInvite) {
    const again = el('button', undefined, '초대 다시');
    again.type = 'button';
    again.addEventListener('click', () => {
      fail('');
      void ask(`/api/admin/users/${line.userKey}/invite`, { method: 'POST' })
        .then((made) => { showInvite(made as Invite); reload(); })
        .catch((error: Error) => fail(error.message));
    });
    actions.append(again);
  }

  const toggle = el('button', undefined, line.toggleLabel);
  toggle.type = 'button';
  toggle.addEventListener('click', () => {
    fail('');
    void ask(`/api/admin/users/${line.userKey}/disabled`, {
      method: 'POST',
      body: JSON.stringify({ disabled: line.toggleLabel === '막기' }),
    })
      .then(reload)
      .catch((error: Error) => fail(error.message));
  });
  actions.append(toggle);

  row.append(actions);
  return row;
}

const listEl = el('div');

function draw(users: AdminUser[], reload: () => void): void {
  listEl.replaceChildren();
  if (users.length === 0) {
    listEl.append(el('p', 'users-empty', '등록된 사용자가 없습니다.'));
    return;
  }
  const head = el('div', 'users-head');
  for (const word of ['이메일', '표시 이름', '역할', '상태', '']) {
    head.append(el('div', 'users-cell', word));
  }
  listEl.append(head);
  for (const line of sortedLines(users)) {
    listEl.append(rowOf(line, reload));
  }
}

function reload(): void {
  void ask('/api/admin/users')
    .then((users) => draw(users as AdminUser[], reload))
    .catch((error: Error) => fail(error.message));
}

root.append(registerForm(reload), errorEl, inviteEl, listEl);
reload();
