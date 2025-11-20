import {inject, Injectable} from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import {map, Observable} from 'rxjs';
import {DetailedStravaActivity, StravaActivity} from '../app/dtos/StravaActivity';
import {Globals} from "../global/globals";

@Injectable({
  providedIn: 'root'
})
export class ActivitiesService {
  private httpClient = inject(HttpClient);
  private globals = inject(Globals);
  private userUri: string = this.globals.backendUri + '/activities';

  // eslint-disable-next-line @angular-eslint/prefer-inject
  constructor(private readonly http: HttpClient) {}

  /**
   * Get authorization headers with Bearer token
   */
  private getAuthHeaders(): HttpHeaders {
    const token = localStorage.getItem('authToken');
    return new HttpHeaders({
      'Authorization': token ? `Bearer ${token}` : '',
      'Content-Type': 'application/json'
    });
  }

  /**
   * Fetch recent activities from Strava
   */
  getRecentActivities(): Observable<StravaActivity[]> {
    const url = `${this.userUri}`;
    return this.http.get<StravaActivity[]>(url, { headers: this.getAuthHeaders() });
  }


  /**
   * Fetch one single activity by its id.
   */
  getActivityById(id:number): Observable<DetailedStravaActivity>{
    const url = `${this.userUri}/${id}`;
    return this.http.get<DetailedStravaActivity>(url, { headers: this.getAuthHeaders() });
  }
}
