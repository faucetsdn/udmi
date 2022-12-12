import { TestBed } from '@angular/core/testing';
import { ApolloTestingController, ApolloTestingModule } from 'apollo-angular/testing';
import { GET_SITE } from './site.gql';
import { SiteQueryResponse } from './site';
import { SiteService } from './site.service';

describe('SiteService', () => {
  let service: SiteService;
  let controller: ApolloTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ApolloTestingModule],
    });
    service = TestBed.inject(SiteService);
    controller = TestBed.inject(ApolloTestingController);
  });

  afterEach(() => {
    controller.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should return the site', () => {
    const mockSiteResponse: SiteQueryResponse = {
      site: {
        name: 'site one',
        seenDevicesCount: 0,
        totalDevicesCount: 0,
        correctDevicesCount: 0,
        correctDevicesPercent: 0,
        missingDevicesCount: 0,
        missingDevicesPercent: 0,
        errorDevicesCount: 0,
        errorDevicesPercent: 0,
        extraDevicesCount: 0,
        lastValidated: '2022-04-24T02:54:51Z',
        totalDeviceErrorsCount: 0,
        validation: '',
      },
    };

    // Make some assertion about the result for once it's fulfilled.
    service.getSite('123').subscribe(({ data }) => {
      expect(data).toEqual(mockSiteResponse);
    });

    // The following `expectOne()` will match the operation's document.
    // If no requests or multiple requests matched that document
    // `expectOne()` would throw.
    const op = controller.expectOne(GET_SITE);

    // Assert the correct search options were sent.
    expect(op.operation.variables).toEqual({
      name: '123',
    });

    // Respond with mock data, causing Observable to resolve.
    op.flush({
      data: mockSiteResponse,
    });
  });
});
