/**
 * 상단 메뉴바.
 *
 * <p>Univer 의 리본 탭 줄(홈·삽입·수식·데이터)을 우리 메뉴로 바꾼다. 탭은 누를
 * 때마다 아랫줄이 통째로 바뀌어서, 자주 쓰는 서식 단추가 눈앞에서 사라진다.
 * 메뉴로 두면 아랫줄은 늘 같은 것을 보이고 나머지는 필요할 때만 펼친다.
 *
 * <p>Univer 안에 끼워 넣지 않고 우리 DOM 으로 만든다. 저쪽은 React 가 그리므로
 * 자식을 밀어 넣으면 다시 그릴 때 사라진다 - 파일 메뉴에서 겪었다.
 */

export interface MenuItem {
  label: string;
  run: () => void;
  /**
   * 글자 앞에 둘 아이콘. SVG 문자열이다.
   *
   * <p>없어도 자리는 잡아 둔다 - 아이콘 없는 항목만 들여쓰기가 어긋나면 목록이
   * 삐뚤어 보인다. 값은 우리가 menuIcons.ts 에 적어 둔 것만 들어오므로 그대로
   * 넣는다(바깥에서 온 문자열이 아니다).
   */
  icon?: string;
  /** 지금 쓸 수 있는지. 없으면 늘 쓸 수 있다. 펼칠 때마다 다시 묻는다. */
  enabled?: () => boolean;
  /**
   * 지금 값인지. 줄 간격처럼 여럿 가운데 하나가 지금 값인 항목에 쓴다.
   *
   * <p>펼칠 때마다 다시 묻는다 - 한 번 보고 말면 커서를 옮겨도 표시가 그대로
   * 남는다.
   */
  checked?: () => boolean;
  /** 이 항목 위에 구분선을 긋는다. */
  separated?: boolean;
  /**
   * 실행 뒤 포커스를 건드리지 않는다.
   *
   * <p>표 넣기·찾기처럼 창을 여는 항목이다. 뒷정리로 편집기에 포커스를 주면 방금
   * 뜬 창이 곧바로 닫힌다(2026-08-11 실측: 표가 들어가지 않았다).
   */
  keepFocus?: boolean;
}

export interface MenuDefinition {
  key: string;
  label: string;
  items: MenuItem[];
}

export interface MenuBarOptions {
  /**
   * 항목을 실제로 실행한 뒤 부른다.
   *
   * <p>포커스를 격자로 돌려주는 자리다. 누른 뒤 포커스가 메뉴 단추에 남으면 Univer
   * 단축키가 안 듣는다 - 그쪽은 포커스가 격자 안일 때만 키를 받는다. 행을 넣고
   * Ctrl+Z 를 눌러도 아무 일이 없었다(2026-08-10 지적).
   */
  afterRun?: () => void;
}

export interface MenuBar {
  /** 펼친 것을 닫는다. 화면이 바뀔 때 부른다. */
  close(): void;
}

export function createMenuBar(container: HTMLElement, menus: MenuDefinition[],
                              options: MenuBarOptions = {}): MenuBar {
  // 앞서 그린 것을 지운다. 시트를 열었다 글 문서를 열면 메뉴가 통째로 바뀌는데,
  // 비우지 않으면 두 벌이 나란히 선다.
  container.replaceChildren();

  let openKey: string | null = null;

  const titles = new Map<string, HTMLButtonElement>();
  const lists = new Map<string, HTMLElement>();

  for (const menu of menus) {
    const box = document.createElement('div');
    box.className = 'menu-box';

    const title = document.createElement('button');
    title.type = 'button';
    title.className = 'menu-title';
    title.textContent = menu.label;
    title.dataset.menu = menu.key;
    title.setAttribute('aria-haspopup', 'true');
    title.setAttribute('aria-expanded', 'false');

    const list = document.createElement('div');
    list.className = 'menu-list';
    list.dataset.menuList = menu.key;
    list.hidden = true;

    for (const item of menu.items) {
      const button = document.createElement('button');
      button.type = 'button';
      const icon = document.createElement('span');
      icon.className = 'menu-icon';
      icon.innerHTML = item.icon ?? '';
      button.append(icon, document.createTextNode(item.label));
      if (item.separated) {
        button.classList.add('menu-separated');
      }
      button.addEventListener('click', () => {
        // disabled 여도 부르는 브라우저가 있다. 한 번 더 막는다.
        if (button.disabled) return;
        close();
        item.run();
        if (!item.keepFocus) {
          options.afterRun?.();
        }
      });
      list.append(button);
    }

    box.append(title, list);
    container.append(box);
    titles.set(menu.key, title);
    lists.set(menu.key, list);

    title.addEventListener('click', () => {
      if (openKey === menu.key) {
        close();
      } else {
        openMenu(menu);
      }
    });
  }

  function openMenu(menu: MenuDefinition) {
    close();
    // 쓸 수 있는지는 펼칠 때마다 다시 묻는다. 만들 때 한 번 보고 말면 시작
    // 화면에서 굳은 상태가 문서를 연 뒤에도 그대로 남는다.
    const list = lists.get(menu.key)!;
    const buttons = list.querySelectorAll('button');
    menu.items.forEach((item, at) => {
      buttons[at].disabled = item.enabled ? !item.enabled() : false;
      if (item.checked) {
        const now = item.checked();
        buttons[at].setAttribute('aria-checked', String(now));
        buttons[at].classList.toggle('menu-checked', now);
      }
    });
    list.hidden = false;
    titles.get(menu.key)!.setAttribute('aria-expanded', 'true');
    openKey = menu.key;
  }

  function close() {
    if (openKey === null) return;
    lists.get(openKey)!.hidden = true;
    titles.get(openKey)!.setAttribute('aria-expanded', 'false');
    openKey = null;
  }

  // 바깥을 누르면 닫는다. 메뉴 안을 누른 것은 항목 처리가 알아서 닫는다.
  document.addEventListener('pointerdown', (event) => {
    if (openKey === null) return;
    const target = event.target as Node | null;
    if (target && container.contains(target)) return;
    close();
  }, { capture: true });

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') close();
  });

  return { close };
}
