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
    database: process.env.DATABASE,
    projectId: process.env.PROJECT_ID,
    logLevel: process.env.LOG_LEVEL,
  };
}

export function logConfig(): void {
  const config = loadConfig();
  logger.info(`Running the API service with the following configuration:`);
  logger.info(`  environment: ${config.nodeEnv}`);
  logger.info(`     Database: ${config.database}`);
  logger.info(`   Project ID: ${config.projectId}`);
  logger.info(`    Log Level: ${config.logLevel}`);
}
