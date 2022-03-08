const logger = {
  debug: jest.fn(),
  info: jest.fn(),
};

// IMPORTANT First mock winston
jest.mock('winston', () => ({
  format: {
    colorize: jest.fn(),
    combine: jest.fn(),
    label: jest.fn(),
    timestamp: jest.fn(),
    printf: jest.fn(),
    splat: jest.fn(),
  },
  createLogger: jest.fn().mockReturnValue(logger),
  transports: {
    Console: jest.fn(),
  },
}));

// IMPORTANT import the mock after
import * as winston from 'winston';
import { loadConfig, logConfig } from '../../server/config';

describe('config.loadConfig', () => {
  test('loads the confiuration and returns an object that can be passed with all the values', () => {
    expect(loadConfig()).toBeTruthy();
  });

  test('config has a nodeEnv entry', () => {
    expect(loadConfig().nodeEnv).toBe('test');
  });

  test('config has a datasource entry', () => {
    process.env.DATASOURCE = 'STATIC';
    expect(loadConfig().datasource).toBe('STATIC');
  });

  test('config has a project_id entry', () => {
    process.env.PROJECT_ID = 'test';
    expect(loadConfig().projectId).toBe('test');
  });

  test('config has a log level entry', () => {
    process.env.LOG_LEVEL = 'debug';
    expect(loadConfig().logLevel).toBe('debug');
  });

  test('config has a mongo protocol entry', () => {
    process.env.MONGO_PROTOCOL = 'protocol';
    expect(loadConfig().mongoProtocol).toBe('protocol');
  });

  test('config has a mongo protocol entry', () => {
    process.env.MONGO_USERNAME = 'name';
    expect(loadConfig().mongoUsername).toBe('name');
  });

  test('config has a mongo protocol entry', () => {
    process.env.MONGO_PASSWORD = 'pwd';
    expect(loadConfig().mongoPassword).toBe('pwd');
  });

  test('config has a mongo protocol entry', () => {
    process.env.MONGO_HOST = 'host';
    expect(loadConfig().mongoHost).toBe('host');
  });

  test('config has a mongo protocol entry', () => {
    process.env.MONGO_DATABASE = 'db';
    expect(loadConfig().mongoDatabase).toBe('db');
  });
});

describe('config.logConfig', () => {
  let loggerMock: winston.Logger;
  const mockCreateLogger = jest.spyOn(winston, 'createLogger');
  loggerMock = mockCreateLogger.mock.instances[0];

  test('info logs are written', () => {
    process.env.PROJECT_ID = 'test';

    logConfig();
    expect(logger.info).toHaveBeenCalledTimes(5);
    expect(logger.info).toHaveBeenNthCalledWith(1, 'Running the API service with the following configuration:');
    expect(logger.info).toHaveBeenNthCalledWith(2, '  Environment: test');
    expect(logger.info).toHaveBeenNthCalledWith(3, '    Log Level: debug');
    expect(logger.info).toHaveBeenNthCalledWith(4, '   Project ID: test');
    expect(logger.info).toHaveBeenNthCalledWith(5, '   Datasource: STATIC');
  });
});
