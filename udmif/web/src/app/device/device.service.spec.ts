import { TestBed } from '@angular/core/testing';
import { ApolloTestingController, ApolloTestingModule } from 'apollo-angular/testing';
import { GET_DEVICE } from './device.gql';
import { DeviceQueryResponse } from './device';
import { DeviceService } from './device.service';

describe('DeviceService', () => {
  let service: DeviceService;
  let controller: ApolloTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ApolloTestingModule],
    });
    service = TestBed.inject(DeviceService);
    controller = TestBed.inject(ApolloTestingController);
  });

  afterEach(() => {
    controller.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should return the device', () => {
    const mockDeviceResponse: DeviceQueryResponse = {
      device: {
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
        level: 400,
        state: 'CORRECT',
        errorsCount: 2,
        validation: '',
        lastStateUpdated: '2022-01-03',
        lastStateSaved: '2022-01-03',
        lastTelemetryUpdated: '2022-01-03',
        lastTelemetrySaved: '2022-01-03',
      },
    };

    // Make some assertion about the result for once it's fulfilled.
    service.getDevice('123').subscribe(({ data }) => {
      expect(data).toEqual(mockDeviceResponse);
    });

    // The following `expectOne()` will match the operation's document.
    // If no requests or multiple requests matched that document
    // `expectOne()` would throw.
    const op = controller.expectOne(GET_DEVICE);

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
