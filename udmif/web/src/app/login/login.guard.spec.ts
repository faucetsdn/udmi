import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { BehaviorSubject } from 'rxjs';
import { LoginGuard } from './login.guard';
import { AuthService } from '../auth/auth.service';

describe('LoginGuard', () => {
  let guard: LoginGuard;
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
    guard = TestBed.inject(LoginGuard);
  });

  it('should be created', () => {
    expect(guard).toBeTruthy();
  });

  it('should redirect to the devices screen when logged in', () => {
    mockAuthService.isLoggedIn$.next(true);
    guard.canActivate(route, state).subscribe((res) => expect(res).toEqual(false));
  });

  it('should proceed when not logged in', () => {
    mockAuthService.isLoggedIn$.next(false);
    guard.canActivate(route, state).subscribe((res) => expect(res).toEqual(true));
  });
});
