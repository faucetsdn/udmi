import * as fs from 'fs';
import { gql } from 'apollo-server';
import { DocumentNode } from 'graphql';

// read schema .graphql file into a string
const readSchema = (path: string, file: string): string => {
  return fs.readFileSync(`${path}/${file}`, 'utf8').toString();
};

const deviceSchema: string = readSchema('./src/device', 'schema.graphql');
const siteSchema: string = readSchema('./src/site', 'schema.graphql');

// build up master schema from individual schema files
export const typeDefs: DocumentNode = gql(deviceSchema + siteSchema);
