import { describe, expect, it } from 'vitest';

import { renderSlides, type SlideDeckView, type SlideView } from './slideView';

function deck(overrides: Partial<SlideDeckView> = {}): SlideDeckView {
  return {
    width: 960,
    height: 720,
    slides: [{
      slideNo: 0, texts: [], tables: [], pictures: [], notes: null,
    }],
    ...overrides,
  };
}

describe('renderSlides', () => {
  it('슬라이드마다 종이 한 장을 그린다', () => {
    const box = document.createElement('div');

    renderSlides(box, deck({
      slides: [
        { slideNo: 0, texts: [], tables: [], pictures: [], notes: null },
        { slideNo: 1, texts: [], tables: [], pictures: [], notes: null },
      ],
    }), 1);

    expect(box.querySelectorAll('.slide-page')).toHaveLength(2);
  });

  it('종이 크기를 원본 비율대로 잡는다', () => {
    const box = document.createElement('div');

    renderSlides(box, deck(), 0.5);

    const page = box.querySelector<HTMLElement>('.slide-page')!;
    expect(page.style.width).toBe('480px');
    expect(page.style.height).toBe('360px');
  });

  it('글상자를 원본 자리에 앉힌다', () => {
    // 흐름에 두면 자리가 무너진다. 발표 자료는 자리가 곧 뜻이다.
    const box = document.createElement('div');

    renderSlides(box, deck({
      slides: [{
        slideNo: 0,
        texts: [{ text: '제목', left: 96, top: 48, width: 400, height: 80,
          fontSize: 28, bold: true }],
        tables: [], pictures: [], notes: null,
      }],
    }), 0.5);

    const text = box.querySelector<HTMLElement>('.slide-text')!;
    expect(text.textContent).toBe('제목');
    expect(text.style.left).toBe('48px');
    expect(text.style.top).toBe('24px');
    // 글자도 함께 줄어야 원본과 같은 모습이다.
    expect(text.style.fontSize).toBe('14pt');
    expect(text.style.fontWeight).toBe('700');
  });

  it('표를 칸 글자 그대로 그린다', () => {
    const box = document.createElement('div');

    renderSlides(box, deck({
      slides: [{
        slideNo: 0, texts: [],
        tables: [{ cells: [[{ text: '항목' }, { text: '값' }],
          [{ text: '매출' }, { text: '100' }]],
          left: 0, top: 0, width: 200, height: 80 }],
        pictures: [], notes: null,
      }],
    }), 1);

    const cells = [...box.querySelectorAll('.slide-table td')].map((td) => td.textContent);
    expect(cells).toEqual(['항목', '값', '매출', '100']);
  });

  it('그림을 원본 자리에 앉힌다', () => {
    const box = document.createElement('div');

    renderSlides(box, deck({
      slides: [{
        slideNo: 0, texts: [], tables: [],
        pictures: [{ url: '/api/images/7', left: 10, top: 20, width: 100, height: 50 }],
        notes: null,
      }],
    }), 1);

    const image = box.querySelector<HTMLImageElement>('.slide-image')!;
    expect(image.getAttribute('src')).toBe('/api/images/7');
    expect(image.style.left).toBe('10px');
    expect(image.style.width).toBe('100px');
  });

  it('발표자 노트를 종이 아래에 적는다', () => {
    const box = document.createElement('div');

    renderSlides(box, deck({
      slides: [{ slideNo: 0, texts: [], tables: [], pictures: [], notes: '천천히' }],
    }), 1);

    expect(box.querySelector('.slide-notes')?.textContent).toContain('천천히');
  });

  it('노트가 없으면 자리도 두지 않는다', () => {
    const box = document.createElement('div');

    renderSlides(box, deck(), 1);

    expect(box.querySelector('.slide-notes')).toBeNull();
  });

  it('다시 그리면 앞서 그린 것을 지운다', () => {
    const box = document.createElement('div');

    renderSlides(box, deck(), 1);
    renderSlides(box, deck(), 1);

    expect(box.querySelectorAll('.slide-page')).toHaveLength(1);
  });
});

