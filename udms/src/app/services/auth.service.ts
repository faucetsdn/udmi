import { Injectable } from '@angular/core';
import firebase from 'firebase/app';
import 'firebase/auth';
import { AngularFireAuth } from '@angular/fire/auth';
import { from, Observable } from 'rxjs';
import { map } from 'rxjs/operators';

export interface User {
  id: string;
  name?: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  constructor(private service: AngularFireAuth) { }

  public isAuthenticated(): Observable<{ authenticated: boolean }> {
    return this.getUser().pipe(map((user) => {
      return { authenticated: !!user };
    }));
  }

  public getUser(): Observable<User | undefined> {
    return this.service.user.pipe(map((user) => {
      if (!user) {
        return undefined;
      }
      return {
        id: user.uid,
        name: user.displayName || user.uid
      };
    }));
  }

  public signIn(): Observable<firebase.auth.UserCredential> {
    return from(this.service.signInWithPopup(new firebase.auth.GoogleAuthProvider()));
  }

  public signOut(): Observable<void> {
    return from(this.service.signOut());
  }

}
