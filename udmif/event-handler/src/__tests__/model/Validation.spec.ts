import { Error, Status } from '../../udmi/UdmiEvent';
import { Validation, ValidationBuilder } from '../../model/Validation';

describe('Validation.ValidationBuilder', () => {
  const TIMESTAMP_ERROR: string = 'Validation timestamp can not be empty';
  const VERSION_ERROR: string = 'Validation version can not be empty';

  let builder: ValidationBuilder;
  beforeEach(() => {
    builder = new ValidationBuilder();
  });

  test.each([undefined, null])('throws exception when timestamp is not provided', (timestamp: string) => {
    expect(() => {
      builder.timestamp(timestamp);
    }).toThrow(TIMESTAMP_ERROR);
  });

  test.each([undefined, null])('throws exception when version is not provided', (version: string) => {
    expect(() => {
      builder.version(version);
    }).toThrow(VERSION_ERROR);
  });

  test('build returns a Validation object', () => {
    const status: Status = {
      message: 'some-message',
      category: 'some-category',
      level: 500,
      timestamp: '2022-08-03T17:28:49Z',
      detail: 'some-detail',
    };

    const errors: Error[] = [
      {
        message: 'While converting to json node: 2 schema violations found',
        level: 500,
        category: 'category-x',
      },
    ];

    const output: Validation = builder
      .timestamp('2022-08-03T17:28:49Z')
      .version('1.0')
      .status(status)
      .category('some-category')
      .message('some-message')
      .detail('some-detail')
      .errors(errors)
      .build();

    const expectedValidation: Validation = {
      timestamp: '2022-08-03T17:28:49Z',
      last_updated: null,
      version: '1.0',
      status,
      category: 'some-category',
      message: 'some-message',
      detail: 'some-detail',
      errors,
    };

    expect(output).toEqual(expectedValidation);
  });
});
