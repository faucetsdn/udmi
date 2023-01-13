export interface DAO<Type> {
  insert(document: Type): Promise<void>;
  upsert(document: Type, filterQuery: any): Promise<void>;
  get(filterQuery: any): Promise<Type | null>;
}
