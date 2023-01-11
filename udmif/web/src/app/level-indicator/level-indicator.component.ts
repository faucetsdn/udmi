import { Component } from '@angular/core';

@Component({
  selector: 'app-level-indicator',
  inputs: ['level'],
  templateUrl: './level-indicator.component.html',
  styleUrls: ['./level-indicator.component.scss'],
})
export class LevelIndicatorComponent {
  level?: number;
}