describe('색과 도형', () => {
  const oneSlide = (slide: Partial<SlideView>): SlideDeckView => ({
    width: 960, height: 540,
    slides: [{ slideNo: 0, texts: [], tables: [], pictures: [], notes: null, ...slide }],
  });

  it('바탕색을 종이에 칠한다', () => {
    // 발표 자료는 어두운 바탕이 흔하다. 흰 종이로 그리면 원본과 딴판이다.
    const box = document.createElement('div');
    renderSlides(box, oneSlide({ background: '#07182d' }), 1);

    const paper = box.querySelector<HTMLElement>('.slide-page')!;
    expect(paper.style.background).toBe('rgb(7, 24, 45)');
  });

  it('도형을 자리와 색 그대로 그린다', () => {
    const box = document.createElement('div');
    renderSlides(box, oneSlide({
      shapes: [{ left: 10, top: 20, width: 100, height: 50, fill: '#ff7a00',
        line: null, lineWidth: 0, shape: 'rect', rotation: 0 }],
    }), 1);

    const shape = box.querySelector<HTMLElement>('.slide-shape')!;
    expect(shape.style.left).toBe('10px');
    expect(shape.style.width).toBe('100px');
    expect(shape.style.background).toBe('rgb(255, 122, 0)');
    expect(shape.style.borderRadius).toBe('');
  });

  it('동그란 도형은 동그랗게 그린다', () => {
    const box = document.createElement('div');
    renderSlides(box, oneSlide({
      shapes: [{ left: 0, top: 0, width: 80, height: 80, fill: '#00e5ff',
        line: null, lineWidth: 0, shape: 'ellipse', rotation: 0 }],
    }), 1);

    expect(box.querySelector<HTMLElement>('.slide-shape')!.style.borderRadius).toBe('50%');
  });

  it('세모·마름모 같은 모양은 잘라서 그린다', () => {
    // 다 네모로 그리면 원본과 딴판이다(2026-08-21 지적).
    const box = document.createElement('div');
    renderSlides(box, oneSlide({
      shapes: [{ left: 0, top: 0, width: 80, height: 80, fill: '#f00',
        line: null, lineWidth: 0, shape: 'triangle', rotation: 0 }],
    }), 1);

    expect(box.querySelector<HTMLElement>('.slide-shape')!.style.clipPath)
      .toBe('polygon(50% 0%, 100% 100%, 0% 100%)');
  });

  it('둥근 네모는 모서리를 굴린다', () => {
    const box = document.createElement('div');
    renderSlides(box, oneSlide({
      shapes: [{ left: 0, top: 0, width: 80, height: 40, fill: '#f00',
        line: null, lineWidth: 0, shape: 'roundRect', rotation: 0 }],
    }), 1);

    // 값을 못 받으면 파워포인트 기본 16.7% 다. 짧은 변 40 → 7px
    expect(box.querySelector<HTMLElement>('.slide-shape')!.style.borderRadius).toBe('7px');
  });

  it('돌려 놓은 도형은 그만큼 돌린다', () => {
    const box = document.createElement('div');
    renderSlides(box, oneSlide({
      shapes: [{ left: 0, top: 0, width: 80, height: 40, fill: '#f00',
        line: null, lineWidth: 0, shape: 'rect', rotation: 45 }],
    }), 1);

    expect(box.querySelector<HTMLElement>('.slide-shape')!.style.transform)
      .toBe('rotate(45deg)');
  });

  it('모르는 모양은 네모로 둔다', () => {
    const box = document.createElement('div');
    renderSlides(box, oneSlide({
      shapes: [{ left: 0, top: 0, width: 80, height: 40, fill: '#f00',
        line: null, lineWidth: 0, shape: '알 수 없는 것', rotation: 0 }],
    }), 1);

    const shape = box.querySelector<HTMLElement>('.slide-shape')!;
    expect(shape.style.clipPath).toBe('');
    expect(shape.style.borderRadius).toBe('');
  });

  it('도형은 글자 밑에 깔린다', () => {
    // 도형이 글 위에 오면 글이 가려진다.
    const box = document.createElement('div');
    renderSlides(box, oneSlide({
      shapes: [{ left: 0, top: 0, width: 80, height: 80, fill: '#000000',
        line: null, lineWidth: 0, shape: 'rect', rotation: 0 }],
      texts: [{ text: '제목', left: 0, top: 0, width: 80, height: 20,
        fontSize: 20, bold: false }],
    }), 1);

    const kids = [...box.querySelectorAll('.slide-page > *')].map((e) => e.className);
    expect(kids.indexOf('slide-shape')).toBeLessThan(kids.indexOf('slide-text'));
  });

  it('글자 색을 따른다', () => {
    const box = document.createElement('div');
    renderSlides(box, oneSlide({
      texts: [{ text: '흰 글씨', left: 0, top: 0, width: 80, height: 20,
        fontSize: 20, bold: false, color: '#ffffff' }],
    }), 1);

    expect(box.querySelector<HTMLElement>('.slide-text')!.style.color)
      .toBe('rgb(255, 255, 255)');
  });

  it('배율을 도형에도 매긴다', () => {
    const box = document.createElement('div');
    renderSlides(box, oneSlide({
      shapes: [{ left: 10, top: 20, width: 100, height: 50, fill: '#123456',
        line: null, lineWidth: 0, shape: 'rect', rotation: 0 }],
    }), 0.5);

    const shape = box.querySelector<HTMLElement>('.slide-shape')!;
    expect(shape.style.left).toBe('5px');
    expect(shape.style.width).toBe('50px');
  });
});

