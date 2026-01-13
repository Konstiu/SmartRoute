import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, Subject} from 'rxjs';
import {DetailedActivity, Activity} from '../app/dtos/Activity';
import {Globals} from "../global/globals";
import {RunClassificationDto, RunType} from "../app/dtos/run-classification";

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
  constructor(private readonly http: HttpClient) {
  }


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
  refreshActivities(count: number): Observable<void> {
    const url = `${this.userUri}/sync`;
    return this.httpClient.post<void>(url, count);
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

  /**
   * Updates the run type classification for the selected run.
   *
   * @param id the activity id
   * @param runType the updated run type
   */
  updateClassification(id: number, runType: RunType): Observable<void> {
    const url = `${this.userUri}/classification/correction/${id}`;
    return this.httpClient.post<void>(url, JSON.stringify(runType),
      {
        headers: { 'Content-Type': 'application/json' }
      });
  }

  private activityUpdated = new Subject<number>();

  activityUpdated$ = this.activityUpdated.asObservable();

  notifyActivityUpdate(activityId: number) {
    this.activityUpdated.next(activityId);
  }
}
