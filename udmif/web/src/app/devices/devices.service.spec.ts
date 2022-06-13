import { TestBed } from '@angular/core/testing';
import { ApolloTestingController, ApolloTestingModule } from 'apollo-angular/testing';
import { GET_DEVICES } from './devices.gql';
import { DevicesQueryResponse } from './devices';
import { DevicesService } from './devices.service';

describe('DevicesService', () => {
  let service: DevicesService;
  let controller: ApolloTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ApolloTestingModule],
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
    const mockDevicesResponse: DevicesQueryResponse = {
      devices: {
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
            serialNumber: 's123',
            firmware: 'V3',
            tags: [],
          },
        ],
        totalCount: 1,
        totalFilteredCount: 1,
      },
    };

    // Make some assertion about the result for once it's fulfilled.
    service.getDevices().valueChanges.subscribe(({ data }) => {
      expect(data).toEqual(mockDevicesResponse);
    });

    // The following `expectOne()` will match the operation's document.
    // If no requests or multiple requests matched that document
    // `expectOne()` would throw.
    const op = controller.expectOne(GET_DEVICES);

    // Assert the correct search options were sent.
    expect(op.operation.variables).toEqual({
      searchOptions: {
        offset: undefined,
        batchSize: 10,
        sortOptions: undefined,
        filter: undefined,
      },
    });

    // Respond with mock data, causing Observable to resolve.
    op.flush({
      data: mockDevicesResponse,
    });
  });
});
