import { NgModule } from '@angular/core';
import { GoogleLoginProvider, SocialAuthServiceConfig, SocialLoginModule } from '@abacritt/angularx-social-login';
import { EnvService } from '../env/env.service';

export function SocialAuthFactory(env: EnvService) {
  const config: SocialAuthServiceConfig = {
    autoLogin: true,
    providers: [
      {
        id: GoogleLoginProvider.PROVIDER_ID,
        provider: new GoogleLoginProvider(env.googleClientId),
      },
    ],
  };

  return config;
}

@NgModule({
  imports: [SocialLoginModule],
  providers: [
    {
      provide: 'SocialAuthServiceConfig',
      useFactory: SocialAuthFactory,
      deps: [EnvService],
    },
  ],
})
export class AuthModule {}
