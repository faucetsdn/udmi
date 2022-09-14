import { ParsePipe } from './parse.pipe';

describe('ParsePipe', () => {
  const pipe = new ParsePipe();

  const testCases = [
    { value: null, expected: undefined },
    { value: undefined, expected: undefined },
    { value: '', expected: undefined },
    { value: '{}', expected: {} },
    { value: '{"id":"123"}', expected: { id: '123' } },
  ];

  testCases.forEach((test) => {
    it(`should transform ${test.value} to ${test.expected}`, () => {
      expect(pipe.transform(test.value)).toEqual(test.expected);
    });
  });
});
