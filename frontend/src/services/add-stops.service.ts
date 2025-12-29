import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AddStopsRequest, GeoJsonPosition } from '../app/dtos/add-stops';

@Injectable({ providedIn: 'root' })
export class StopsService {
  constructor(private http: HttpClient) {}

  insertStops(req: AddStopsRequest): Observable<GeoJsonPosition[]> {
    return this.http.post<GeoJsonPosition[]>('/api/stops/insert', req);
  }
}
