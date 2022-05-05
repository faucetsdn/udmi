import { TestBed } from '@angular/core/testing';
import { ApolloTestingController, ApolloTestingModule } from 'apollo-angular/testing';
import { GET_AUTOCOMPLETE_SUGGESTIONS } from './search-filter.gql';
import { AutocompleteSuggestionsQueryResponse } from './search-filter';
import { SearchFilterService } from './search-filter.service';

describe('SearchFilterService', () => {
  let service: SearchFilterService;
  let controller: ApolloTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ApolloTestingModule],
    });
    service = TestBed.inject(SearchFilterService);
    controller = TestBed.inject(ApolloTestingController);
  });

  afterEach(() => {
    controller.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  xit('should return the device', (done) => {
    const mockDeviceResponse: AutocompleteSuggestionsQueryResponse = {
      autocompleteSuggestions: ['City A'],
    };

    // Make some assertion about the result for once it's fulfilled.
    service.getAutocompleteSuggestions('device', 'site', 'ci', 10).subscribe(({ data }) => {
      expect(data).toEqual(mockDeviceResponse);
      done();
    });

    // The following `expectOne()` will match the operation's document.
    // If no requests or multiple requests matched that document
    // `expectOne()` would throw.
    const op = controller.expectOne(GET_AUTOCOMPLETE_SUGGESTIONS);

    // Assert the correct variables were sent.
    expect(op.operation.variables).toEqual({
      autocompleteOptions: {
        entity: 'device',
        field: 'site',
        term: 'ci',
        limit: 10,
      },
    });

    // Respond with mock data, causing Observable to resolve.
    op.flush({
      data: mockDeviceResponse,
    });
  });
});
