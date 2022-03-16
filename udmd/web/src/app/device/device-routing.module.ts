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
    ],
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
})
export class DeviceRoutingModule {}
