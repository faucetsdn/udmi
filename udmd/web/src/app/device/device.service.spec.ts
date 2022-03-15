import { TestBed } from '@angular/core/testing';
import { ApolloTestingController, ApolloTestingModule } from 'apollo-angular/testing';
import { GraphQLModule } from '../graphql/graphql.module';
import { GET_DEVICE } from './device.gql';
import { DeviceResponse } from './device';
import { DeviceService } from './device.service';

describe('DeviceService', () => {
  let service: DeviceService;
  let controller: ApolloTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ApolloTestingModule, GraphQLModule],
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

  xit('should return the device', (done) => {
    const mockDeviceResponse: DeviceResponse = {
      device: {
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
    };

    // Make some assertion about the result for once it's fulfilled.
    service.getDevice('123').subscribe(({ data }) => {
      expect(data).toEqual(mockDeviceResponse);
      done();
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
      data: {
        devices: mockDeviceResponse,
      },
    });
  });
});
