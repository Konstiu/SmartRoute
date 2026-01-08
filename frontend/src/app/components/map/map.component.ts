import { Component, EventEmitter, Input, OnInit, Output, AfterViewInit, NgZone } from '@angular/core';
import { LeafletDirective, LeafletLayersDirective } from '@bluehalo/ngx-leaflet';
import { LatLng, latLng, Layer, MapOptions, tileLayer, Map, LeafletMouseEvent, Point } from 'leaflet';
import { Geolocation } from "@capacitor/geolocation";

@Component({
  selector: 'app-map',
  templateUrl: './map.component.html',
  styleUrls: ['./map.component.scss'],
  imports: [LeafletDirective, LeafletLayersDirective],
})
export class MapComponent implements OnInit, AfterViewInit {

  @Input() showLocation = false;
  @Input() layers: Layer[] = [];
  @Input() interactive = true;
  @Input() addPointMode = false;

  @Output() pointAdded = new EventEmitter<LatLng>();
  @Output() exactLocationFailed = new EventEmitter<void>();
  @Output() locationError = new EventEmitter<void>(); // final failure
  @Output() locationSelected = new EventEmitter<LatLng>();

  public map: Map | null = null;

  private locationEmitted = false;
  private defaultViewApplied = false;

  zoom = 10;
  center = latLng(48.2081693881957, 16.3738174047985); // Vienna

  constructor(private zone: NgZone) {}

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

  onMapReady(map: Map) {
    this.map = map;

    // Always show something immediately (Vienna) before geolocation resolves
    this.applyDefaultView();

    // Leaflet sizing quirks (Ionic transitions, permission prompts)
    this.invalidateMapSizeSoon();

    if (!this.interactive) {
      this.disableInteraction(map);
    }
  }

  ngAfterViewInit() {
    this.invalidateMapSizeSoon();
  }

  async ngOnInit() {
    if (!this.showLocation) return;

    const location = await this.getLocationWithFallback();

    // Location may finish before map is ready -> still fine: applyDefaultView() handles that.
    if (location) {
      // Only move map to location if we haven't already been moved somewhere else.
      // (Parent might call fitBounds for a route.)
      if (this.map && this.defaultViewApplied) {
        this.map.setView(location, this.zoom, { animate: true });
      }

      this.emitLocationOnce(location);
    }

    // Always re-invalidate after geo completes (success OR fail)
    this.invalidateMapSizeSoon();
  }

  private applyDefaultView() {
    if (!this.map) return;
    if (this.defaultViewApplied) return;

    this.defaultViewApplied = true;
    this.map.setView(this.center, this.zoom, { animate: false });
  }

  private invalidateMapSizeSoon() {
    if (!this.map) return;

    this.zone.runOutsideAngular(() => {
      requestAnimationFrame(() => this.map?.invalidateSize(true));
      setTimeout(() => this.map?.invalidateSize(true), 150);
      setTimeout(() => this.map?.invalidateSize(true), 400);
    });
  }

  private emitLocationOnce(location: LatLng) {
    if (this.locationEmitted) return;
    this.locationEmitted = true;
    this.locationSelected.emit(location);
  }

private async getLocationWithFallback(): Promise<LatLng | null> {
  // Try exact location (GPS)
  const exact = await this.tryGetLocation(true);
  if (exact) return exact;

  // Exact failed -> notify parent
  this.exactLocationFailed.emit();

  // Try imprecise location
  const coarse = await this.tryGetLocation(false);
  if (coarse) return coarse;

  // Everything failed
  this.locationError.emit();
  return null;
}


  private async tryGetLocation(enableHighAccuracy: boolean): Promise<LatLng | null> {
    try {
      const pos = await Geolocation.getCurrentPosition({
        enableHighAccuracy,
        timeout: 12000,
        maximumAge: enableHighAccuracy ? 0 : 5 * 60 * 1000,
      });

      return latLng(pos.coords.latitude, pos.coords.longitude);
    } catch (e) {
      return null;
    }
  }

  private touched = false;
  private touchTimeout: any;

  onClick(event: LeafletMouseEvent) {
    if (this.touched) return;
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

    container.addEventListener('touchmove', () => clearTimeout(this.touchTimeout));
    container.addEventListener('touchend', () => clearTimeout(this.touchTimeout));
  }

  private disableInteraction(map: Map) {
    map.dragging.disable();
    map.scrollWheelZoom.disable();
    map.doubleClickZoom.disable();
    map.boxZoom.disable();
    map.keyboard.disable();
  }
}
