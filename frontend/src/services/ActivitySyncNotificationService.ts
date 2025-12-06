import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class ActivitySyncNotificationService {
  private syncCompletedSubject = new Subject<void>();

  syncCompleted = this.syncCompletedSubject.asObservable();

  notifySyncCompleted() {
    this.syncCompletedSubject.next();
  }
}
