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
   * Redirects to the Strava OAuth page.
   */
  connectStravaAccount(origin: "register" | "tabs/account"): void {
    this.httpClient.get(this.stravaBaseUri + `/connect?origin=${origin}`, {responseType: "text"})
      .subscribe(url => {
        window.location.href = url;
      })
  }

  /**
   * Disconnects a connected Strava account.
   */
  disconnectStravaAccount(): Observable<StravaAccountConnectionStateDto> {
    return this.httpClient.delete<StravaAccountConnectionStateDto>(this.stravaBaseUri + `/disconnect`);
  }

  /**
   * Returns Strava API connection state information.
   */
  getConnectionState(): Observable<StravaAccountConnectionStateDto> {
    return this.httpClient.get<StravaAccountConnectionStateDto>(this.stravaBaseUri + '/connection-state')
  }
}
