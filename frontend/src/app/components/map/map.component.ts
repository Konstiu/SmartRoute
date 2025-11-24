import { Component, Input, OnInit } from '@angular/core';
import { LeafletDirective } from '@bluehalo/ngx-leaflet';
import { Icon, icon, LatLng, latLng, MapOptions, marker, polyline, tileLayer } from 'leaflet';
import { Geolocation } from "@capacitor/geolocation"

@Component({
  selector: 'app-map',
  templateUrl: './map.component.html',
  styleUrls: ['./map.component.scss'],
  imports: [LeafletDirective],
  inputs: ["showLocation"]
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
      marker(latLng(48.2081693881957, 16.3738174047985), this.markerOptions),
    ],
    zoom: 10,
    center: latLng(48.2081693881957, 16.3738174047985),
  };

  @Input() showLocation = false
  @Input() route = []

  constructor() { }

  async ngOnInit() {
    if (this.showLocation) {
      const location = await this.getLocation();
      this.options.center = location;
      this.options.layers?.push(marker(location));
      this.options.zoom = 15;
    }
    if (this.route) {
      this.options.layers?.push(polyline(this.route))
    }
  }

  async getLocation(): Promise<LatLng> {
    const location = await Geolocation.getCurrentPosition();
    return latLng(location.coords.latitude, location.coords.longitude);
    // return latLng(48.2081693881957, 16.3738174047985);
  }
}
