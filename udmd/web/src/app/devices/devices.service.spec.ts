import { TestBed } from '@angular/core/testing';
import { ApolloTestingController, ApolloTestingModule } from 'apollo-angular/testing';
import { GraphQLModule } from '../graphql/graphql.module';
import { DevicesResponse, GET_DEVICES } from './device.gql';
import { DevicesService } from './devices.service';

describe('DevicesService', () => {
  let service: DevicesService;
  let controller: ApolloTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ApolloTestingModule, GraphQLModule],
    });
    service = TestBed.inject(DevicesService);
    controller = TestBed.inject(ApolloTestingController);
  });

  afterEach(() => {
    controller.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should return the devices', () => {
    const mockDevicesResponse: DevicesResponse = {
      devices: [
        {
          id: '123',
          name: 'device one',
          make: 'Mitr',
          model: 'MTR 1',
          site: 'ABC',
          section: 'ABC 3',
          lastPayload: '2022-01-03',
          operational: false,
          tags: [],
        },
      ],
      totalCount: 1,
    };

    // Make some assertion about the result for once it's fulfilled.
    service.getDevices().subscribe(({ data }) => {
      expect(data.devices.totalCount).toEqual(1);
    });

    // The following `expectOne()` will match the operation's document.
    // If no requests or multiple requests matched that document
    // `expectOne()` would throw.
    const op = controller.expectOne(GET_DEVICES);

    // Assert the correct search options were sent.
    expect(op.operation.variables).toEqual({
      searchOptions: {
        batchSize: 10,
        offset: 0,
      },
    });

    // Respond with mock data, causing Observable to resolve.
    op.flush({
      data: {
        devices: mockDevicesResponse,
      },
    });

    // Finally, assert that there are no outstanding operations.
    controller.verify();
  });
});
