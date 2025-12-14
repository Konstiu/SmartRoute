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
  @Input() layers: Layer[] = [];
  @Input() interactive = true;
  @Input() addPointMode = false;

  @Output() pointAdded = new EventEmitter<LatLng>();
  @Output() locationError = new EventEmitter();
  @Output() locationSelected = new EventEmitter<LatLng>();

  public map: Map | null = null;
  private locationEmitted = false;

  constructor() { }


  options: MapOptions = {
    layers: [
      // NOTE: This layer is 'blurry' on HiDPI displays. To remidy this one can use a vector tileset (like https://protomaps.com) or use the detectRetina option below (this however makes the text in the images smaller)
      tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 18,
        attribution: "Map data from <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a>",
        keepBuffer: 100,              // keep tiles outside viewport
         }),
    ],
    zoomAnimation: true,
    zoomAnimationThreshold: 0,
  };
  zoom = 10;
  center = latLng(48.2081693881957, 16.3738174047985);

  onMapReady(map: Map) {
    this.map = map;

    // Ensure correct sizing (Leaflet quirk)
    setTimeout(() => map.invalidateSize(), 100);

    if (!this.interactive) {
      this.disableInteraction(map);
    }
  }

  private emitLocationOnce(location: LatLng) {
    if (this.locationEmitted) return;

    this.locationEmitted = true;
    this.locationSelected.emit(location);
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
        this.emitLocationOnce(location);
      }
    }
  }

  private async getLocation(): Promise<LatLng | null> {
    try {
      const pos = await Geolocation.getCurrentPosition({
        enableHighAccuracy: true,
        maximumAge: 0
      });

      return latLng(pos.coords.latitude, pos.coords.longitude);
    } catch (e) {
      console.error('Geolocation failed:', e);
      if (e instanceof GeolocationPositionError) {
        this.locationError.emit(e);
      }
      return null;
    }
  }

  private touched = false;
  private touchTimeout: any;
  private lastClick = 0;
  private clickTimeout: any;

  onClick(event: LeafletMouseEvent) {
    if (this.touched) return; // disable click for mobile, as markers get added via tap-and-hold
    if (!this.interactive || !this.addPointMode) return;

    this.pointAdded.emit(event.latlng);
  }

  registerTouchHandlers(map: Map) {
    const container = map.getContainer();

    container.addEventListener('touchstart', (e: TouchEvent) => {
      if (!this.interactive) return;
      this.touched = true;

      if (e.touches.length > 1) return;

      this.touchTimeout = setTimeout(() => {
        const rect = container.getBoundingClientRect();
        const p = new Point(
          e.touches[0].clientX - rect.left,
          e.touches[0].clientY - rect.top
        );

        this.emitLocationOnce(map.containerPointToLatLng(p));
      }, 500);
    });

    container.addEventListener('touchmove', () =>
      clearTimeout(this.touchTimeout)
    );
    container.addEventListener('touchend', () =>
      clearTimeout(this.touchTimeout)
    );
  }

  // -------- INTERACTION --------
  private disableInteraction(map: Map) {
    map.dragging.disable();
    map.scrollWheelZoom.disable();
    map.doubleClickZoom.disable();
    map.boxZoom.disable();
    map.keyboard.disable();
  }
}
