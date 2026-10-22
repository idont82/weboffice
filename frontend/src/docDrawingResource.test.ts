import { describe, it, expect } from 'vitest';

import {
  DOC_DRAWING_RESOURCE, repairDrawingResource, withFreshResources,
} from './docDrawingResource';

function drawing(id: string) {
  return { unitId: 'wo-doc-1', subUnitId: 'wo-doc-1', drawingId: id, drawingType: 0,
    imageSourceType: 'URL', source: `/api/images/${id}` };
}

describe('withFreshResources', () => {
  it('모델에 남은 옛 리소스를 지금 것으로 갈아 끼운다', () => {
    // 모델이 들고 있는 resources 는 문서를 열 때 받은 그대로다. 그림을 넣어도
    // 안 바뀌어서, 그 빈 것을 저장하면 다음에 열 때 그림을 덮어 지운다
    // (2026-10-16 실측: 붙여넣은 그림이 다시 열면 사라졌다).
    const snapshot = {
      id: 'wo-doc-1',
      resources: [{ name: DOC_DRAWING_RESOURCE, data: '{"data":{},"order":[]}' }],
    };
    const now = [{ name: DOC_DRAWING_RESOURCE, data: '{"data":{"7":{}},"order":["7"]}' }];

    expect(withFreshResources(snapshot, now).resources).toEqual(now);
  });

  it('나머지는 건드리지 않는다', () => {
    const snapshot = { id: 'wo-doc-1', body: { dataStream: '\r\n' }, resources: [] };

    const got = withFreshResources(snapshot, []);

    expect(got.id).toBe('wo-doc-1');
    expect(got.body).toEqual({ dataStream: '\r\n' });
  });
});

describe('repairDrawingResource', () => {
  it('리소스가 비었으면 본문에 적힌 그림으로 채운다', () => {
    // 옛 문서를 되살리는 길이다. 그림은 snapshot.drawings 에 남아 있는데 리소스가
    // 비어 있어, 그대로 열면 Univer 가 빈 리소스를 믿고 그림을 지운다.
    const snapshot = {
      id: 'wo-doc-1',
      drawings: { 7: drawing('7') },
      drawingsOrder: ['7'],
      resources: [{ name: DOC_DRAWING_RESOURCE, data: '{"data":{},"order":[]}' }],
    };

    const got = repairDrawingResource(snapshot);
    const data = JSON.parse(got.resources.find((one) => one.name === DOC_DRAWING_RESOURCE)!.data);

    expect(Object.keys(data.data)).toEqual(['7']);
    expect(data.order).toEqual(['7']);
  });

  it('리소스가 없으면 하나 만들어 넣는다', () => {
    const snapshot = { id: 'wo-doc-1', drawings: { 7: drawing('7') }, drawingsOrder: ['7'] };

    const got = repairDrawingResource(snapshot);

    expect(got.resources).toHaveLength(1);
    expect(got.resources[0].name).toBe(DOC_DRAWING_RESOURCE);
  });

  it('리소스에 이미 그림이 있으면 손대지 않는다', () => {
    // 제대로 저장된 문서다. 본문 쪽 목록이 뒤처져 있어도 리소스가 참이다.
    const kept = '{"data":{"9":{}},"order":["9"]}';
    const snapshot = {
      id: 'wo-doc-1',
      drawings: { 7: drawing('7') },
      drawingsOrder: ['7'],
      resources: [{ name: DOC_DRAWING_RESOURCE, data: kept }],
    };

    const got = repairDrawingResource(snapshot);

    expect(got.resources[0].data).toBe(kept);
  });

  it('그림이 없으면 리소스를 만들지 않는다', () => {
    const snapshot = { id: 'wo-doc-1', body: { dataStream: '\r\n' } };

    expect(repairDrawingResource(snapshot).resources).toEqual([]);
  });

  it('망가진 리소스 글자는 빈 것으로 본다', () => {
    const snapshot = {
      id: 'wo-doc-1',
      drawings: { 7: drawing('7') },
      drawingsOrder: ['7'],
      resources: [{ name: DOC_DRAWING_RESOURCE, data: '{' }],
    };

    const got = repairDrawingResource(snapshot);
    const data = JSON.parse(got.resources[0].data);

    expect(data.order).toEqual(['7']);
  });
});
