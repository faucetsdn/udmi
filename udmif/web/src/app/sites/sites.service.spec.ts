import { TestBed } from '@angular/core/testing';
import { ApolloTestingController, ApolloTestingModule } from 'apollo-angular/testing';
import { GET_SITES } from './sites.gql';
import { SitesQueryResponse } from './sites';
import { SitesService } from './sites.service';

describe('SitesService', () => {
  let service: SitesService;
  let controller: ApolloTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ApolloTestingModule],
    });
    service = TestBed.inject(SitesService);
    controller = TestBed.inject(ApolloTestingController);
  });

  afterEach(() => {
    controller.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should return the sites', () => {
    const mockSitesResponse: SitesQueryResponse = {
      sites: {
        sites: [
          {
            name: 'site one',
            totalDevicesCount: 0,
            correctDevicesCount: 0,
            missingDevicesCount: 0,
            errorDevicesCount: 0,
            extraDevicesCount: 0,
            lastValidated: '2022-04-24T02:54:51Z',
            percentValidated: 0,
            totalDeviceErrorsCount: 0,
          },
        ],
        totalCount: 1,
        totalFilteredCount: 1,
      },
    };

    // Make some assertion about the result for once it's fulfilled.
    service.getSites().valueChanges.subscribe(({ data }) => {
      expect(data).toEqual(mockSitesResponse);
    });

    // The following `expectOne()` will match the operation's document.
    // If no requests or multiple requests matched that document
    // `expectOne()` would throw.
    const op = controller.expectOne(GET_SITES);

    // Assert the correct search options were sent.
    expect(op.operation.variables).toEqual({
      searchOptions: {
        offset: undefined,
        batchSize: undefined,
        sortOptions: undefined,
        filter: undefined,
      },
    });

    // Respond with mock data, causing Observable to resolve.
    op.flush({
      data: mockSitesResponse,
    });
  });
});
