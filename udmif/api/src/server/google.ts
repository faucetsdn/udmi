import { logger } from '../common/logger';
import { AuthenticationError } from 'apollo-server-core';
import { LoginTicket, OAuth2Client, TokenPayload } from 'google-auth-library';
import { loadConfig } from './config';

export async function authenticateIdToken(idToken: string, audience: string[]): Promise<void> {
  try {
    if (!idToken) {
      throw new Error('An id token is expected');
    }
    logger.debug('Authenticating the id token: ' + idToken);
    const authClient: OAuth2Client = getAuthClient();
    const validatedToken: LoginTicket = await authClient.verifyIdToken({
      idToken,
      audience,
    });
    const payload: TokenPayload = validatedToken.getPayload();
    if (!audience.includes(payload.aud)) {
      throw new Error('Invalid client id');
    }
  } catch (e) {
    logger.error(e);
    throw new AuthenticationError(e);
  }
}

// we may need to cache/memoize this since this will be called a lot
function getAuthClient(): OAuth2Client {
  return new OAuth2Client(loadConfig().authClientId);
}
