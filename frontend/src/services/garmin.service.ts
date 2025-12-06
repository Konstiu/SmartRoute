import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Globals } from '../global/globals';
import { Observable, of } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class GarminService {
  private http: HttpClient = inject(HttpClient);
  private globals: Globals = inject(Globals);

  private baseUri = this.globals.backendUri + '/garmin';

  /**
   * Base Garmin API client for the frontend.
   *
   * The service calls these backend endpoints (all under `${baseUri}`):
   * - `GET /connection-state` -> returns a boolean indicating whether a Garmin
   *   account is connected for the current user.
   * - `POST /sync` -> triggers a manual sync using provided credentials.
   * - `POST /disconnect` -> disconnects the Garmin account.
   *
   * Notes:
   * - Requests rely on the global `Globals.backendUri` to build the base URI.
   * - Authentication (Authorization header) should be attached by an HTTP
   *   interceptor elsewhere in the app so calls are made on behalf of the
   *   authenticated user.
   */

  /**
   * Returns whether a Garmin account is currently connected for the logged-in user.
   *
   * Backend endpoint: GET `${baseUri}/connection-state`
   *
   * @returns Observable<boolean> - emits `true` when connected, `false` otherwise.
   */
  getConnectionState(): Observable<boolean> {
    return this.http.get<boolean>(this.baseUri + '/connection-state');
  }

  /**
   * Triggers a manual Garmin sync on the backend.
   *
   * Backend endpoint: POST `${baseUri}/sync`
   * Body: { garminEmail: string, garminPassword: string, count: number }
   *
   * The `email` and `password` parameters are used by the server to authenticate
   * against Garmin. `count` controls how many items (e.g., activities) to sync.
   *
   * The method returns an Observable of the backend response. The frontend
   * component should subscribe and handle success/error states (loading UI,
   * toasts, etc.).
   *
   * @param garminEmail - user email used for Garmin authentication
   * @param garminPassword - user password used for Garmin authentication
   * @param count - number of items to sync (must be >= 1)
   * @returns Observable<any> - backend response (success payload or error)
   */
  sync(garminEmail: string, garminPassword: string, count: number): Observable<any> {
    const body = { garminEmail, garminPassword, count };
    return this.http.post<any>(this.baseUri + '/sync', body);
  }

  /**
   * Disconnects the Garmin account for the current user.
   *
   * Backend endpoint: POST `${baseUri}/disconnect`
   *
   * @returns Observable<void> - completes when server has processed the disconnect.
   */
  disconnect(): Observable<void> {
    return this.http.post<void>(this.baseUri + '/disconnect', {});
  }
}
