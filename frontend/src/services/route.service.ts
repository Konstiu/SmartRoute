import {HttpClient} from "@angular/common/http";
import {inject, Injectable} from "@angular/core";
import {BehaviorSubject, Observable} from "rxjs";
import {Globals} from "src/global/globals";
import {GeneratedRouteDto} from "src/app/dtos/route";
import {SaveRouteDto, ViewRouteDto} from "../app/dtos/recommended-activity";

@Injectable({
  providedIn: 'root',
})
export class RouteService {
  private readonly httpClient: HttpClient = inject(HttpClient);
  private readonly globals: Globals = inject(Globals);

  private readonly routeBaseUri: string = this.globals.backendUri + '/route';

  private lastRoute: GeneratedRouteDto | null = null;

  /**
   * Generate route for ('lat', 'long') with length 'length'.
   */
  getGeneratedRoute(lat: number, long: number, length: number): Observable<GeneratedRouteDto> {
    let route = this.httpClient.get<GeneratedRouteDto>(this.routeBaseUri, {
      params: {
        "lat": lat,
        "long": long,
        "length": length
      }
    });
    return route;
  }

  getLastGeneratedRoute(): GeneratedRouteDto | null {
    return this.lastRoute;
  }

  /**
   * Save Route to user
   * @param dto
   */
  saveRoute(dto: SaveRouteDto) {
    return this.httpClient.post(this.routeBaseUri + '/save', dto);
  }

  /**
   * Get one route by id
   * @param id
   */
  getRoute(id: number): Observable<ViewRouteDto> {
    return this.httpClient.get<ViewRouteDto>(`${this.routeBaseUri}/${id}`);
  }

  /**
   * Get all saved routes from the user
   */
  getRoutes(): Observable<ViewRouteDto[]> {
    return this.httpClient.get<ViewRouteDto[]>(this.routeBaseUri + '/get');
  }

  /**
   * delete route with id
   * @param id
   */
  delete(id: number) {
    return this.httpClient.delete(this.routeBaseUri + `/${id}`);
  }

  share(id: number, friend: string[]){
    console.log(this.routeBaseUri + `/${id}/share`, {"friends":friend});
    return this.httpClient.put(this.routeBaseUri + `/${id}/share`, {"friends":friend})
  }
}
