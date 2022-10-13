import { getAggregate } from '../../mongo/MongoAggregateBuilder';

describe('MongoAggregateBuilder.getAggregate', () => {
  test('return a propery built aggregate', () => {
    expect(
      getAggregate('name', { limit: 8, filter: JSON.stringify([{ field: 'site', operator: '=', value: 'site-1' }]) })
    ).toEqual([
      {
        $match: {
          name: { $in: [/(?:)/i] },
          $and: [
            {
              site: { $in: ['site-1'] },
            },
          ],
        },
      },
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
