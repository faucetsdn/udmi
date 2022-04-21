import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PointsComponent } from './points.component';
import { PointsRoutingModule } from './points-routing.module';
import { PipesModule } from '../common/pipes/pipes.module';
import { MatTableModule } from '@angular/material/table';
import { MatSortModule } from '@angular/material/sort';

@NgModule({
  declarations: [PointsComponent],
  imports: [PointsRoutingModule, CommonModule, MatTableModule, MatSortModule, PipesModule],
})
export class PointsModule {}
