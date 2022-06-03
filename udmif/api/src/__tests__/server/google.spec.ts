import { LoginTicket, TokenPayload, OAuth2Client } from 'google-auth-library';
import { authenticateIdToken } from '../../server/google';

describe('google auth tests', () => {
  const payload: TokenPayload = { iss: '', aud: 'aud1', iat: 1, exp: 1, sub: '' };
  const loginTicket = new LoginTicket('test', payload);

  beforeEach(() => {
    jest.restoreAllMocks();
    OAuth2Client.prototype.verifyIdToken = jest.fn().mockResolvedValue(loginTicket);
  });

  test('authenticateIdToken with an invalid id token throws exception', async () => {
    await expect(authenticateIdToken('', ['', ''])).rejects.toThrow('Error: An id token is expected');
  });
  test('authenticateIdToken with an invalid audience throws exception', async () => {
    await expect(authenticateIdToken('some-id-token', ['aud2', 'aud3'])).rejects.toThrow('Invalid client id');
  });
  test('authenticateIdToken with valid token returns', async () => {
    await expect(authenticateIdToken('some-id-token', ['aud1', 'aud3'])).resolves.toBeUndefined();
  });
});
