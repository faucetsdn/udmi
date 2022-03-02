import { Injectable } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { GET_DEVICES } from './device.gql';
import { shareReplay } from 'rxjs/operators';

@Injectable({
  providedIn: 'root',
})
export class DevicesService {
  constructor(private apollo: Apollo) {}

  getDevices() {
    return this.apollo
      .watchQuery({
        query: GET_DEVICES,
        variables: {
          searchOptions: {
            batchSize: 10, //TODO:: bind to input from user
            offset: 0, //TODO:: same ^^
          },
        },
      })
      .valueChanges.pipe(shareReplay(1));
  }
}
