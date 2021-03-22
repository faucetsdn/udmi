import { TestBed } from '@angular/core/testing';

import { AngularFireModule } from '@angular/fire';
import { environment } from 'src/environments/environment';
import { OriginsService } from './origins.service';

describe('OriginsService', () => {
  let service: OriginsService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [
        AngularFireModule.initializeApp(environment.firebase),
      ]
    });
    service = TestBed.inject(OriginsService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
