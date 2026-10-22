/**
 * 단축키에 쓰는 셈.
 *
 * <p>키를 잡아 명령에 잇는 일은 Univer 의 단축키 서비스가 한다. 여기에는 그 서비스가
 * 모르는 것 - 우리 시트 목록에서 옆 시트를 고르는 규칙만 둔다.
 */

/** 시트 목록에서 필요한 것만. 화면 전체 타입을 끌어오지 않는다. */
interface SheetLike {
  sheetSeq: number;
  sheetName: string;
}

/**
 * 옆 시트. 없으면 null 이다.
 *
 * <p>끝에서 감기지 않는다. 엑셀도 여기서 멈춘다 - 감기면 마지막 시트에서 한 번 더
 * 눌렀을 때 첫 시트로 튀어 어디로 갔는지 모른다.
 *
 * @param step 1 이면 다음, -1 이면 이전
 */
export function neighborSheet<T extends SheetLike>(
  sheets: T[], currentSeq: number, step: number,
): T | null {
  const at = sheets.findIndex((sheet) => sheet.sheetSeq === currentSeq);
  if (at < 0) {
    return null;
  }
  return sheets[at + step] ?? null;
}
