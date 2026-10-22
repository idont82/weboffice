import { describe, expect, it } from 'vitest';

import { fillTabLeaders } from './tabLeader';

/** 글자 하나를 10px, 점 하나를 4px 로 재는 자. 셈만 본다. */
const 자 = (text: string) => (text.startsWith('.') ? text.length * 4 : text.length * 10);

/** 문단마다 같은 자를 쓴다. */
const 자를준다 = () => 자;

describe('목차 점선 채우기', () => {
  it('탭 자리를 점으로 채워 번호를 오른쪽 끝에 세운다', () => {
    // Univer 는 탭 정지를 모른다 - 탭을 늘 같은 폭으로 그려 번호가 제목 바로
    // 뒤에 붙는다. 화면이 폭을 재서 손수 채운다.
    const rows = fillTabLeaders([
      { paragraphNo: 0, text: '1. Introduction\t3',
        style: '{"tabRight":200,"tabLeader":"dot"}', runs: null },
    ], 자를준다);

    // 제목 15자 = 150px, 번호 1자 = 10px, 남은 40px 의 94% = 37.6px, 점 4px 로 9개
    expect(rows[0].text).toBe(`1. Introduction${'.'.repeat(9)}3`);
  });

  it('점선이 아니면 공백으로 민다', () => {
    const rows = fillTabLeaders([
      { paragraphNo: 0, text: '가\t9', style: '{"tabRight":100}', runs: null },
    ], 자를준다);

    // 남은 폭 80px 의 94% = 75.2px, 공백 하나 10px → 7칸
    expect(rows[0].text).toBe(`가${' '.repeat(7)}9`);
  });

  it('글자 서식 자리를 밀린 만큼 옮긴다', () => {
    // 안 옮기면 쪽 번호에 걸린 서식이 점선에 걸린다.
    const rows = fillTabLeaders([
      { paragraphNo: 0, text: '가\t9', style: '{"tabRight":100,"tabLeader":"dot"}',
        runs: '[{"st":2,"ed":3,"ts":{"bl":1}}]' },
    ], 자를준다);

    const runs = JSON.parse(rows[0].runs!) as { st: number; ed: number }[];
    expect(rows[0].text.endsWith('9')).toBe(true);
    expect(runs[0].st).toBe(rows[0].text.length - 1);
    expect(runs[0].ed).toBe(rows[0].text.length);
  });

  it('들여쓴 만큼 채울 자리를 줄인다', () => {
    // 들여쓰기를 안 빼면 줄이 넘쳐 두 줄로 접힌다.
    const rows = fillTabLeaders([
      { paragraphNo: 0, text: '가	9',
        style: '{"tabRight":200,"tabLeader":"dot","paragraphStyle":{"indentStart":{"v":40}}}',
        runs: null },
    ], 자를준다);

    // (200 - 40 - 10 - 10) * 0.94 / 4 = 32개
    expect(rows[0].text).toBe(`가${'.'.repeat(32)}9`);
  });

  it('탭이 없거나 탭 정지가 없으면 그대로 둔다', () => {
    const rows = fillTabLeaders([
      { paragraphNo: 0, text: '탭 없음', style: '{"tabRight":100}', runs: null },
      { paragraphNo: 1, text: '정지\t없음', style: null, runs: null },
    ], 자를준다);

    expect(rows[0].text).toBe('탭 없음');
    expect(rows[1].text).toBe('정지\t없음');
  });

  it('채울 자리가 없으면 한 칸만 둔다', () => {
    // 제목이 길어 번호와 붙으면 글자가 겹쳐 보인다.
    const rows = fillTabLeaders([
      { paragraphNo: 0, text: '아주 긴 제목이다\t12',
        style: '{"tabRight":30,"tabLeader":"dot"}', runs: null },
    ], 자를준다);

    expect(rows[0].text).toBe('아주 긴 제목이다 12');
  });
});
