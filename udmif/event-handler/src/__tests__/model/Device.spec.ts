import { Point } from '../../model/Point';
import { DeviceBuilder } from '../../model/Device';
import { Validation } from '../../model/Validation';

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
const validation: Validation = {
  category: 'category-x',
  message: 'Multiple validation errors',
  timestamp: '2022-08-03T17:28:49Z',
  detail:
    'While converting to json node: 2 schema violations found; While converting to json node: 1 schema violations found',
  errors: [
    {
      message: 'While converting to json node: 2 schema violations found',
      level: 500,
      category: 'category-x',
    },
    {
      message: 'While converting to json node: 1 schema violations found',
      level: 500,
      category: 'category-x',
    },
  ],
};

describe('Device.DeviceBuilder', () => {
  let builder: DeviceBuilder;
  beforeEach(() => {
    builder = new DeviceBuilder();
  });

  test('throws exception when site is not specified', () => {
    expect(() => {
      builder.build();
    }).toThrow('Device site can not be empty');
  });

  test('throws exception when name is not specified', () => {
    builder.site(site);
    expect(() => {
      builder.build();
    }).toThrow('Device name can not be empty');
  });

  test('Builder creates Device Document with id and name', () => {
    builder.site(site).name(name);
    expect(builder.build()).toEqual({ site, name, tags });
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
      .points(points)
      .validation(validation);
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
      validation,
      tags,
    });
  });
});
