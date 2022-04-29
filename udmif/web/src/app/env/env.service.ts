import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class EnvService {
  // The values that are defined here are the default values that can
  // be overridden by env.js.

  // Google client id
  public googleClientId = '';

  // Api
  public apiUri = '/api';

  constructor() {}
}
