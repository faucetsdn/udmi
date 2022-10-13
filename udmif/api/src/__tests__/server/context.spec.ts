import { ContextProcessor, getDefaultContextProcessor } from '../../server/context';
import * as googleAuth from '../../server/google';

describe('context.getDefaultContextProcessor', () => {
  test('returns a default context processor', async () => {
    await expect(getDefaultContextProcessor([])).resolves.not.toBeNull();
  });
});

describe('ContextProcessor.processRequest', () => {
  const contextProcessor: ContextProcessor = new ContextProcessor([]);
  beforeEach(() => {
    jest.resetAllMocks();
    const mockAuthenticateIdToken = jest.fn();
    jest.spyOn(googleAuth, 'authenticateIdToken').mockImplementation(mockAuthenticateIdToken);
  });

  test('calling with req that has idtoken in header returns a not null context', async () => {
    const req = { headers: { idtoken: 'some-id-token' } };
    await expect(contextProcessor.processRequest(<any>{ req, res: {} })).resolves.not.toBeNull();
  });

  test('calling with req that has no headers throws exception', async () => {
    const req = {};
    await expect(contextProcessor.processRequest(<any>{ req, res: {} })).rejects.toThrowError('Invalid Headers');
  });
});
