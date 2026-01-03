import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {catchError, map, Observable, of, throwError} from 'rxjs';
import {DetailedActivity, Activity} from '../app/dtos/Activity';
import {Globals} from "../global/globals";
import {SyncOutcome, SyncStatusDto} from "../app/dtos/syncStates";

@Injectable({
  providedIn: 'root'
})
export class ActivitiesService {
  private httpClient = inject(HttpClient);
  private globals = inject(Globals);
  private userUri: string = this.globals.backendUri + '/activities';

  private readonly MAX_REFRESHES = 3;
  private readonly COOLDOWN_MS = 5 * 60 * 1000;

  // eslint-disable-next-line @angular-eslint/prefer-inject
  constructor(private readonly http: HttpClient) {}


  /**
   * Fetch recent activities.
   */
  getRecentActivities(): Observable<Activity[]> {
    const url = `${this.userUri}`;
    return this.httpClient.get<Activity[]>(url);
  }

  /**
   * Fetches activities from all connected Services (Strava, Garmin).
   */
  refreshActivities(count: number, requestId: string): Observable<void> {
    const url = `${this.userUri}/sync`;
    const headers = new HttpHeaders({ 'X-Request-Id': requestId });
    return this.httpClient.post<void>(url, { count }, { headers });
  }

  getSyncStatus(requestId: string): Observable<SyncStatusDto> {
    const url = `${this.userUri}/sync/status/${requestId}`;
    return this.httpClient.get<SyncStatusDto>(url);
  }

  /**
   * Starts sync. If the POST fails with status 0, validates via status endpoint instead of retrying.
   */
  syncWithValidation(count: number): Observable<{ requestId: string; outcome: SyncOutcome }> {
    const requestId = crypto.randomUUID();

    return this.refreshActivities(count, requestId).pipe(
      map(() => ({ requestId, outcome: { kind: 'success' as const } })),
      catchError(err => {
        if (err?.status !== 0) {
          return throwError(() => err);
        }

        // status 0 -> unknown outcome, try to confirm
        return this.getSyncStatus(requestId).pipe(
          map(status => {
            if (status.state === 'SUCCESS') {
              return { requestId, outcome: { kind: 'success' as const } };
            }
            if (status.state === 'RUNNING') {
              return { requestId, outcome: { kind: 'running' as const } };
            }
            return { requestId, outcome: { kind: 'failed' as const, message: status.message } };
          }),
          catchError(() => of({ requestId, outcome: { kind: 'unknown' as const } }))
        );
      })
    );
  }

  /**
   * Checks if the user reached the refresh limit.
   */
  canRefresh(): boolean {
    const refreshCount = Number(sessionStorage.getItem('activities_refresh_count')) || 0;
    const lastRefreshStr = sessionStorage.getItem('last_activities_refresh');
    if (!lastRefreshStr) return true;

    const lastRefresh = Number(lastRefreshStr);
    const now = Date.now();

    if (now - lastRefresh >= this.COOLDOWN_MS) {
      sessionStorage.setItem('activities_refresh_count', String(0));
      sessionStorage.setItem('last_activities_refresh', String(Date.now()));
    }

    return refreshCount < this.MAX_REFRESHES;
  }

  /**
   * Increments the refresh count and sets the last refresh time.
   */
  incrementRefreshCount(): void {
    const refreshCount = Number(sessionStorage.getItem('activities_refresh_count')) || 0;

    sessionStorage.setItem('activities_refresh_count', String(refreshCount + 1));
    sessionStorage.setItem('last_activities_refresh', String(Date.now()));
  }

  /**
   * Fetch one single activity by its id.
   */
  getActivityById(id: number): Observable<DetailedActivity> {
    const url = `${this.userUri}/${id}`;
    return this.httpClient.get<DetailedActivity>(url);
  }
}
