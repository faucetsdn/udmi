import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { DeviceErrorsComponent } from './device-errors.component';

const routes: Routes = [{ path: '', component: DeviceErrorsComponent, outlet: 'deviceTabs' }];

@NgModule({
  imports: [RouterModule.forChild(routes)],
})
export class DeviceErrorsRoutingModule {}
