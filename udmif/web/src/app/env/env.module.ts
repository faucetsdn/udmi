import { NgModule } from '@angular/core';
import { assign } from 'lodash-es';
import { EnvService } from './env.service';

export function EnvFactory(): EnvService {
  const env = new EnvService();

  // Assign environment variables from browser window to env
  // In the current implementation, properties from env.js overwrite defaults from the EnvService.
  // If needed, a deep merge can be performed here to merge properties instead of overwriting them.
  assign(env, (<any>window).__env);

  return env;
}

@NgModule({
  providers: [
    {
      provide: EnvService,
      useFactory: EnvFactory,
      deps: [],
    },
  ],
})
export class EnvModule {}
