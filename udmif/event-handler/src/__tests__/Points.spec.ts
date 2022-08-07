import { PointBuilder } from '../Point';

const id: string = 'some-id';
const name: string = 'some-name';
const state: string = 'some-state';
const units: string = 'units';
const value: string = 'value';
const code: string = '';

describe('Points.PointBuilder', () => {
  let builder: PointBuilder;
  beforeEach(() => {
    builder = new PointBuilder();
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
    expect(builder.build()).toEqual({ id, name, state: '' });
  });
  test('Builder allows optional attributes', () => {
    builder.id(id).name(name).state(state).units(units).value(value);
    expect(builder.build()).toEqual({ id, name, state, units, value });
  });
  test('Builder allows optional meta attributes', () => {
    builder.id(id).name('filter_alarm_pressure_status').units('Degrees-Celsius').metaCode('filter_alarm_pressure_status').metaUnit('Degrees-Celsius');
    expect(builder.build()).toEqual({
      id,
      name: 'filter_alarm_pressure_status',
      units: 'Degrees-Celsius',
      meta: { code: 'filter_alarm_pressure_status', units: 'Degrees-Celsius' },
      state: ''
    });
  });
});
