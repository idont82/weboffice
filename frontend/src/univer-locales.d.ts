/**
 * Univer 로케일 번들의 타입 선언.
 *
 * 이 경로는 패키지의 exports 맵에 없어서 tsc 가 못 찾는다(vite 는 실제 파일을
 * 그대로 읽으므로 문제없이 번들된다). 선언이 없으면 tsc --noEmit 이 항상 오류를
 * 내고, 그러면 타입 검사를 아무도 안 돌리게 된다.
 */
declare module '@univerjs/preset-sheets-core/lib/locales/*' {
  const locale: Record<string, unknown>;
  export default locale;
}

declare module '@univerjs/preset-sheets-drawing/lib/locales/*' {
  const locale: Record<string, unknown>;
  export default locale;
}
