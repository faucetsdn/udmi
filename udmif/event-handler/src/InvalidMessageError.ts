export class InvalidMessageError extends Error {
  constructor(msg: string) {
    super(msg);

    // Set the prototype explicitly.
    Object.setPrototypeOf(this, InvalidMessageError.prototype);
  }
}
