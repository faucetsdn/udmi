import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AuthService } from '../auth/auth.service';
import { LoginComponent } from './login.component';
import { LoginModule } from './login.module';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let mockAuthService: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    mockAuthService = jasmine.createSpyObj(AuthService, ['loginWithGoogle']);

    await TestBed.configureTestingModule({
      imports: [LoginModule],
      providers: [{ provide: AuthService, useValue: mockAuthService }],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should be able to call the login functionality', () => {
    component.loginWithGoogle();
    expect(mockAuthService.loginWithGoogle).toHaveBeenCalled();
  });
});
