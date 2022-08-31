import { DAO } from '../dao/DAO';

export const upsertMock = jest.fn();
export const getMock = jest.fn();
export const insertMock = jest.fn();

export const mockDAO: DAO<any> = { upsert: upsertMock, get: getMock, insert: insertMock };
