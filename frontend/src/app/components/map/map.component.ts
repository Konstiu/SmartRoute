import { Component, EventEmitter, Input, OnInit, Output, SimpleChanges } from '@angular/core';
import { LeafletDirective, LeafletLayersDirective } from '@bluehalo/ngx-leaflet';
import { Icon, icon, LatLng, latLng, Layer, MapOptions, marker, tileLayer, Map, Polyline, LeafletMouseEvent, Marker, Point, latLngBounds } from 'leaflet';
import { Geolocation } from "@capacitor/geolocation"

@Component({
  selector: 'app-map',
  templateUrl: './map.component.html',
  styleUrls: ['./map.component.scss'],
  imports: [LeafletDirective, LeafletLayersDirective],
})
export class MapComponent implements OnInit {

  @Input() showLocation = false;
  @Input() route: Polyline | null = null;
  @Input() layers: Layer[] = [];

  @Output() onGeolocationError = new EventEmitter();
  @Output() onNewLocationRegisterd = new EventEmitter();
  @Output() geoLocation = new EventEmitter();

  @Output() leafletMapReady = new EventEmitter<Map>();

  public map: Map | null = null;

  constructor() { }

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
      // NOTE: This layer is 'blurry' on HiDPI displays. To remidy this one can use a vector tileset (like https://protomaps.com) or use the detectRetina option below (this however makes the text in the images smaller)
      tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 18, attribution: "Map data from <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a>" }),
    ],
    zoomAnimation: true,
    zoomAnimationThreshold: 0,
  };
  zoom = 10;
  center = latLng(48.2081693881957, 16.3738174047985);
  markerSelection: Marker | null = null;

  touchTimeout: any;
  touched = false;

  onMapReady(map: Map) {
    setTimeout(() => map.invalidateSize(), 100); // See https://github.com/bluehalo/ngx-leaflet/issues/104
    this.map = map;
    this.leafletMapReady.emit(map);
    // register events to detect new markers
    map.getContainer().addEventListener("touchstart", (e) => {
      this.touched = true;
      if (e.touches.length > 1) return;
      this.touchTimeout = setTimeout(() => {
        let bb = map.getContainer().getBoundingClientRect()
        let p = new Point(e.touches[0].clientX - bb.left, e.touches[0].clientY - bb.top)
        let location = map.containerPointToLatLng(p);
        this.onNewLocationRegisterd.next(location);
      }, 500);
    })
    map.getContainer().addEventListener("touchmove", () => clearTimeout(this.touchTimeout));
    map.getContainer().addEventListener("touchend", () => clearTimeout(this.touchTimeout));
  }

  ngOnChanges(changes: SimpleChanges) {
    // When route input changes and map exists, fit bounds to route
    if (true) {
      return;
    }
  }

  async ngOnInit() {
    if (this.showLocation) {
      const location = await this.getLocation();
      if (location) {
        this.geoLocation.next(location);
      }
    }
    if (this.route) {
      this.layers.push(this.route)
    }
  }

  async getLocation(): Promise<(LatLng | null)> {
    try {
      const location = await Geolocation.getCurrentPosition({
        enableHighAccuracy: true,
        maximumAge: 0
      });
      return latLng(location.coords.latitude, location.coords.longitude);
    } catch (e) {
      console.error("ERROR: unable to determine position:", e);
      if (e instanceof GeolocationPositionError && this.onGeolocationError != null) {
        this.onGeolocationError.next(e);
      }
    }
    return null;
  }

  clickTimeout: any;
  lastClick = 0;

  onClick(event: LeafletMouseEvent) {
    if (this.touched) return; // disable click for mobile, as markers get added via tap-and-hold
    if (Date.now() - this.lastClick < 450) {
      clearTimeout(this.clickTimeout);
    } else {
      this.clickTimeout = setTimeout(() => {
        this.onNewLocationRegisterd.next(event.latlng);
      }, 500);
    }
    this.lastClick = Date.now();
  }
}
