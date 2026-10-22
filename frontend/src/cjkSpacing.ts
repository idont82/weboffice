/**
 * 한글과 영문 사이 자동 간격을 켜고 끄는 스위치 이름.
 *
 * <p>{@code false} 면 붙여 쓴다. 켜고 끄는 자리는 main.ts 다 - 시트는 끄고
 * (엑셀은 이 간격을 넣지 않는다) 워드 문서는 켠다(워드는 넣는다).
 */
export const CJK_SPACING_FLAG = '__woCjkSpacing';

/** Univer 안에서 이 간격을 넣는 자리. 판올림하면 여기가 바뀔 수 있다. */
const CALL = 'addCJKLatinSpacing(shapedTextList);';

/**
 * Univer 의 한글·영문 자동 간격에 스위치를 단다.
 *
 * <p>Univer 는 한글 글자 앞뒤에 영문·숫자가 붙으면 글자 폭의 1/4 을 넣는다
 * (engine-render 의 {@code addCJKLatinSpacing}). 워드가 하는 일이라 문서에서는
 * 맞지만, 셀을 편집할 때 "'25.10월" 이 "'25.10 월" 로 벌어져 보인다
 * (2026-08-18 지적). 엑셀은 이 간격을 넣지 않는다.
 *
 * <p>끄는 설정이 없다 - 호출이 조건 없이 박혀 있고, 다음 줄기(1.0.0-beta.1)도
 * 같다(실물 확인). 그래서 빌드할 때 우리가 스위치를 끼운다. 함수는 그대로 둔다.
 *
 * @throws 자리를 못 찾으면. 조용히 지나가면 고친 줄 알고 벌어진 채로 나간다.
 */
export function gateCjkSpacing(code: string): string {
  if (!code.includes(CALL)) {
    throw new Error(
      `Univer 안에서 ${CALL} 을 찾지 못했습니다. 판올림으로 자리가 바뀐 것 같습니다`,
    );
  }
  return code.replaceAll(
    CALL,
    `globalThis.${CJK_SPACING_FLAG} !== false && ${CALL}`,
  );
}
