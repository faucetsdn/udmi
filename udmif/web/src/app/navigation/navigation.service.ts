import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class NavigationService {
  private _title: BehaviorSubject<string> = new BehaviorSubject('');
  readonly title$: Observable<string> = this._title.asObservable();

  setTitle(title: string = ''): void {
    this._title.next(title);
  }

  clearTitle(): void {
    this._title.next('');
  }
}
