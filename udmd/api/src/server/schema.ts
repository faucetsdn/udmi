import * as fs from 'fs';
import { gql } from 'apollo-server';
import { DocumentNode } from 'graphql';

// read schema .graphql file into a string
const readSchema = (file: string): string => {
  return fs.readFileSync(`./src/server/${file}`, 'utf8').toString();
};

// build up master schema from individual schema files
export const typeDefs: DocumentNode = gql(readSchema('schema.graphql'));
