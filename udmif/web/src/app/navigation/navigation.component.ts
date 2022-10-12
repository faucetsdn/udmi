import { Component } from '@angular/core';
import { map, Observable, of, take } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { NavigationService } from './navigation.service';

@Component({
  selector: 'app-navigation',
  templateUrl: './navigation.component.html',
  styleUrls: ['./navigation.component.scss'],
})
export class NavigationComponent {
  isLoggedIn$: Observable<boolean | null> = of(null);
  displayName$: Observable<string | undefined> = of();
  pageTitle$: Observable<string> = this.navigationService.title$;

  constructor(private authService: AuthService, private navigationService: NavigationService) {
    this.isLoggedIn$ = this.authService.isLoggedIn$;
    this.displayName$ = this.authService.user$.pipe(
      map((user) => user?.name),
      take(1)
    );
  }

  logout(): void {
    this.authService.logout();
  }
}
