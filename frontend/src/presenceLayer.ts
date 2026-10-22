/**
 * 상대 위치를 격자 위에 그린다.
 *
 * Univer 장면(scene)에 도형을 얹는다. DOM 을 겹치지 않는 이유: 스크롤과 확대를
 * 따라가려면 그 둘을 계속 좇아야 하는데, 장면 안의 도형은 좌표계가 같아 저절로
 * 따라간다. 떠 있는 그림이 쓰는 좌표계와도 같다.
 *
 * 이름표까지 직접 그린다. engine-render 의 Text 는 위치를 준 채로 만들면 반드시
 * 터진다 - 생성자가 스켈레톤을 만들기 전에 makeDirty() 를 부르고, Text 의
 * makeDirty 는 그 스켈레톤을 읽는다("Cannot read properties of undefined
 * (reading 'makeDirty')", 2026-08-06 실측). 예외가 프레임 핸들러에서 삼켜져
 * 화면에는 아무 표시도 안 났다.
 */
import { Rect } from '@univerjs/engine-render';

import { colorKeyOf, colorOf, describePresence, type Presence } from './presence';
import { trackMoves, visibleLabels, type SeatMove } from './presenceLabels';

const KEY_PREFIX = 'wo-presence-';

/**
 * 이름표를 몇 초나 보일지.
 *
 * <p>이름표는 자리 위 칸을 덮는다. 계속 두면 그 칸의 글자를 영영 못 읽는다
 * (2026-08-07 지적). 누가 어디 있는지는 테두리로 남고 이름은 접속자 칩에 있다.
 */
const LABEL_TTL_MS = 8_000;
const LABEL_HEIGHT = 17;
const LABEL_FONT = '12px sans-serif';
const LABEL_PADDING = 6;

interface Box { left: number; top: number; width: number; height: number }

/**
 * 테두리 + 이름표.
 *
 * <p>도형의 상자는 이름표까지 감싸도록 위(또는 아래)로 늘려 잡는다. 상자 밖에
 * 그리면 잘린다. 그래서 {@code _draw} 를 통째로 갈아 끼운다 - 부모를 부르면
 * 늘려 잡은 상자 전체에 테두리가 쳐진다.
 */
class PresenceBox extends Rect {
  constructor(
    key: string,
    props: Box,
    private readonly label: string,
    private readonly tone: string,
    /** 이름표가 위인지 아래인지. 첫 행에서는 위로 나가면 화면 밖이다. */
    private readonly labelOnTop: boolean,
    private readonly range: Box,
  ) {
    super(key, { ...props, evented: false } as never);
  }

  protected override _draw(ctx: unknown): void {
    const canvas = ctx as CanvasRenderingContext2D;
    canvas.save();

    const rangeTop = this.labelOnTop ? LABEL_HEIGHT + 1 : 0;

    // 이름이 비면 이름표를 안 그린다. 시간이 지나 이름표만 걷은 상태다 -
    // 테두리는 남아 누가 어디 있는지는 계속 보인다.
    if (this.label !== '') {
      canvas.font = LABEL_FONT;
      const labelWidth = canvas.measureText(this.label).width + LABEL_PADDING * 2;
      const labelTop = this.labelOnTop ? 0 : this.range.height + 1;

      canvas.fillStyle = this.tone;
      canvas.fillRect(0, labelTop, labelWidth, LABEL_HEIGHT);
      canvas.fillStyle = '#ffffff';
      canvas.textBaseline = 'middle';
      canvas.fillText(this.label, LABEL_PADDING, labelTop + LABEL_HEIGHT / 2);
    }

    // 속을 채우지 않는다. 칠하면 그 아래 셀 글자가 안 읽힌다.
    canvas.strokeStyle = this.tone;
    canvas.lineWidth = 2;
    canvas.strokeRect(1, rangeTop + 1, this.range.width - 2, this.range.height - 2);

    canvas.restore();
  }
}

export interface PresenceLayerDeps {
  /** 장면. 없으면(아직 안 그려졌으면) 아무것도 하지 않는다. */
  scene: () => { addObject: (object: unknown) => unknown } | null;
  /** 셀 범위를 픽셀 상자로 바꾼다. 행 높이·열 너비를 아는 쪽이 넘겨준다. */
  toRect: (presence: Presence) => Box | null;
  /** 이름 목록. 화면 밖에 있는 사람도 알 수 있게 툴바에도 함께 띄운다. */
  showNames: (labels: { text: string; color: string }[]) => void;
}

export function createPresenceLayer({ scene, toRect, showNames }: PresenceLayerDeps) {
  const drawn: unknown[] = [];
  let moves = new Map<string, SeatMove>();
  let lastList: Presence[] = [];
  let fadeTimer: ReturnType<typeof setTimeout> | null = null;

  function removeAll() {
    for (const object of drawn) {
      (object as { dispose?: () => void }).dispose?.();
    }
    drawn.length = 0;
  }

  return {
    /**
     * 통째로 다시 그린다.
     *
     * <p>사람이 몇 명 안 되고, 하나가 움직이면 색·이름표까지 함께 바뀐다.
     * 부분 갱신은 얻는 것보다 어긋날 여지가 크다.
     */
    render(list: Presence[]) {
      lastList = list;
      moves = trackMoves(moves, list.map((seat) => ({
        key: String(seat.lockSeq),
        // 사람이 아니라 "어디에 앉아 있나" 가 모습이다. 자리를 옮겨야 다시 센다.
        signature: `${seat.fromRowNo}:${seat.fromColumnNo}:${seat.toRowNo}:${seat.toColumnNo}`,
      })), Date.now());
      this.draw();

      // 시간이 지나면 이름표만 지운다. 그때 다시 그려야 하므로 예약해 둔다.
      if (fadeTimer !== null) clearTimeout(fadeTimer);
      fadeTimer = setTimeout(() => this.draw(), LABEL_TTL_MS + 100);
    },

    /** 지금 시각 기준으로 다시 그린다. 이름표가 살아 있는 자리만 이름을 단다. */
    draw() {
      const list = lastList;
      const showing = visibleLabels(moves, Date.now(), LABEL_TTL_MS);
      removeAll();
      showNames(list.map((seat) => ({
        text: describePresence(seat), color: colorOf(colorKeyOf(seat)),
      })));

      const target = scene();
      if (target === null) return;

      for (const seat of list) {
        const range = toRect(seat);
        if (range === null) continue;

        const labelOnTop = range.top >= LABEL_HEIGHT + 1;
        const box: Box = {
          left: range.left,
          top: labelOnTop ? range.top - LABEL_HEIGHT - 1 : range.top,
          // 이름이 범위보다 길 수 있다. 상자가 좁으면 이름표가 잘린다.
          width: Math.max(range.width, 240),
          height: range.height + LABEL_HEIGHT + 1,
        };

        const shape = new PresenceBox(KEY_PREFIX + seat.lockSeq, box,
          showing.has(String(seat.lockSeq)) ? describePresence(seat) : '',
          colorOf(colorKeyOf(seat)), labelOnTop, range);

        target.addObject(shape);
        drawn.push(shape);
      }
    },

    clear() {
      if (fadeTimer !== null) {
        clearTimeout(fadeTimer);
        fadeTimer = null;
      }
      lastList = [];
      moves = new Map();
      removeAll();
      showNames([]);
    },
  };
}
