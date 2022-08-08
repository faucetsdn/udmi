import { GoogleLoginProvider, SocialAuthService, SocialUser } from '@abacritt/angularx-social-login';
import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { Apollo } from 'apollo-angular';
import { BehaviorSubject } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  user$ = new BehaviorSubject<SocialUser | null>(null);
  isLoggedIn$ = new BehaviorSubject<boolean | null>(null);

  constructor(private socialAuthService: SocialAuthService, private router: Router, private apollo: Apollo) {
    this.socialAuthService.authState.subscribe((user) => {
      this.user$.next(user);
      this.isLoggedIn$.next(user !== null);
    });
  }

  async loginWithGoogle(): Promise<void> {
    await this.socialAuthService.signIn(GoogleLoginProvider.PROVIDER_ID); // TODO:: try option {ux_mode: 'redirect'}
    this.router.navigateByUrl('/devices');
  }

  async logout(): Promise<void> {
    await this.socialAuthService.signOut();
    this.router.navigateByUrl('/login');
    this.apollo.client.clearStore(); // clear apollo cache
  }
}
