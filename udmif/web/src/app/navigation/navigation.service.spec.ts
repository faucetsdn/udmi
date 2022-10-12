import { TestBed } from '@angular/core/testing';
import { NavigationService } from './navigation.service';

describe('NavigationService', () => {
  let service: NavigationService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(NavigationService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should set the title', () => {
    service.setTitle('my title');

    service.title$.subscribe((title) => {
      expect(title).toEqual('my title');
    });
  });

  it('should clear the title', () => {
    service.clearTitle();

    service.title$.subscribe((title) => {
      expect(title).toEqual('');
    });
  });
});
