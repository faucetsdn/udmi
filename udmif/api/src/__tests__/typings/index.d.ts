export {};
declare global {
  namespace jest {
    interface Matchers<R> {
      toBeDistinct(): R;
      toBeWithinRange(floor: number, ceiling: number): R;
    }
  }
}
