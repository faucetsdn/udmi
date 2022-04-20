import { SocialUser } from '@abacritt/angularx-social-login';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { NavigationComponent } from './navigation.component';
import { NavigationModule } from './navigation.module';

describe('NavigationComponent', () => {
  let component: NavigationComponent;
  let fixture: ComponentFixture<NavigationComponent>;
  let mockAuthService: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    mockAuthService = jasmine.createSpyObj(AuthService, ['isLoggedIn$', 'user$', 'logout']);
    mockAuthService.user$ = new BehaviorSubject<SocialUser | null>(null);
    mockAuthService.isLoggedIn$ = new BehaviorSubject<boolean | null>(null);

    await TestBed.configureTestingModule({
      imports: [NavigationModule],
      providers: [{ provide: AuthService, useValue: mockAuthService }],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(NavigationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should be able to call the logout functionality', () => {
    component.logout();
    expect(mockAuthService.logout).toHaveBeenCalled();
  });
});
