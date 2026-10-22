/**
 * 만든 유닛을 화면에 붙인다.
 *
 * <p>Univer 1.0 의 작업대는 화면이 뜬 뒤 <b>300ms 타이머 안에서</b> 유닛과 캔버스를
 * 잇는 구독(focused$ · created$)을 건다. 그보다 먼저 만든 유닛은 그 구독을 못 만나
 * 영영 안 붙는다 - 가져온 문서도, 뷰어도 쪽 수는 세어 놓고 화면만 비었다. 오류도
 * 안 난다(2026-10-16 실측). 0.25 는 봐줬다.
 *
 * <p>그래서 구독이 걸린 뒤({@code Rendered}) 유닛을 한 번 더 집어 준다.
 */

import { LifecycleService, LifecycleStages, IUniverInstanceService, type Univer }
  from '@univerjs/core';

export async function showUnit(univer: Univer, unitId: string): Promise<void> {
  const injector = univer.__getInjector();
  try {
    // 그 단계를 유닛보다 "먼저" 기다리면 안 된다 - 첫 유닛이 생겨야 단계가
    // 오르므로 서로 기다리다 멈춘다(실측: 화면이 통째로 멈췄다).
    await injector.get(LifecycleService).onStage(LifecycleStages.Rendered);
  } catch {
    // 그 단계에 영영 못 가는 수가 있다(화면이 먼저 닫혔다). 문서를 여는 길까지
    // 무너뜨릴 일은 아니다.
    return;
  }
  injector.get(IUniverInstanceService).focusUnit(unitId);
}
