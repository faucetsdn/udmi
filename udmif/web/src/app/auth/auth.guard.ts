import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { map, Observable } from 'rxjs';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root',
})
export class AuthGuard implements CanActivate {
  constructor(private authService: AuthService, private router: Router) {}

  canActivate(_route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean | UrlTree> {
    return this.authService.isLoggedIn$.pipe(
      map((isLoggedIn) => {
        if (!isLoggedIn) {
          // The subscription gets ignored after initial route guard check, so
          // we need to manually navigate instead of return a UrlTree.
          this.router.navigate(['login'], { queryParams: { returnUrl: state.url } });
          return false;
        } else {
          return true;
        }
      })
      //NOTE:: no need to share replay since with Google sign in we always start off logged out
    );
  }
}
