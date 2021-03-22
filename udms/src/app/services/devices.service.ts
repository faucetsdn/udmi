import { Injectable } from '@angular/core';
import { AngularFirestore, QueryFn } from '@angular/fire/firestore';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { Origin } from './origins.service';

export interface Device {
  mac: string;
  ip: string;
  port: number;
  vlan: number;
  role: string;
}

export interface DeviceSearch {
  mac?: string;
  ip?: string;
  port?: number;
  vlan?: number;
  role?: string;
}

@Injectable({
  providedIn: 'root'
})
export class DevicesService {

  constructor(private firestore: AngularFirestore) { }

  private mapDevice(device: any): Device {
    return {
      mac: device.attributes.mac,
      ip: device.attributes.ip,
      port: device.attributes.port,
      vlan: device.attributes.vlan,
      role: device.attributes.role
    };
  }

  public get(origin: Origin, count: number, search?: DeviceSearch): Observable<Device[]> {
    const queryFn: QueryFn = ref => {
      let query = ref.limit(count);
      if (search && Object.keys(search).length) {
        Object.keys(search).forEach((key) => {
          const value = search[key as keyof DeviceSearch];
          query = query.where(key, '==', value);
        });
      }
      return query;
    };

    return this.firestore.collection('origin').doc(origin.id)
      .collection('devices', queryFn)
      .valueChanges().pipe(
        map((devices) => devices.map(this.mapDevice)));
  }
}