describe('번지는 색', () => {
  const oneShape = (extra: object): SlideDeckView => ({
    width: 960, height: 540,
    slides: [{ slideNo: 0, texts: [], tables: [], pictures: [], notes: null,
      shapes: [{ left: 0, top: 0, width: 100, height: 50, fill: null, line: null,
        lineWidth: 0, shape: 'rect', rotation: 0, ...extra }] }],
  });

  it('두 색 이상이면 번지게 칠한다', () => {
    // 못 그리면 그 자리가 뻥 뚫린다(2026-08-21 지적).
    const box = document.createElement('div');
    renderSlides(box, oneShape({
      gradient: [{ color: '#ff0000', at: 0 }, { color: '#0000ff', at: 1 }],
      gradientAngle: 90,
    }), 1);

    expect(box.querySelector<HTMLElement>('.slide-shape')!.style.backgroundImage)
      .toBe('linear-gradient(180deg, rgb(255, 0, 0) 0%, rgb(0, 0, 255) 100%)');
  });

  it('파워포인트 각도를 화면 각도로 바꾼다', () => {
    // 파워포인트는 0 도가 왼쪽에서 오른쪽, CSS 는 0 도가 아래에서 위다.
    const box = document.createElement('div');
    renderSlides(box, oneShape({
      gradient: [{ color: '#000000', at: 0 }, { color: '#ffffff', at: 1 }],
      gradientAngle: 0,
    }), 1);

    expect(box.querySelector<HTMLElement>('.slide-shape')!.style.backgroundImage)
      .toContain('90deg');
  });

  it('색이 하나뿐이면 번지게 하지 않는다', () => {
    const box = document.createElement('div');
    renderSlides(box, oneShape({ fill: '#123456', gradient: [] }), 1);

    const shape = box.querySelector<HTMLElement>('.slide-shape')!;
    expect(shape.style.backgroundImage === '' || shape.style.backgroundImage === 'none')
      .toBe(true);
    expect(shape.style.background).toContain('rgb(18, 52, 86)');
  });
});

describe('그림으로 채운 도형', () => {
  it('깔아 둔 그림을 바탕으로 쓴다', () => {
    const box = document.createElement('div');
    renderSlides(box, {
      width: 960, height: 540,
      slides: [{ slideNo: 0, texts: [], tables: [], pictures: [], notes: null,
        shapes: [{ left: 0, top: 0, width: 100, height: 50, fill: null, line: null,
          lineWidth: 0, shape: 'rect', rotation: 0, image: '/api/images/7' }] }],
    }, 1);

    const shape = box.querySelector<HTMLElement>('.slide-shape')!;
    expect(shape.style.backgroundImage).toContain('/api/images/7');
    expect(shape.style.backgroundSize).toBe('cover');
  });
});

