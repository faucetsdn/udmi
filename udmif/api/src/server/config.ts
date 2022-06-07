import 'dotenv/config';

export interface Configuration {
  nodeEnv: string;
  datasource: string;
  projectId: string;
  logLevel: string;
  mongoProtocol: string;
  mongoUsername: string;
  mongoPassword: string;
  mongoHost: string;
  mongoDatabase: string;
  authClientId: string;
  clientIds: string[];
}

export function loadConfig(): Configuration {
  return {
    nodeEnv: process.env.NODE_ENV,
    datasource: process.env.DATASOURCE || 'STATIC',
    projectId: process.env.PROJECT_ID,
    logLevel: process.env.LOG_LEVEL,
    mongoProtocol: process.env.MONGO_PROTOCOL,
    mongoUsername: process.env.MONGO_USERNAME,
    mongoPassword: process.env.MONGO_PWD,
    mongoHost: process.env.MONGO_HOST,
    mongoDatabase: process.env.MONGO_DATABASE,
    authClientId: process.env.AUTH_CLIENT_ID,
    clientIds: process.env.CLIENT_IDS.split(','),
  };
}
