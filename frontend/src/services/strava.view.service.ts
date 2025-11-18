import {inject, Injectable} from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import {map, Observable} from 'rxjs';
import { StravaActivity } from '../app/dtos/StravaActivity';
import {Globals} from "../global/globals";

@Injectable({
  providedIn: 'root'
})
export class StravaViewService {
  private httpClient = inject(HttpClient);
  private globals = inject(Globals);
  private userUri: string = this.globals.backendUri + '/strava';

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
   * @param page Page number (default 1)
   * @param perPage Number of activities per page (default 30, max 200)
   */
  getRecentActivities(page: number = 1, perPage: number = 30): Observable<StravaActivity[]> {
    const url = `${this.userUri}/activities/view`;
    return this.http.get<StravaActivity[]>(url, { headers: this.getAuthHeaders() });
  }

  //TODO: We will have to implement some logic to fetch exactly one
  getActivityById(id:number){
    return this.getRecentActivities(1,30).pipe(
      map((activity: any[]) => activity[id])
    );
  }
}