describe('표 꾸밈', () => {
  it('칸 바탕색·글자 색·정렬을 따른다', () => {
    const box = document.createElement('div');
    renderSlides(box, {
      width: 960, height: 540,
      slides: [{ slideNo: 0, texts: [], pictures: [], notes: null, shapes: [],
        tables: [{ left: 0, top: 0, width: 200, height: 40,
          cells: [[{ text: '구분', fill: '#1b365d', color: '#ffffff',
                     fontSize: 20, bold: true, font: 'Gulim' },
                   { text: '값', align: 'center' }]] }] }],
    }, 1);

    const cells = box.querySelectorAll<HTMLElement>('.slide-table td');
    expect(cells[0].style.background).toContain('rgb(27, 54, 93)');
    expect(cells[0].style.color).toBe('rgb(255, 255, 255)');
    expect(cells[0].style.fontSize).toBe('20pt');
    expect(cells[0].style.fontWeight).toBe('700');
    expect(cells[0].style.fontFamily).toContain('Gulim');
    expect(cells[1].style.textAlign).toBe('center');
  });

  it('둥근 네모는 적힌 비율로 굴린다', () => {
    const box = document.createElement('div');
    renderSlides(box, {
      width: 960, height: 540,
      slides: [{ slideNo: 0, texts: [], tables: [], pictures: [], notes: null,
        shapes: [{ left: 0, top: 0, width: 200, height: 100, fill: '#eee', line: null,
          lineWidth: 0, shape: 'roundRect', rotation: 0, corner: 0.05 }] }],
    }, 1);

    // 짧은 변 100 의 5% = 5px
    expect(box.querySelector<HTMLElement>('.slide-shape')!.style.borderRadius).toBe('5px');
  });
});

describe('한 상자 안의 줄', () => {
  it('줄마다 적힌 크기로 그린다', () => {
    const box = document.createElement('div');
    renderSlides(box, {
      width: 960, height: 540,
      slides: [{ slideNo: 0, tables: [], pictures: [], notes: null, shapes: [],
        texts: [{ text: '큰 줄' + String.fromCharCode(10) + '작은 줄', left: 0, top: 0, width: 300, height: 100,
          fontSize: 32, bold: false,
          lines: [{ text: '큰 줄', fontSize: 32, bold: true, color: '#ff0000' },
                  { text: '작은 줄', fontSize: 12, bold: false, color: null }] }] }],
    }, 1);

    const lines = box.querySelectorAll<HTMLElement>('.slide-text p');
    expect(lines).toHaveLength(2);
    expect(lines[0].textContent).toBe('큰 줄');
    expect(lines[0].style.fontSize).toBe('32pt');
    expect(lines[0].style.fontWeight).toBe('700');
    expect(lines[0].style.color).toBe('rgb(255, 0, 0)');
    expect(lines[1].style.fontSize).toBe('12pt');
  });

  it('줄 정보가 없으면 예전처럼 통째로 그린다', () => {
    const box = document.createElement('div');
    renderSlides(box, {
      width: 960, height: 540,
      slides: [{ slideNo: 0, tables: [], pictures: [], notes: null, shapes: [],
        texts: [{ text: '한 덩이', left: 0, top: 0, width: 300, height: 100,
          fontSize: 20, bold: false }] }],
    }, 1);

    const one = box.querySelector<HTMLElement>('.slide-text')!;
    expect(one.querySelectorAll('p')).toHaveLength(0);
    expect(one.textContent).toBe('한 덩이');
  });
});

