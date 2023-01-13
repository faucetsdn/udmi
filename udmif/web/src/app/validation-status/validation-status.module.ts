import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ValidationStatusComponent } from './validation-status.component';
import { MatCardModule } from '@angular/material/card';
import { LevelIndicatorModule } from '../level-indicator/level-indicator.module';
import { PipesModule } from '../common/pipes/pipes.module';

@NgModule({
  declarations: [ValidationStatusComponent],
  imports: [CommonModule, MatCardModule, LevelIndicatorModule, PipesModule],
  exports: [ValidationStatusComponent],
})
export class ValidationStatusModule {}
