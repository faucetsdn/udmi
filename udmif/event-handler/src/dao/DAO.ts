export interface DAO<Type> {
  insert(document: Type): Promise<void>;
  upsert(filterQuery: any, updateQuery: any): Promise<void>;
  get(filterQuery: any): Promise<Type>;
}