describe('글꼴', () => {
  it('적힌 글꼴을 쓰되 없을 때를 대비한다', () => {
    const box = document.createElement('div');
    renderSlides(box, {
      width: 960, height: 540,
      slides: [{ slideNo: 0, tables: [], pictures: [], notes: null, shapes: [],
        texts: [{ text: '굴림 줄', left: 0, top: 0, width: 300, height: 40,
          fontSize: 14, bold: false, font: 'Gulim' }] }],
    }, 1);

    const one = box.querySelector<HTMLElement>('.slide-text')!;
    expect(one.style.fontFamily).toContain('Gulim');
    // 한글 글꼴이 사슬에 있어야 한글 기호(〮 같은)가 두부로 안 보인다.
    expect(one.style.fontFamily).toContain('Malgun Gothic');
    expect(one.style.fontFamily).toContain('sans-serif');
  });
});

describe('세로 맞춤', () => {
  it('가운데 맞춤이면 위아래로 고루 넘치게 그린다', () => {
    const box = document.createElement('div');
    renderSlides(box, {
      width: 960, height: 540,
      slides: [{ slideNo: 0, tables: [], pictures: [], notes: null, shapes: [],
        texts: [{ text: '가운데 줄', left: 0, top: 100, width: 300, height: 20,
          fontSize: 14, bold: false, anchor: 'center' }] }],
    }, 1);

    const one = box.querySelector<HTMLElement>('.slide-text')!;
    expect(one.style.display).toBe('flex');
    expect(one.style.flexDirection).toBe('column');
    expect(one.style.justifyContent).toBe('center');
  });
});

describe('글머리 기호', () => {
  it('기호를 붙이고 내어 쓴다', () => {
    const box = document.createElement('div');
    renderSlides(box, {
      width: 960, height: 540,
      slides: [{ slideNo: 0, tables: [], pictures: [], notes: null, shapes: [],
        texts: [{ text: '첫 항목', left: 0, top: 0, width: 300, height: 100,
          fontSize: 14, bold: false,
          lines: [{ text: '첫 항목', fontSize: 14, bold: false,
            bullet: '•', indent: 40, hanging: 40 }] }] }],
    }, 1);

    const line = box.querySelector<HTMLElement>('.slide-text p')!;
    expect(line.textContent).toBe('• 첫 항목');
    expect(line.style.paddingLeft).toBe('40px');
    expect(line.style.textIndent).toBe('-40px');
  });
});

describe('바탕 그림', () => {
  it('깔린 그림을 장 전체에 편다', () => {
    const box = document.createElement('div');
    renderSlides(box, {
      width: 960, height: 540,
      slides: [{ slideNo: 0, texts: [], tables: [], pictures: [], notes: null,
        shapes: [], backgroundImage: '/api/images/7' }],
    }, 1);

    const paper = box.querySelector<HTMLElement>('.slide-page')!;
    expect(paper.style.backgroundImage).toContain('/api/images/7');
    expect(paper.style.backgroundSize).toBe('100% 100%');
  });
});

describe('손으로 그린 도형', () => {
  it('꼭짓점대로 오려 낸다', () => {
    const box = document.createElement('div');
    renderSlides(box, {
      width: 960, height: 540,
      slides: [{ slideNo: 0, texts: [], tables: [], pictures: [], notes: null,
        shapes: [{ left: 0, top: 0, width: 400, height: 80, fill: '#164194',
          line: null, lineWidth: 0, shape: 'rect', rotation: 0,
          outline: [{ x: 0, y: 0 }, { x: 0.8, y: 0 }, { x: 1, y: 1 }, { x: 0, y: 1 }] }] }],
    }, 1);

    expect(box.querySelector<HTMLElement>('.slide-shape')!.style.clipPath)
      .toBe('polygon(0% 0%, 80% 0%, 100% 100%, 0% 100%)');
  });
});

describe('돌려 놓은 글상자', () => {
  it('적힌 각도만큼 글도 돌린다', () => {
    const box = document.createElement('div');
    renderSlides(box, {
      width: 960, height: 540,
      slides: [{ slideNo: 0, tables: [], pictures: [], notes: null, shapes: [],
        texts: [{ text: '비스듬한 글', left: 0, top: 0, width: 300, height: 40,
          fontSize: 14, bold: false, rotation: 4.5 }] }],
    }, 1);

    expect(box.querySelector<HTMLElement>('.slide-text')!.style.transform)
      .toBe('rotate(4.5deg)');
  });
});

