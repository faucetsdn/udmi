import { Point } from '../Point';
import { DeviceDocumentBuilder } from '../DeviceDocument';

const id: string = 'some-id';
const name: string = 'some-name';
const firmware: string = 'firmware';
const lastPayload: string = 'lastPayload';
const make: string = 'make';
const model: string = 'model';
const operational: string = 'operational';
const section: string = 'section';
const site: string = 'site';
const serialNumber: string = 'serialNumber';
const points: Point[] = [];
const tags: string[] = [];

describe('DeviceDocument.DeviceDocumentBuilder', () => {
  let builder: DeviceDocumentBuilder;
  beforeEach(() => {
    builder = new DeviceDocumentBuilder();
  });

  test('throws exception when id is not specified', () => {
    expect(() => {
      builder.build();
    }).toThrow('Point id can not be empty');
  });
  test('throws exception when name is not specified', () => {
    builder.id(id);
    expect(() => {
      builder.build();
    }).toThrow('Point name can not be empty');
  });
  test('Builder creates Device Document with id and name', () => {
    builder.id(id).name(name);
    expect(builder.build()).toEqual({ id, name, tags, points });
  });
  test('Builder allows optional attributes', () => {
    builder
      .id(id)
      .name(name)
      .firmware(firmware)
      .lastPayload(lastPayload)
      .make(make)
      .model(model)
      .operational(operational)
      .section(section)
      .site(site)
      .serialNumber(serialNumber)
      .points(points);
    expect(builder.build()).toEqual({
      id,
      name,
      firmware,
      lastPayload,
      make,
      model,
      operational,
      section,
      site,
      serialNumber,
      points,
      tags
    });
  });
});
