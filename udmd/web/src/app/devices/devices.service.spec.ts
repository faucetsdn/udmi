import { TestBed } from '@angular/core/testing';
import { ApolloTestingController, ApolloTestingModule, APOLLO_TESTING_CACHE } from 'apollo-angular/testing';
import { GraphQLModule } from '../graphql/graphql.module';
import { DevicesResponse, GET_DEVICES } from './device.gql';
import { DevicesService } from './devices.service';
import { addTypenameToDocument } from '@apollo/client/utilities';

describe('DevicesService', () => {
  let service: DevicesService;
  let controller: ApolloTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ApolloTestingModule, GraphQLModule],
      providers: [
        {
          provide: APOLLO_TESTING_CACHE,
          useValue: { addTypename: true },
        },
      ],
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

  it('should return the devices', (done) => {
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
      done();
    });

    // The following `expectOne()` will match the operation's document.
    // If no requests or multiple requests matched that document
    // `expectOne()` would throw.
    const op = controller.expectOne(addTypenameToDocument(GET_DEVICES));

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
