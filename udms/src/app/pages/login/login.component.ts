import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService } from 'src/app/services/auth.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit {

  public authenticated!: Observable<{authenticated: boolean}>;

  constructor(private service: AuthService, private router: Router) { }

  ngOnInit(): void {
    this.authenticated = this.service.isAuthenticated();
  }

  public signIn(): void {
    this.service.signIn().subscribe((user) => {
      this.router.navigate(['/dashboard']);
    });
  }

  public signOut(): void {
    this.service.signOut();
  }

}
