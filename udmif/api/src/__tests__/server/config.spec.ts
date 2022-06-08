// IMPORTANT import the mock after
import { loadConfig } from '../../server/config';

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
    process.env.LOG_LEVEL = 'info';
    expect(loadConfig().logLevel).toBe('info');
  });

  test('config has a mongo protocol entry', () => {
    process.env.MONGO_PROTOCOL = 'protocol';
    expect(loadConfig().mongoProtocol).toBe('protocol');
  });

  test('config has a mongo protocol entry', () => {
    process.env.MONGO_USER = 'name';
    expect(loadConfig().mongoUsername).toBe('name');
  });

  test('config has a mongo protocol entry', () => {
    process.env.MONGO_PWD = 'pwd';
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
