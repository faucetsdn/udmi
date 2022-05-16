import { getAggregate } from '../../../../device/dao/mongodb/MongoAggregateBuilder';

describe('MongoAggregateBuilder.getAggregate', () => {
  test('return a propery built aggregate', () => {
    expect(getAggregate('name', 8)).toEqual([
      { $match: { name: { $in: [/(?:)/i] } } },
      { $group: { _id: '$name', distinct_doc: { $first: '$$ROOT' } } },
      {
        $replaceRoot: {
          newRoot: '$distinct_doc',
        },
      },
      { $limit: 8 },
      { $sort: { name: 1 } },
    ]);
  });
});
