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
    const token = localStorage.getItem('authToken'); // Adjust based on where you store the token
    return new HttpHeaders({
      'Authorization': `Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJpc3MiOiJzZWN1cmUtYmFja2VuZCIsImF1ZCI6InNlY3VyZS1hcHAiLCJzdWIiOiJlbWFpbDBAc21hcnRyb3V0ZS5jb20iLCJleHAiOjE3NjMyNDUyMDgsInJvbCI6WyJST0xFX1VTRVIiXX0.bQXpPrXiDDcugOvVZK5OUHHbBmxKjRt8Jg0gRi1cFZhKO6FXc016dql_0AFgs6Hjuu2vReg-LBA-nIXBQJy8Eg`,
      'Content-Type': 'application/json'
    });
    //TODO: Here we have to actually du {token} but because no login module is ready yet we have to set the token ourselves for now
  }

  /**
   * Fetch recent activities from Strava
   * @param page Page number (default 1)
   * @param perPage Number of activities per page (default 30, max 200)
   */
  getRecentActivities(page: number = 1, perPage: number = 30): Observable<StravaActivity[]> {
    const url = `${this.userUri}/activities/imp`;
    return this.http.get<StravaActivity[]>(url, { headers: this.getAuthHeaders() });
  }

  //TODO: We will have to implement some logic to fetch exactly one
  getActivityById(id:number){
    return this.getRecentActivities(1,30).pipe(
      map((activity: any[]) => activity[id])
    );
  }
}
