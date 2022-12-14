import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { DeviceModel } from '../device/device';
import { DevicesComponent } from './devices.component';

const routes: Routes = [
  {
    path: '',
    component: DevicesComponent,
    data: {
      searchFields: <Record<string, string>>{
        name: 'getDeviceNames',
        make: 'getDeviceMakes',
        model: 'getDeviceModels',
        site: 'getDeviceSites',
        section: 'getDeviceSections',
      },
      displayedColumns: <(keyof DeviceModel)[]>[
        'name',
        'make',
        'model',
        'site',
        'section',
        'lastPayload',
        'operational',
        'errorsCount',
      ],
    },
  },
  {
    path: '',
    component: DevicesComponent,
    outlet: 'siteTabs',
    data: {
      searchFields: <Record<string, string>>{
        name: 'getDeviceNames',
      },
      displayedColumns: <(keyof DeviceModel)[]>['name', 'message', 'details', 'level', 'state', 'errorsCount'],
    },
  },
  { path: ':deviceId', loadChildren: () => import('../device/device.module').then((m) => m.DeviceModule) },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class DevicesRoutingModule {}
