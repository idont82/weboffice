/**
 * 행·열을 지운 뒤 어디를 고를 것인가.
 *
 * <p>Univer 는 지운 줄을 고른 채로 둔다. 그 자리에는 이제 다른 줄이 올라와 있어서,
 * 그대로 두면 다음 조작이 엉뚱한 줄에 걸린다(2026-08-27 지적: 여러 줄을 지웠더니
 * 블록이 그대로 남았다). 엑셀처럼 지운 자리 한 칸만 고른다.
 */

const REMOVE_ROW = 'sheet.command.remove-row';
const REMOVE_COL = 'sheet.command.remove-col';

interface RemoveParams {
  range?: { startRow?: number; startColumn?: number; endRow?: number; endColumn?: number };
}

/** 지운 뒤 고를 자리. 그 명령이 아니거나 범위를 모르면 null 이다. */
export function spotAfterRemove(commandId: string, params: RemoveParams | undefined):
{ row: number; column: number } | null {
  if (commandId !== REMOVE_ROW && commandId !== REMOVE_COL) {
    return null;
  }
  const range = params?.range;
  if (range?.startRow == null || range.startColumn == null) {
    return null;
  }
  return { row: range.startRow, column: range.startColumn };
}
