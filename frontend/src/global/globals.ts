import {Injectable} from "@angular/core";

@Injectable({providedIn: "root"})
export class Globals {
  readonly backendUri: string = 'http://localhost:8081/api/v1';
}
