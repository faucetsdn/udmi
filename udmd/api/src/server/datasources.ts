import { DataSources } from 'apollo-server-core/dist/graphqlOptions';

export default function dataSources(): () => DataSources<object> {
  return () => ({
  });
}
