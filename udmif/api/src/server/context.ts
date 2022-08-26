import { Context, ContextFunction } from 'apollo-server-core';
import { ExpressContext } from 'apollo-server-express/dist/ApolloServer';
import { get } from 'lodash';
import { v4 as uuid } from 'uuid';
import { logger } from '../common/logger';
import { authenticateIdToken } from './google';

export async function getDefaultContextProcessor(
  clientIds: string[]
): Promise<ContextFunction<ExpressContext, Context> | Context> {
  // bind the public key endpoint to our context function so that it can be referenced on each call
  const contextProcessor = new ContextProcessor(clientIds);
  return contextProcessor.processRequest.bind(contextProcessor);
}

export class ContextProcessor {
  constructor(private clientIds: string[]) {}

  // This function is called on every incoming graphql request.
  public async processRequest({ req }: ExpressContext): Promise<Context<any>> {
    if (!req.headers) {
      throw new Error('Invalid Headers');
    }

    await authenticateIdToken(req.headers.idtoken.toString(), this.clientIds);

    // initialize the context
    const context: Context<any> = {};

    // store a correlation ID in the context
    const cid = get(req, 'headers.cid', uuid());
    context.cid = cid;

    // get IP address for the incoming request
    const ip: string = get(req, 'headers.x-forwarded-for', get(req, 'ip'));

    // log incoming request with the correlation ID and IP address context
    logger.debug({ cid, ip, inboundquery: get(req, 'body.query'), variables: get(req, 'body.variables') });

    // return updated context
    return context;
  }
}
