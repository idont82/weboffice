/**
 * 글 문서의 그림을 담는 자리.
 *
 * <p>Univer 는 글 그림을 문서 자료의 {@code drawings} 가 아니라 플러그인 리소스
 * ({@code DOC_DRAWING_PLUGIN})에서 읽는다. 열 때 그 리소스가 참이고, 거기 없는
 * 그림은 모델에서 지워진다.
 *
 * <p>우리는 문서 한 벌을 {@code getSnapshot()} 으로 떠서 저장하는데, 그 안의
 * {@code resources} 는 <b>문서를 열 때 받은 그대로</b>다 - 그림을 넣어도 안
 * 바뀐다. 그래서 빈 리소스를 저장하게 되고, 다음에 열면 그 빈 것이 그림을 덮어
 * 지운다(2026-10-16 실측: 붙여넣은 그림이 다시 열면 사라졌다. 0.25 에서도 같다).
 *
 * <p>저장할 때는 {@link withFreshResources} 로 지금 리소스를 끼워 넣고, 이미
 * 그렇게 저장돼 버린 옛 문서는 {@link repairDrawingResource} 로 되살린다.
 */

/** 글 그림이 담기는 리소스 이름. Univer 가 정한 이름이다. */
export const DOC_DRAWING_RESOURCE = 'DOC_DRAWING_PLUGIN';

/** 리소스 한 칸. 내용은 해석하지 않고 글자 그대로 왕복시킨다. */
export interface UnitResource {
  name: string;
  data: string;
}

interface DocSnapshot {
  drawings?: Record<string, unknown>;
  drawingsOrder?: string[];
  resources?: UnitResource[];
  [key: string]: unknown;
}

/** 저장할 한 벌에 지금 리소스를 끼운다. 모델이 들고 있던 옛 것은 버린다. */
export function withFreshResources<T extends DocSnapshot>(
  snapshot: T, resources: UnitResource[],
): T & { resources: UnitResource[] } {
  return { ...snapshot, resources };
}

/**
 * 빈 그림 리소스를 본문에 적힌 그림으로 채운다.
 *
 * <p>리소스에 이미 그림이 있으면 그것이 참이다 - 손대지 않는다.
 */
export function repairDrawingResource<T extends DocSnapshot>(
  snapshot: T,
): T & { resources: UnitResource[] } {
  const resources = snapshot.resources ?? [];
  const drawings = snapshot.drawings ?? {};
  const ids = Object.keys(drawings);
  if (ids.length === 0) {
    return { ...snapshot, resources };
  }

  const saved = resources.find((one) => one.name === DOC_DRAWING_RESOURCE);
  if (saved != null && countOf(saved.data) > 0) {
    return { ...snapshot, resources };
  }

  const filled: UnitResource = {
    name: DOC_DRAWING_RESOURCE,
    data: JSON.stringify({ data: drawings, order: snapshot.drawingsOrder ?? ids }),
  };
  return {
    ...snapshot,
    resources: [...resources.filter((one) => one.name !== DOC_DRAWING_RESOURCE), filled],
  };
}

/** 리소스 글자에 담긴 그림 수. 못 읽으면 없는 것으로 본다. */
function countOf(data: string): number {
  try {
    const parsed = JSON.parse(data) as { data?: Record<string, unknown> };
    return Object.keys(parsed?.data ?? {}).length;
  } catch {
    return 0;
  }
}
