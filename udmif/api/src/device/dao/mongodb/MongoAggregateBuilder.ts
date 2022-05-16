export function getAggregate(field: string, limit: number, search?: string): any {
  return [
    { $match: { [field]: { $in: [new RegExp(search, 'i')] } } },
    { $group: { _id: `$${field}`, distinct_doc: { $first: '$$ROOT' } } },
    {
      $replaceRoot: {
        newRoot: '$distinct_doc',
      },
    },
    { $limit: limit },
    { $sort: { [field]: 1 } },
  ];
}
