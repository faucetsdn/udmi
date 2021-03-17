import { Injectable } from '@angular/core';
import { AngularFirestore } from '@angular/fire/firestore';
import { Observable, Subscriber } from 'rxjs';
import { map } from 'rxjs/operators';
import { SystemType } from '../enums';

export interface Origin {
  type: SystemType;
  id: string;
}

@Injectable({
  providedIn: 'root'
})
export class OriginsService {
  private changeListeners: Subscriber<Origin>[] = [];
  private currentOrigin?: Origin;

  constructor(private firestore: AngularFirestore) { }

  public getOrigins(): Observable<Origin[]> {
    return this.firestore.collection('origin').valueChanges({ idField: 'id' }).pipe(map((docs) => {
      return docs.map((doc: any) => {
        return {
          type: doc.type as SystemType,
          id: doc.id
        };
      });
    }));
  }

  public changeOrigin(origin: Origin): void {
    if (this.currentOrigin === origin) {
      return;
    }
    this.currentOrigin = origin;
    this.changeListeners = this.changeListeners.filter((subscriber) => !subscriber.closed)
      .map((subscriber) => {
        subscriber.next(origin);
        return subscriber;
      });
  }

  public onOriginChange(): Observable<Origin> {
    return new Observable<Origin>((subscriber) => {
      this.changeListeners.push(subscriber);
      subscriber.next(this.currentOrigin);
    });
  }
}
