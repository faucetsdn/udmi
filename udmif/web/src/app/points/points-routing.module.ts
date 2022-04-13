import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { PointsComponent } from './points.component';

const routes: Routes = [{ path: '', component: PointsComponent, outlet: 'deviceTabs' }];

@NgModule({
  imports: [RouterModule.forChild(routes)],
})
export class PointsRoutingModule {}
