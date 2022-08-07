import { GoogleLoginProvider, SocialAuthService, SocialUser } from '@abacritt/angularx-social-login';
import { Injectable, Injector } from '@angular/core';
import { Router } from '@angular/router';
import { Apollo } from 'apollo-angular';
import { BehaviorSubject, Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  authState: Observable<SocialUser>;
  user$ = new BehaviorSubject<SocialUser | null>(null);
  isLoggedIn$ = new BehaviorSubject<boolean | null>(null);

  constructor(private socialAuth: SocialAuthService, private router: Router, private injector: Injector) {
    this.authState = this.socialAuth.authState;
    this.authState.subscribe((user) => {
      this.user$.next(user);
      this.isLoggedIn$.next(user !== null);
    });
  }

  async loginWithGoogle(): Promise<void> {
    try {
      await this.socialAuth.signIn(GoogleLoginProvider.PROVIDER_ID);
      this.router.navigateByUrl('/devices');
    } catch { }
  }

  async logout(): Promise<void> {
    try {
      await this.socialAuth.signOut();
      this.router.navigateByUrl('/login');
      // Because AuthService is used as a dep in the ApolloFactory,
      // and the AuthService needs to makes use of the Apollo service,
      // we need to use the injector.get pattern to avoid circular dep issue.
      this.injector.get<Apollo>(Apollo).client.clearStore();
    } catch { }
  }

  async refreshToken(): Promise<void> {
    try {
      await this.socialAuth.refreshAuthToken(GoogleLoginProvider.PROVIDER_ID);
    } catch {
      this.logout();
    }
  }
}
