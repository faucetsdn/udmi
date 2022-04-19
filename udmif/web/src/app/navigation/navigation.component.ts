import { Component } from '@angular/core';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-navigation',
  templateUrl: './navigation.component.html',
  styleUrls: ['./navigation.component.scss'],
})
export class NavigationComponent {
  constructor(private authService: AuthService) {}

  get displayName(): string | undefined {
    return this.authService.user?.name;
  }

  get isLoggedin(): boolean | undefined {
    return this.authService.isLoggedin;
  }

  logout(): void {
    this.authService.logout();
  }
}
