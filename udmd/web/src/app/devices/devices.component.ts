import { Component, OnInit } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { GET_DEVICES } from './device.gql';
import { Device, SearchOptions } from './device.interface';
import { Observable, of } from 'rxjs';
import { shareReplay, pluck } from 'rxjs/operators';

@Component({
  templateUrl: './devices.component.html',
  styleUrls: ['./devices.component.scss'],
})
export class DevicesComponent implements OnInit {
  displayedColumns: string[] = ['name', 'make', 'model', 'site', 'section', 'lastPayload', 'operational', 'tags'];

  loading$: Observable<boolean> = of(false);
  devices$: Observable<Device[]> = of([]);
  totalCount$: Observable<number> = of(0);

  constructor(private apollo: Apollo) {}

  ngOnInit() {
    const source$ = this.getDevices();

    this.loading$ = source$.pipe(pluck('loading'));
    this.devices$ = source$.pipe(pluck('data', 'devices', 'devices'));
    this.totalCount$ = source$.pipe(pluck('data', 'devices', 'totalCount'));
  }

  getDevices() {
    return this.apollo
      .watchQuery<any>({
        query: GET_DEVICES,
        variables: {
          searchOptions: <SearchOptions>{
            batchSize: 10, //TODO:: bind to input from user
            offset: 0, //TODO:: same ^^
          },
        },
      })
      .valueChanges.pipe(shareReplay(1));
  }
}
