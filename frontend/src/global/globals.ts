import {Injectable} from "@angular/core";
import {Capacitor} from "@capacitor/core";

@Injectable({ providedIn: "root" })
export class Globals {
  readonly backendUri: string = this.findBackendUrl();
  readonly vapidPublicKey: string = 'BHpFgSD4JeTk9Y5NsOBYs8hxqXBS1ocDB1CCkedh45gvRBnlaDOh9lQI8wUOEfr5olcx4m-MpnRL9T2oaTBTec4';


  private findBackendUrl(): string {
    if (Capacitor.isNativePlatform()){
      // return the deployment right now because when we are on the native phone capacitor, we need to know where the backend is.
      return "https://25ws-ase-pr-inso-05.apps.student.inso-w.at/api/v1"
    }
    if (window.location.port === '8100') { // local `ionic serve`, backend at localhost:8080
      return 'http://localhost:8080/api/v1';
    } else {
      // assume deployed somewhere and backend is available at same host/port as frontend
      return window.location.protocol + '//' + window.location.host + '/api/v1';
    }
  }
}
