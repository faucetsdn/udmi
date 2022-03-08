import 'dotenv/config';
import { logger } from '../common/logger';

export interface Configuration {
  nodeEnv: string;
  database: string;
  projectId: string;
  logLevel: string;
}

export function loadConfig(): Configuration {
  return {
    nodeEnv: process.env.NODE_ENV,
    database: process.env.DATABASE || 'STATIC',
    projectId: process.env.PROJECT_ID,
    logLevel: process.env.LOG_LEVEL,
  };
}

export function logConfig(): void {
  const config = loadConfig();
  logger.info(`Running the API service with the following configuration:`);
  logger.info(`  Environment: ${config.nodeEnv}`);
  logger.info(`    Log Level: ${config.logLevel}`);
  logger.info(`   Project ID: ${config.projectId}`);
  logger.info(`   Datasource: ${config.database}`);
}
