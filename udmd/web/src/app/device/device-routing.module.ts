import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { DeviceComponent } from './device.component';

const routes: Routes = [{ path: '', component: DeviceComponent }];

@NgModule({
  imports: [RouterModule.forChild(routes)],
})
export class DeviceRoutingModule {}
