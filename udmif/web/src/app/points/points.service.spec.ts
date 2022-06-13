import { TestBed } from '@angular/core/testing';
import { ApolloTestingController, ApolloTestingModule } from 'apollo-angular/testing';
import { GET_POINTS } from './points.gql';
import { PointsQueryResponse } from './points';
import { PointsService } from './points.service';

describe('PointsService', () => {
  let service: PointsService;
  let controller: ApolloTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ApolloTestingModule],
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

  it('should return the points', () => {
    const mockPointsResponse: PointsQueryResponse = {
      points: [
        {
          id: '123',
          name: 'Zone Temperature',
          value: '74.93932',
          units: '°C',
          state: '',
        },
      ],
    };

    // Make some assertion about the result for once it's fulfilled.
    service.getPoints('device-id-123').subscribe(({ data }) => {
      expect(data).toEqual(mockPointsResponse);
    });

    // The following `expectOne()` will match the operation's document.
    // If no requests or multiple requests matched that document
    // `expectOne()` would throw.
    const op = controller.expectOne(GET_POINTS);

    // Assert the correct search options were sent.
    expect(op.operation.variables).toEqual({
      deviceId: 'device-id-123',
    });

    // Respond with mock data, causing Observable to resolve.
    op.flush({
      data: mockPointsResponse,
    });
  });
});
