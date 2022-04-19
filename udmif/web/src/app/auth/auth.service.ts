import { GoogleLoginProvider, SocialAuthService, SocialUser } from '@abacritt/angularx-social-login';
import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  user!: SocialUser | null;
  public isLoggedin?: boolean;

  constructor(private socialAuthService: SocialAuthService) {
    this.socialAuthService.authState.subscribe((user) => {
      console.log(user);
      this.user = user;
      this.isLoggedin = user !== null;
    });
  }

  loginWithGoogle(): void {
    this.socialAuthService.signIn(GoogleLoginProvider.PROVIDER_ID); // {ux_mode: 'redirect'}
  }

  logout(): void {
    this.socialAuthService.signOut();
  }
}
