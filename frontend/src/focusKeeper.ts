/**
 * 글 편집기 포커스 지키기.
 *
 * <p>글자색·배경색 팝업이 닫힐 때 포커스를 편집기가 아니라 {@code body} 에 떨군다.
 * 화면에는 커서가 그대로 보이지만 키보드는 아무 데도 닿지 않아 글자가 입력되지
 * 않는다(2026-08-11 실측: 눌린 키의 target 이 BODY 였다).
 *
 * <p>떨어진 포커스만 주워 온다. 제목이나 공유 창처럼 다른 입력칸으로 옮겨 간
 * 포커스는 건드리지 않는다 - 그러면 그쪽에 글을 못 친다.
 */

/** 편집기 요소의 id 를 알려 준다. 글 문서가 닫혀 있으면 null. */
export type EditorIdSource = () => string | null;

export function keepEditorFocus(editorId: EditorIdSource): void {
  document.addEventListener('focusout', () => {
    // 포커스가 어디에 앉는지는 이 이벤트 뒤에 정해진다. 한 박자 기다린다.
    setTimeout(() => {
      const id = editorId();
      if (id == null) {
        return;
      }
      const active = document.activeElement;
      if (active != null && active !== document.body) {
        return;
      }
      document.getElementById(id)?.focus();
    }, 0);
  }, true);
}
