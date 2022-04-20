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

  loginWithGoogle(): void {
    this.socialAuthService.signIn(GoogleLoginProvider.PROVIDER_ID).then(() => {
      this.router.navigateByUrl('/devices');
    }); // {ux_mode: 'redirect'}
  }

  logout(): void {
    this.socialAuthService.signOut().then(() => {
      this.router.navigateByUrl('/login');
      this.apollo.client.clearStore(); // clear apollo cache
    });
  }
}
