import { Component } from '@angular/core';

@Component({
  selector: 'app-validation-status',
  inputs: ['validation'],
  templateUrl: './validation-status.component.html',
  styleUrls: ['./validation-status.component.scss'],
})
export class ValidationStatusComponent {
  validation?: string;
}
