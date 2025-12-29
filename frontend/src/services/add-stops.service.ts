import {inject, Injectable} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AddStopsRequest, GeoJsonPosition } from '../app/dtos/add-stops';
import {RouteWithFacilityDefaults} from "../app/dtos/RouteWithFacilitiesDto";
import {ViennaPointDto} from "../app/dtos/ViennaPointsDto";
import {Globals} from "../global/globals";

@Injectable({ providedIn: 'root' })
export class StopsService {
  private http = inject(HttpClient);
  private globals = inject(Globals);

  private baseUri = this.globals.backendUri + '/stops/';

  insertStops(req: AddStopsRequest): Observable<GeoJsonPosition[]> {
    return this.http.post<GeoJsonPosition[]>(this.baseUri + 'insert', req);
  }

  addFacilitiesStops(req: RouteWithFacilityDefaults): Observable<GeoJsonPosition[]> {
    return this.http.post<GeoJsonPosition[]>(this.baseUri+'with-facilities', req);
  }

  getAllFacilities(): Observable<ViennaPointDto[]> {
    return this.http.get<ViennaPointDto[]>(this.baseUri + 'facilities');
  }
}