describe('가로 맞춤', () => {
  it('가운데 맞춤을 상자와 줄에 준다', () => {
    const box = document.createElement('div');
    renderSlides(box, {
      width: 960, height: 540,
      slides: [{ slideNo: 0, tables: [], pictures: [], notes: null, shapes: [],
        texts: [{ text: '가운데로', left: 0, top: 0, width: 300, height: 40,
          fontSize: 14, bold: false, align: 'center',
          lines: [{ text: '가운데로', fontSize: 14, bold: false, align: 'center' },
                  { text: '오른쪽', fontSize: 14, bold: false, align: 'right' }] }] }],
    }, 1);

    const one = box.querySelector<HTMLElement>('.slide-text')!;
    expect(one.style.textAlign).toBe('center');
    const lines = one.querySelectorAll<HTMLElement>('p');
    expect(lines[1].style.textAlign).toBe('right');
  });
});

describe('쪽 번호와 여백', () => {
  it('첫 장 번호부터 센다', () => {
    const box = document.createElement('div');
    renderSlides(box, {
      width: 960, height: 540, firstSlideNo: 0,
      slides: [{ slideNo: 0, texts: [], tables: [], pictures: [], notes: null, shapes: [] },
               { slideNo: 1, texts: [], tables: [], pictures: [], notes: null, shapes: [] }],
    }, 1);

    const numbers = [...box.querySelectorAll('.slide-no')].map((one) => one.textContent);
    expect(numbers).toEqual(['0 / 2', '1 / 2']);
  });

  it('상자 안쪽 여백을 준다', () => {
    const box = document.createElement('div');
    renderSlides(box, {
      width: 960, height: 540,
      slides: [{ slideNo: 0, tables: [], pictures: [], notes: null, shapes: [],
        texts: [{ text: '여백', left: 0, top: 0, width: 300, height: 80,
          fontSize: 14, bold: false,
          padding: { left: 27, top: 13, right: 40, bottom: 7 } }] }],
    }, 1);

    const one = box.querySelector<HTMLElement>('.slide-text')!;
    expect(one.style.padding).toBe('13px 40px 7px 27px');
    expect(one.style.boxSizing).toBe('border-box');
  });
});

describe('맞닿은 도형 이음새', () => {
  it('맞닿은 두 도형이 같은 경계에서 만난다', () => {
    const box = document.createElement('div');
    renderSlides(box, {
      width: 960, height: 540,
      // 아래 띠는 0~300.4, 위 띠는 100.36~300.4 로 오른쪽 끝이 같다.
      slides: [{ slideNo: 0, texts: [], tables: [], pictures: [], notes: null,
        shapes: [
          { left: 0, top: 0, width: 300.4, height: 60, fill: '#213562',
            line: null, lineWidth: 0, shape: 'rect', rotation: 0 },
          { left: 100.36, top: 0, width: 200.04, height: 60, fill: '#f4f2f3',
            line: null, lineWidth: 0, shape: 'rect', rotation: 0 }] }],
    }, 1);

    const [under, over] = [...box.querySelectorAll<HTMLElement>('.slide-shape')];
    const rightOf = (one: HTMLElement) =>
      Number.parseFloat(one.style.left) + Number.parseFloat(one.style.width);
    expect(rightOf(over)).toBe(rightOf(under));
  });

  it('자리와 크기를 화면 픽셀에 맞춘다', () => {
    const box = document.createElement('div');
    renderSlides(box, {
      width: 960, height: 540,
      slides: [{ slideNo: 0, texts: [], tables: [], pictures: [], notes: null,
        shapes: [{ left: 100.36, top: 0.4, width: 200.04, height: 60.3, fill: '#fff',
          line: '#000', lineWidth: 1, shape: 'rect', rotation: 0 }] }],
    }, 1);

    const one = box.querySelector<HTMLElement>('.slide-shape')!;
    expect(one.style.left).toBe('100px');
    expect(one.style.width).toBe('200px');
    expect(one.style.height).toBe('61px');
  });
});
