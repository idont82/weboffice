/**
 * @univerjs/presets 의 createUniver 대체물.
 *
 * umbrella 패키지 @univerjs/presets 는 preset-sheets-advanced 를 통해
 * @univerjs-pro/* 18개를 끌어오므로 사용하지 않는다(라이선스 제약).
 * preset 자체는 { plugins: [...] } 구조이므로 직접 등록하면 동일하게 동작한다.
 */
import { Univer } from '@univerjs/core';
import { FUniver } from '@univerjs/core/lib/facade';

type PluginEntry = unknown;
interface Preset {
  plugins: PluginEntry[];
}

export function createUniverFromPresets(
  config: Record<string, unknown>,
  presets: Preset[],
): { univer: Univer; univerAPI: FUniver } {
  const univer = new Univer(config as never);

  // 같은 플러그인을 두 번 등록하지 않는다.
  //
  // 프리셋끼리 밑바탕을 나눠 쓴다 - 시트 프리셋과 글 프리셋이 둘 다 network·ui·
  // engine-render 를 품는다. 그대로 넘기면 Univer 가 "duplicated plugin name" 으로
  // 던지고 화면이 통째로 안 뜬다(2026-08-11 콘솔 실측).
  //
  // 먼저 온 것을 남긴다. 프리셋 차례가 곧 우선순위다.
  //
  // 클래스가 아니라 플러그인 이름으로 견준다. 번들이 같은 패키지를 두 벌 담으면
  // (프리셋 하나만 나중에 불러올 때 그렇게 된다) 클래스가 서로 달라 견주기가
  // 빗나가고, 렌더 엔진이 두 번 올라가 화면이 통째로 죽는다(2026-08-11 실측:
  // duplicated plugin name for "UNIVER_RENDER_ENGINE_PLUGIN").
  const registered = new Set<unknown>();

  for (const preset of presets) {
    for (const entry of preset.plugins) {
      const [ctor, pluginConfig] = Array.isArray(entry) ? entry : [entry, undefined];
      const key = (ctor as { pluginName?: string })?.pluginName ?? ctor;
      if (registered.has(key)) {
        continue;
      }
      registered.add(key);
      univer.registerPlugin(ctor as never, pluginConfig as never);
    }
  }

  return { univer, univerAPI: FUniver.newAPI(univer) };
}
