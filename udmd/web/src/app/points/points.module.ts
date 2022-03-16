import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PointsComponent } from './points.component';
import { PointsRoutingModule } from './points-routing.module';

@NgModule({
  declarations: [PointsComponent],
  imports: [PointsRoutingModule, CommonModule],
})
export class PointsModule {}
