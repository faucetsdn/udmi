import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { BehaviorSubject } from 'rxjs';
import { AuthGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('AuthGuard', () => {
  let guard: AuthGuard;
  let mockAuthService: jasmine.SpyObj<AuthService>;
  let route: ActivatedRouteSnapshot;
  let state: RouterStateSnapshot;

  beforeEach(() => {
    mockAuthService = jasmine.createSpyObj(AuthService, ['isLoggedIn$']);
    mockAuthService.isLoggedIn$ = new BehaviorSubject<boolean | null>(null);

    TestBed.configureTestingModule({
      imports: [RouterTestingModule],
      providers: [{ provide: AuthService, useValue: mockAuthService }],
    });
    guard = TestBed.inject(AuthGuard);
  });

  it('should be created', () => {
    expect(guard).toBeTruthy();
  });

  it('should redirect to the login screen when not logged in', () => {
    mockAuthService.isLoggedIn$.next(false);
    guard.canActivate(route, state).subscribe((res) => expect(res.toString()).toEqual('/login'));
  });

  it('should proceed when logged in', () => {
    mockAuthService.isLoggedIn$.next(true);
    guard.canActivate(route, state).subscribe((res) => expect(res).toEqual(true));
  });
});
