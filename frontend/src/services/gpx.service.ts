import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Globals } from '../global/globals';
import { Observable } from 'rxjs';
import { DetailedStravaActivity } from '../app/dtos/StravaActivity';

@Injectable({ providedIn: 'root' })
export class GpxService {
  private http = inject(HttpClient);
  private globals = inject(Globals);

  private baseUri = this.globals.backendUri + '/gpx';

  /**
   * Get authorization headers with Bearer token
   */
  private getAuthHeaders(): HttpHeaders {
    const token = localStorage.getItem('authToken');
    return new HttpHeaders({
      'Authorization': token ? `Bearer ${token}` : ''
    });
  }

  /**
   * Upload multiple GPX files to the backend endpoint that imports Strava GPX files.
   * Endpoint: POST {backendUri}/gpx/import-strava
   * Form param name: files (List<MultipartFile>)
   */
  importStravaGpx(files: File[]): Observable<DetailedStravaActivity[]> {
    const url = `${this.baseUri}/import-strava`;
    const form = new FormData();
    files.forEach(f => form.append('files', f, f.name));

    return this.http.post<DetailedStravaActivity[]>(url, form, { headers: this.getAuthHeaders() });
  }
}
