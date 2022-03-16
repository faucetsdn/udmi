import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { DevicesComponent } from './devices.component';

const routes: Routes = [
  { path: '', component: DevicesComponent },
  { path: ':id', loadChildren: () => import('../device/device.module').then((m) => m.DeviceModule) },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
})
export class DevicesRoutingModule {}
