import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { DeviceComponent } from './device.component';

const routes: Routes = [
  {
    path: '',
    component: DeviceComponent,
    children: [
      { path: '', redirectTo: 'points', pathMatch: 'full' },
      { path: 'points', loadChildren: () => import('../points/points.module').then((m) => m.PointsModule) },
      {
        path: 'errors',
        loadChildren: () => import('../device-errors/device-errors.module').then((m) => m.DeviceErrorsModule),
      },
    ],
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class DeviceRoutingModule {}
