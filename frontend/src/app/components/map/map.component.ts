import { Component, Input, OnInit } from '@angular/core';
import { LeafletDirective, LeafletLayersDirective } from '@bluehalo/ngx-leaflet';
import { Icon, icon, LatLng, latLng, Layer, MapOptions, marker, polyline, tileLayer } from 'leaflet';
import { Geolocation } from "@capacitor/geolocation"

@Component({
  selector: 'app-map',
  templateUrl: './map.component.html',
  styleUrls: ['./map.component.scss'],
  imports: [LeafletDirective, LeafletLayersDirective],
})
export class MapComponent implements OnInit {

  markerOptions = {
    icon: icon({
      ...Icon.Default.prototype.options,
      iconUrl: 'assets/marker-icon.png',
      iconRetinaUrl: 'assets/marker-icon-2x.png',
      shadowUrl: 'assets/marker-shadow.png'
    })
  };

  options: MapOptions = {
    layers: [
      tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 18, attribution: "Map data from <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a>" }),
      // marker(latLng(48.2081693881957, 16.3738174047985), this.markerOptions),
    ],
    zoom: 10,
    center: latLng(48.2081693881957, 16.3738174047985),
  };
  layers: Layer[] = [];

  @Input() showLocation = false
  @Input() route = []
  @Input() onGeolocationError: ((error: GeolocationPositionError) => void) | null = null

  constructor() { }

  async ngOnInit() {
    if (this.showLocation) {
      const location = await this.getLocation();
      if (location) {
        this.options.center = location;
        this.layers?.push(marker(location, this.markerOptions));
        this.options.zoom = 15;
      }
    }
    if (this.route) {
      this.layers?.push(polyline(this.route))
    }
  }

  async getLocation(): Promise<(LatLng | null)> {
    try {
      const location = await Geolocation.getCurrentPosition();
      return latLng(location.coords.latitude, location.coords.longitude);
    } catch (e) {
      console.log(e);
      if (e instanceof GeolocationPositionError && this.onGeolocationError != null) {
        this.onGeolocationError(e);
      }
    }
    return null;
  }
}
