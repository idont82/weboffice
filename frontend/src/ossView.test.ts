import { describe, it, expect, vi } from 'vitest';

import { renderOss, GROUPS, type OssItem, type OssPayload } from './ossView';

function item(part: Partial<OssItem> = {}): OssItem {
  return {
    coords: ['org.example:thing'],
    name: '것',
    version: '1.0',
    license: 'Apache-2.0',
    url: 'https://example.com',
    side: 'BACKEND',
    kind: 'OSS',
    dist: 'SHIPPED',
    what: '쓰임',
    ...part,
  };
}

function payload(items: OssItem[]): OssPayload {
  return {
    items,
    overview: {
      open: items.filter((one) => one.kind === 'OSS').length,
      closed: items.filter((one) => one.kind !== 'OSS').length,
      shipped: items.filter((one) => one.dist === 'SHIPPED').length,
      devOnly: items.filter((one) => one.dist !== 'SHIPPED').length,
      licenses: [{ license: 'Apache-2.0', count: items.length }],
    },
  };
}

describe('GROUPS', () => {
  it('배포물 · 개발 전용 · 오픈소스가 아닌 것 세 묶음이다', () => {
    // 라이선스 점검에서 갈라 보는 세 갈래다. 한 표에 섞으면 무엇이 고지 대상인지 흐려진다.
    expect(GROUPS.map((one) => one.key)).toEqual(['SHIPPED', 'DEV_ONLY', 'CLOSED']);
  });
});

describe('renderOss', () => {
  it('요약 숫자를 위에 보여 준다', () => {
    const box = document.createElement('div');

    renderOss(box, payload([item(), item({ name: '둘', kind: 'COMMERCIAL' })]));

    expect(box.textContent).toContain('오픈소스');
    expect(box.textContent).toContain('1');
  });

  it('라이선스 갈래를 딱지로 나눠 적는다', () => {
    // 한 줄로 이어 붙이면 'MPL-2.0 OR EPL-1.0' 같은 긴 이름이 어디서 갈라지는지 안 보인다.
    const box = document.createElement('div');
    const base = payload([item()]);

    renderOss(box, {
      ...base,
      overview: {
        ...base.overview,
        licenses: [{ license: 'Apache-2.0', count: 15 }, { license: 'MIT', count: 7 }],
      },
    });

    const chips = [...box.querySelectorAll('.oss-chip')].map((one) => one.textContent);
    expect(chips).toEqual(['Apache-2.0 15', 'MIT 7']);
  });

  it('이름 · 버전 · 라이선스 · 쓰임을 한 줄에 적는다', () => {
    const box = document.createElement('div');

    renderOss(box, payload([item({ name: 'Univer', version: '0.25.1', what: '편집기 본체' })]));

    const text = box.textContent ?? '';
    expect(text).toContain('Univer');
    expect(text).toContain('0.25.1');
    expect(text).toContain('Apache-2.0');
    expect(text).toContain('편집기 본체');
  });

  it('버전이 없으면 상위가 정한다고 적는다', () => {
    // 빈 칸으로 두면 "못 읽어 왔다" 로 보인다.
    const box = document.createElement('div');

    renderOss(box, payload([item({ version: null })]));

    expect(box.textContent).toContain('상위 지정');
  });

  it('오픈소스가 아닌 것은 따로 모은다', () => {
    const box = document.createElement('div');

    renderOss(box, payload([
      item({ name: '열린것' }),
      item({ name: '상용것', kind: 'COMMERCIAL' }),
      item({ name: '사내것', kind: 'INTERNAL' }),
    ]));

    const closed = box.querySelector('[data-group="CLOSED"]');
    expect(closed?.textContent).toContain('상용것');
    expect(closed?.textContent).toContain('사내것');
    expect(closed?.textContent).not.toContain('열린것');
  });

  it('개발 전용은 배포물과 섞지 않는다', () => {
    const box = document.createElement('div');

    renderOss(box, payload([
      item({ name: '나가는것' }),
      item({ name: '시험도구', dist: 'DEV_ONLY' }),
    ]));

    expect(box.querySelector('[data-group="SHIPPED"]')?.textContent).toContain('나가는것');
    expect(box.querySelector('[data-group="DEV_ONLY"]')?.textContent).toContain('시험도구');
  });

  it('빈 묶음은 아예 안 그린다', () => {
    const box = document.createElement('div');

    renderOss(box, payload([item()]));

    expect(box.querySelector('[data-group="DEV_ONLY"]')).toBeNull();
  });

  it('주소는 새 창으로 열고 안전하게 연다', () => {
    const box = document.createElement('div');

    renderOss(box, payload([item({ url: 'https://univer.ai' })]));

    const link = box.querySelector('a[href="https://univer.ai"]') as HTMLAnchorElement;
    expect(link.target).toBe('_blank');
    expect(link.rel).toContain('noopener');
  });

  it('주소가 없으면 링크를 만들지 않는다', () => {
    const box = document.createElement('div');

    renderOss(box, payload([item({ url: '', kind: 'COMMERCIAL' })]));

    expect(box.querySelector('[data-group="CLOSED"] a')).toBeNull();
  });

  it('이름을 글로만 넣는다', () => {
    // 목록은 우리가 적은 것이지만, 관리자 화면에서 문자열을 HTML 로 넣는 습관을
    // 두면 다음 사람이 남이 준 값에도 그렇게 한다.
    const box = document.createElement('div');

    renderOss(box, payload([item({ name: '<img src=x onerror=alert(1)>' })]));

    expect(box.querySelector('img')).toBeNull();
  });

  it('좌표 수를 적어 묶은 패키지를 알려 준다', () => {
    const box = document.createElement('div');

    renderOss(box, payload([item({ coords: ['npm:a', 'npm:b', 'npm:c'] })]));

    expect(box.textContent).toContain('패키지 3개');
  });

  it('좌표가 하나면 수를 안 적는다', () => {
    const box = document.createElement('div');

    renderOss(box, payload([item({ coords: ['npm:a'] })]));

    expect(box.textContent).not.toContain('패키지 1개');
  });

  it('다시 그리면 앞서 그린 것을 지운다', () => {
    const box = document.createElement('div');

    renderOss(box, payload([item({ name: '처음' })]));
    renderOss(box, payload([item({ name: '다음' })]));

    expect(box.textContent).not.toContain('처음');
    expect(box.textContent).toContain('다음');
  });

  it('고지문 단추를 누르면 받아 온 글을 보여 준다', async () => {
    const box = document.createElement('div');
    const onNotice = vi.fn().mockResolvedValue('KCC Web Office\n\n- Univer 0.25.1');

    renderOss(box, { ...payload([item()]), onNotice });
    const button = box.querySelector('button') as HTMLButtonElement;
    button.click();
    await Promise.resolve();
    await Promise.resolve();

    expect(onNotice).toHaveBeenCalled();
    expect(box.querySelector('pre')?.textContent).toContain('Univer 0.25.1');
  });
});
