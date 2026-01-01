import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AddStopsRequest, GeoJsonPosition } from '../app/dtos/add-stops';
import { GeneratedRouteDto } from "src/app/dtos/route"
import { Globals } from "src/global/globals";

@Injectable({ providedIn: 'root' })
export class StopsService {
  private readonly httpClient: HttpClient = inject(HttpClient);
  private readonly globals: Globals = inject(Globals);
  private readonly baseUri: string = this.globals.backendUri + '/stops/insert';

  insertStops(req: AddStopsRequest): Observable<GeneratedRouteDto> {
    console.log("AddStopsRequest payload:", JSON.stringify(req));
    return this.httpClient.post<GeneratedRouteDto>(this.baseUri, req)
  }
}
