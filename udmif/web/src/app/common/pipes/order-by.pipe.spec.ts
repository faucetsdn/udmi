import { CapitalizePipe } from './capitalize.pipe';

describe('CapitalizePipe', () => {
  const pipe = new CapitalizePipe();

  const testCases = [
    { value: null, expected: '' },
    { value: undefined, expected: '' },
    { value: '', expected: '' },
    { value: 'errorCount', expected: 'Error Count' },
  ];

  testCases.forEach((test) => {
    it(`should transform ${test.value} to ${test.expected}`, () => {
      expect(pipe.transform(test.value)).toEqual(test.expected);
    });
  });
});
