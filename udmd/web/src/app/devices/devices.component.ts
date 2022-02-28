import { Component, OnInit } from '@angular/core';
import { Apollo, gql } from 'apollo-angular';

const GET_DEVICES = gql`
  query GetDevices {
    devices {
      id
      name
    }
  }
`;

@Component({
  templateUrl: './devices.component.html',
  styleUrls: ['./devices.component.scss'],
})
export class DevicesComponent implements OnInit {
  displayedColumns: string[] = ['position', 'name'];
  loading: boolean = true;
  devices: any[] = []; // TODO:: replace with proper model

  constructor(private apollo: Apollo) {}

  ngOnInit() {
    this.apollo
      .watchQuery<any>({
        query: GET_DEVICES,
      })
      .valueChanges.subscribe(({ data, loading }) => {
        this.loading = loading;
        this.devices = data.devices;
      });
  }
}
