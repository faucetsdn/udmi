import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { AuthService, User } from './services/auth.service';
import { Origin, OriginsService } from './services/origins.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {
  public title = 'UDMS';
  public origins!: Observable<Origin[]>;
  public user!: Observable<User | undefined>;
  public selectedOrigin?: Origin;

  constructor(private originsService: OriginsService, private auth: AuthService) { }

  ngOnInit(): void {
    this.user = this.auth.getUser();
    this.origins = this.originsService.getOrigins().pipe(map((origins) => {
      if (this.selectedOrigin || !origins.length) {
        return origins;
      }
      this.changeOrigin(origins[0]); // Defaults to first origin
      return origins;
    }));
  }

  public changeOrigin(origin: Origin): void {
    this.selectedOrigin = origin;
    this.originsService.changeOrigin(origin);
  }
}
