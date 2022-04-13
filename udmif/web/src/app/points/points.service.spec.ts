import { TestBed } from '@angular/core/testing';
import { ApolloTestingController, ApolloTestingModule } from 'apollo-angular/testing';
import { GraphQLModule } from '../graphql/graphql.module';
import { GET_POINTS } from './points.gql';
import { PointsQueryResponse } from './points';
import { PointsService } from './points.service';

describe('PointsService', () => {
  let service: PointsService;
  let controller: ApolloTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ApolloTestingModule, GraphQLModule],
    });
    service = TestBed.inject(PointsService);
    controller = TestBed.inject(ApolloTestingController);
  });

  afterEach(() => {
    controller.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  xit('should return the points', (done) => {
    const mockDeviceResponse: PointsQueryResponse = {
      points: [],
    };

    // Make some assertion about the result for once it's fulfilled.
    service.getPoints('123').subscribe(({ data }) => {
      expect(data).toEqual(mockDeviceResponse);
      done();
    });

    // The following `expectOne()` will match the operation's document.
    // If no requests or multiple requests matched that document
    // `expectOne()` would throw.
    const op = controller.expectOne(GET_POINTS);

    // Assert the correct search options were sent.
    expect(op.operation.variables).toEqual({
      id: '123',
    });

    // Respond with mock data, causing Observable to resolve.
    op.flush({
      data: mockDeviceResponse,
    });
  });
});
