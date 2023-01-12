import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LevelIndicatorComponent } from './level-indicator.component';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

@NgModule({
  declarations: [LevelIndicatorComponent],
  imports: [CommonModule, MatIconModule, MatTooltipModule],
  exports: [LevelIndicatorComponent],
})
export class LevelIndicatorModule {}
