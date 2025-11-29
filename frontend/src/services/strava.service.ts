import {Injectable, inject} from '@angular/core';
import {Globals} from '../global/globals';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {StravaActivity} from "../app/dtos/StravaActivity";
import {StravaAccountConnectionStateDto} from '../app/dtos/strava-account-connection-state'
import { Observable } from "rxjs";

@Injectable({
  providedIn: 'root',
})
export class StravaService {
  private httpClient: HttpClient = inject(HttpClient);
  private globals: Globals = inject(Globals);

  private stravaBaseUri: string = this.globals.backendUri + '/strava';

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
   * Redirects to the Strava OAuth page.
   */
  //TODO: implement interceptor for token
  connectStravaAccount(origin: "register" | "tabs/account"): void {
    this.httpClient.get(this.stravaBaseUri + `/connect?origin=${origin}`, {headers: this.getAuthHeaders(), responseType: "text"})
      .subscribe(url => {
        window.location.href = url;
      })
  }

  getConnectionState(): Observable<StravaAccountConnectionStateDto> {
    return this.httpClient.get<StravaAccountConnectionStateDto>(this.stravaBaseUri + '/connection-state', {headers: this.getAuthHeaders()})
  }
}
