/**
 * 문서 제목 표시와 수정.
 *
 * 입력 상자 대신 contenteditable 을 쓴다. 제목 길이에 맞춰 폭이 늘고, 툴바에
 * 얹었을 때 다른 글자와 같은 줄에 앉는다.
 *
 * 바뀐 값만 올려보낸다. 누르기만 하고 안 고쳐도 저장하면 이력이 헛되이 쌓인다 -
 * 셀 저장에서 겪은 것과 같은 부류다.
 */
export interface TitleEditorOptions {
  element: HTMLElement;
  canWrite: boolean;
  onRename: (name: string) => void;
}

export function createTitleEditor({ element, canWrite, onRename }: TitleEditorOptions) {
  let committed = '';

  function revert() {
    element.textContent = committed;
  }

  function commit() {
    const next = (element.textContent ?? '').trim();
    if (!next || next === committed) {
      // 빈 제목은 서버도 거부한다. 화면이 빈 채로 남으면 무엇이 잘못됐는지 안 보인다.
      revert();
      return;
    }
    committed = next;
    element.textContent = next;
    onRename(next);
  }

  function setEditable(editable: boolean) {
    if (editable) {
      // true 로 두면 붙여넣기로 서식과 태그가 들어온다.
      // 프로퍼티가 아니라 속성으로 넣는다. CSS 의 .title[contenteditable] 이 속성을
      // 보고, jsdom 은 프로퍼티 대입을 속성에 반영하지 않는다.
      element.setAttribute('contenteditable', 'plaintext-only');
    } else {
      element.removeAttribute('contenteditable');
    }
  }

  // 리스너는 권한과 무관하게 한 번만 단다. 문서마다 권한이 달라 나중에 바뀌는데,
  // 그때 다시 달면 같은 리스너가 겹쳐 붙는다. 편집 불가 상태에서는 사용자가 글자를
  // 바꿀 수 없으므로 blur 가 와도 commit 은 되돌리기만 한다.
  element.addEventListener('blur', commit);
  element.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      element.blur();
    }
    if (event.key === 'Escape') {
      revert();
      element.blur();
    }
  });
  setEditable(canWrite);

  return {
    show(name: string) {
      committed = name;
      element.textContent = name;
    },
    setEditable,
  };
}
