/**
 * 목록(글머리표·번호) 모양.
 *
 * <p>Univer 가 들고 있는 기본 목록은 글머리표가 12pt 이고 층마다 21px 만
 * 들여쓴다. 워드는 11pt 짜리 작은 점에 층마다 0.5인치(48px)다 - 기본을 그대로
 * 쓰면 줄이 6px 씩 높아지고 들여쓰기가 얕아, 쪽마다 서너 줄씩 덜 들어가
 * 원본과 쪽 나눔이 어긋났다(2026-08-19 지적: 4쪽이 "2. Role-Based" 로
 * 시작해야 하는데 앞 쪽 글 세 줄이 넘어왔다).
 *
 * <p>이름을 Univer 기본과 같게 둔다. 문서에 실어 보내면 기본 위에 덮인다
 * (DocumentDataModel.getBulletPresetList 실측).
 */

/** 워드 목록 한 층의 들여쓰기(twip 720). */
const INDENT_PER_LEVEL = 48;

/** 글머리표가 글보다 앞에 걸리는 거리(twip 360). */
const HANGING = 24;

/** 층 수. 워드도 아홉 층까지다. */
const LEVELS = 9;

/** 워드 기본 글머리표. 층마다 돌려 쓴다. */
const SYMBOLS = ['•', 'o', '▪'];

/** 번호 매기기 모양. 1. a. i. 를 돌려 쓴다(워드 기본). */
const NUMBERS = [
  { glyphFormat: '%1.', glyphType: 2 },
  { glyphFormat: '%2.', glyphType: 5 },
  { glyphFormat: '%3.', glyphType: 7 },
];

interface NestingLevel {
  glyphSymbol?: string;
  glyphFormat?: string;
  glyphType?: number;
  bulletAlignment: number;
  textStyle: { fs: number };
  startNumber: number;
  paragraphProperties: { hanging: { v: number }; indentStart: { v: number } };
}

function levelsOf(fontSize: number,
  shape: (level: number) => Partial<NestingLevel>): NestingLevel[] {
  return Array.from({ length: LEVELS }, (_unused, level) => ({
    ...shape(level),
    bulletAlignment: 1,
    textStyle: { fs: fontSize },
    startNumber: 0,
    paragraphProperties: {
      hanging: { v: HANGING },
      indentStart: { v: INDENT_PER_LEVEL * (level + 1) },
    },
  } as NestingLevel));
}

/**
 * 문서에 실을 목록 모양. 글머리표 글자 크기는 본문에 맞춘다.
 *
 * @param fontSize 본문 글자 크기(pt). 워드 기본은 11pt 다.
 */
export function docLists(fontSize = 11) {
  return {
    BULLET_LIST: {
      listType: 'BULLET_LIST',
      nestingLevel: levelsOf(fontSize,
        (level) => ({ glyphSymbol: SYMBOLS[level % SYMBOLS.length] })),
    },
    ORDER_LIST: {
      listType: 'ORDER_LIST',
      nestingLevel: levelsOf(fontSize, (level) => NUMBERS[level % NUMBERS.length]),
    },
  };
}
