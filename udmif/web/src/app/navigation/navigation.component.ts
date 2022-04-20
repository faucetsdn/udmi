import { Component } from '@angular/core';
import { map, Observable, of, take } from 'rxjs';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-navigation',
  templateUrl: './navigation.component.html',
  styleUrls: ['./navigation.component.scss'],
})
export class NavigationComponent {
  displayName: Observable<string | undefined> = of();
  isLoggedIn: Observable<boolean | null> = of(null);

  constructor(private authService: AuthService) {
    this.isLoggedIn = this.authService.isLoggedIn$;
    this.displayName = this.authService.user$.pipe(
      map((user) => user?.name),
      take(1)
    );
  }

  logout(): void {
    this.authService.logout();
  }
}
