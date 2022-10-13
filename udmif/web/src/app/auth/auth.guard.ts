import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { map, Observable, shareReplay } from 'rxjs';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root',
})
export class AuthGuard implements CanActivate {
  constructor(private authService: AuthService, private router: Router) {}

  canActivate(_route: ActivatedRouteSnapshot, _state: RouterStateSnapshot): Observable<boolean | UrlTree> {
    return this.authService.isLoggedIn$.pipe(
      map((isLoggedIn) => {
        if (!isLoggedIn) {
          // The subscription gets ignored after initial route guard check, so
          // we need to manually navigate instead of return a UrlTree.
          this.router.navigateByUrl('/login');
          return false;
        } else {
          return true;
        }
      }),
      shareReplay(1) // share to trigger map to run again, thus triggering the navigateByUrl
    );
  }
}
