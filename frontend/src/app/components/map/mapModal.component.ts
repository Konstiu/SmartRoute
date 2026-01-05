import { Component, Input, ViewChild } from '@angular/core';
import { IonicModule, ModalController, ToastController } from '@ionic/angular';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms'
import { LatLngBounds, LatLng, Layer, marker, Marker } from 'leaflet';
import { MapComponent } from './map.component';
import { MAP_MARKER_COLORS, coloredMarker } from './map-icon';
import { GeocodingService, GeocodeResult } from 'src/services/geocoding.service';
import { ActionSheetController } from '@ionic/angular';
import { NgZone } from '@angular/core';

type AddStopsMode = 'KEEP_SHAPE' | 'KEEP_LENGTH';

@Component({
  standalone: true,
  selector: 'app-map-modal',
  imports: [IonicModule, CommonModule, MapComponent, FormsModule],
  templateUrl: './mapModal.component.html',
  styleUrls: ['./mapModal.component.scss']
})
export class MapModalComponent {
  private static readonly MAX_STOPS = 3;

  @Input() layers: any[] = [];
  @Input() routeBounds!: LatLngBounds;
  @Input() onConfirm!: (points: LatLng[], mode: AddStopsMode) => Promise<{ layers: Layer[]; bounds: LatLngBounds | null }>;
  @Input() committedStops: LatLng[] = []; // stops already confirmed for this route
  @Input() originalLayers!: Layer[];
  @Input() originalBounds!: LatLngBounds;
  @Input() onReset?: () => Promise<{ layers: Layer[]; bounds: LatLngBounds | null }>;
  @Input() onChangeStart?: (start: LatLng) => Promise<{ layers: Layer[]; bounds: LatLngBounds | null }>;

  @ViewChild(MapComponent) mapComponent!: MapComponent;

  mode: AddStopsMode = 'KEEP_SHAPE';
  addPointMode = false;

  addedPoints: LatLng[] = []; // pending points (not confirmed yet)
  localLayers: Layer[] = [];
  private baseLayers: Layer[] = [];

  isMapReady = false;
  isProcessing = false;
  loadingMessage = 'Loading map…';

  searchQuery = '';
  searchResults: GeocodeResult[] = [];
  isSearching = false;

  private wasReset = false;

  private searchTimer?: any;

  searchMarker: Marker | null = null;
  private searchMarkerPos: LatLng | null = null;

  private clearSearchMarker() {
    this.searchMarker = null;
    this.searchMarkerPos = null;
    this.rebuildEditorLayers();
  }

  constructor(
    private modalCtrl: ModalController,
    private toastCtrl: ToastController,
    private geocoding: GeocodingService,
    private actionSheetCtrl: ActionSheetController,
    private zone: NgZone
  ) {}

  ngOnInit() {
    this.baseLayers = [...this.layers];

    this.rebuildEditorLayers();
  }


  get totalStops(): number {
    return (this.committedStops?.length ?? 0) + (this.addedPoints?.length ?? 0);
  }

  get remainingStops(): number {
    return Math.max(0, MapModalComponent.MAX_STOPS - this.totalStops);
  }

  private async showMaxStopsToast() {
    const toast = await this.toastCtrl.create({
      message: `You can add at most ${MapModalComponent.MAX_STOPS} stops per route.`,
      duration: 1800,
      position: 'bottom'
    });
    await toast.present();
  }

  ionViewDidEnter() {
    this.centerRouteInitially();
  }

  private centerRouteInitially() {
    this.isMapReady = false;
    this.loadingMessage = 'Centering route…';

    requestAnimationFrame(() => {
      const map = this.mapComponent?.map;
      if (!map || !this.routeBounds) return;

      map.invalidateSize();
      map.fitBounds(this.routeBounds, { padding: [50, 50], animate: false });

      setTimeout(() => {
        this.isMapReady = true;
      }, 80);
    });
  }

  centerRoute() {
    const map = this.mapComponent?.map;
    if (!map || !this.routeBounds) return;

    map.fitBounds(this.routeBounds, { padding: [50, 50], animate: true });
  }

  async toggleAddPointMode() {
    // entering add mode: block if already at limit
    if (!this.addPointMode && this.remainingStops === 0) {
      await this.showMaxStopsToast();
      return;
    }

    // cancel -> remove unconfirmed points
    if (this.addPointMode) {
      this.addedPoints = [];
      this.rebuildEditorLayers();

    }

    this.addPointMode = !this.addPointMode;
  }

  async onPointAdded(point: LatLng) {
    if (!this.addPointMode || this.isProcessing) return;

    if (this.totalStops >= MapModalComponent.MAX_STOPS) {
      await this.showMaxStopsToast();
      this.addPointMode = false; // optional: auto-exit add mode
      return;
    }

    this.addedPoints = [...this.addedPoints, point];
    this.rebuildEditorLayers();

    // optional: auto-exit when user hits limit
    if (this.totalStops >= MapModalComponent.MAX_STOPS) {
      this.addPointMode = false;
    }
  }

  close() {
    this.modalCtrl.dismiss({
      addedPoints: this.addedPoints,
      committedStops: this.committedStops
    });
  }

  async confirm() {
    if (!this.onConfirm || this.addedPoints.length === 0 || this.isProcessing) return;

    // Safety check (should already be prevented by UI)
    if (this.totalStops > MapModalComponent.MAX_STOPS) {
      await this.showMaxStopsToast();
      return;
    }

    const points = [...this.addedPoints];

    this.isProcessing = true;
    this.loadingMessage = 'Recalculating route…';

    try {
      const { layers, bounds } = await this.onConfirm(points, this.mode);

      // persist confirmed points as "committed" for this route
      this.committedStops = [...this.committedStops, ...points];

      // update route layers coming from parent (route polyline etc.)
      this.baseLayers = [...layers];
      if (bounds) this.routeBounds = bounds;

      // reset pending editor state
      this.addedPoints = [];
      this.addPointMode = false;

      // rebuild all markers with correct colors
      this.rebuildEditorLayers();

      this.addPointMode = false;

      // refit
      this.loadingMessage = 'Centering route…';

      requestAnimationFrame(() => {
        const map = this.mapComponent?.map;
        if (!map) {
          this.isProcessing = false;
          return;
        }

        map.invalidateSize();

        if (this.routeBounds) {
          map.fitBounds(this.routeBounds, { padding: [50, 50], animate: true });
        }

        setTimeout(() => {
          this.isProcessing = false;
        }, 120);
      });

    } catch (e) {
      console.error('Confirm failed', e);
      this.isProcessing = false;
    }
  }

  toggleMode() {
    this.mode = this.mode === 'KEEP_SHAPE' ? 'KEEP_LENGTH' : 'KEEP_SHAPE';
  }


  onSearchInput(ev: any) {
    const value = (ev?.target?.value ?? '').toString();
    this.searchQuery = value;

    clearTimeout(this.searchTimer);

    // debounce
    this.searchTimer = setTimeout(async () => {
      const q = this.searchQuery.trim();
      if (q.length < 3) {
        this.searchResults = [];
        return;
      }

      this.isSearching = true;
      try {
        this.searchResults = await this.geocoding.search(q);
      } catch (e) {
        console.error('Geocoding failed', e);
        this.searchResults = [];
      } finally {
        this.isSearching = false;
      }
    }, 350);
  }

  clearSearch() {
    this.searchQuery = '';
    this.searchResults = [];
  }

async selectSearchResult(r: { display_name: string; lat: string; lon: string }) {
  const lat = Number(r.lat);
  const lon = Number(r.lon);
  if (Number.isNaN(lat) || Number.isNaN(lon)) return;

  const point = new LatLng(lat, lon);

  // Center map
  const map = this.mapComponent?.map;
  if (map) map.setView(point, Math.max(map.getZoom(), 15), { animate: true });

  // Place/move neutral marker
  this.setSearchMarker(point);

  // Hide results after selection
  this.clearSearch();
}

  async reset() {
    if (!this.onReset || this.isProcessing) return;

    this.isProcessing = true;
    this.loadingMessage = 'Resetting route…';

    try {
      // Ask parent to reset route and return fresh layers/bounds
      const { layers, bounds } = await this.onReset();

      // Clear local editor state
      this.addPointMode = false;
      this.addedPoints = [];
      this.committedStops = [];

      this.baseLayers = [...layers];
      if (bounds) this.routeBounds = bounds;

      // clear markers + neutral marker
      this.addedPoints = [];
      this.committedStops = [];
      this.addPointMode = false;
      this.clearSearchMarker();

      // rebuild all layers
      this.rebuildEditorLayers();

      // Refit map
      requestAnimationFrame(() => {
        const map = this.mapComponent?.map;
        if (!map || !this.routeBounds) return;

        map.invalidateSize();
        map.fitBounds(this.routeBounds, { padding: [50, 50], animate: true });
      });

    } catch (e) {
      console.error('Reset failed', e);
    } finally {
      // small delay avoids leaflet grey flash in modals
      setTimeout(() => (this.isProcessing = false), 120);
    }
  }

private rebuildEditorLayers() {
  const layers: Layer[] = [...this.baseLayers];

  for (const p of this.committedStops) {
    layers.push(
      marker(p, { icon: coloredMarker(MAP_MARKER_COLORS.confirmed) })
    );
  }

  for (const p of this.addedPoints) {
    layers.push(
      marker(p, { icon: coloredMarker(MAP_MARKER_COLORS.added) })
    );
  }

  if (this.searchMarker) {
    layers.push(this.searchMarker);
  }

  this.localLayers = layers;
}

private setSearchMarker(point: LatLng) {
  this.searchMarkerPos = point;

  if (this.searchMarker) {
    this.searchMarker.setLatLng(point);
    this.rebuildEditorLayers();
    return;
  }

  // Neutral marker icon:
  // If you have MAP_MARKER_COLORS.search, use coloredMarker(...) here.
  // Otherwise default Leaflet marker is fine:
  this.searchMarker = marker(point, { icon: coloredMarker(MAP_MARKER_COLORS.added) });

  // Leaflet click -> Ionic action sheet (must run in Angular zone)
  this.searchMarker.on('click', () => {
    this.zone.run(() => this.openSearchMarkerActions());
  });

  this.rebuildEditorLayers();
}

private async openSearchMarkerActions() {
  if (!this.searchMarkerPos || this.isProcessing) return;

  const point = this.searchMarkerPos;

  const sheet = await this.actionSheetCtrl.create({
    header: 'Location',
    buttons: [
      {
        text: 'Add to route',
        icon: 'add-circle-outline',
        handler: () => this.addSearchedPointAsStop(point),
      },
      {
        text: 'Choose as starting point',
        icon: 'flag-outline',
        handler: () => this.chooseSearchedPointAsStart(point),
      },
      {
        text: 'Remove marker',
        role: 'destructive',
        icon: 'trash-outline',
        handler: () => this.clearSearchMarker(),
      },
      {
        text: 'Cancel',
        role: 'cancel',
      }
    ]
  });

  await sheet.present();
}

private async addSearchedPointAsStop(point: LatLng) {
  if (this.isProcessing) return;

  const wasAddMode = this.addPointMode;
  this.addPointMode = true;

  try {
    await this.onPointAdded(point);

    this.clearSearchMarker();

    this.rebuildEditorLayers();
  } finally {
    this.addPointMode = wasAddMode;
  }
}


private async chooseSearchedPointAsStart(point: LatLng) {
  if (!this.onChangeStart || this.isProcessing) return;

  this.isProcessing = true;
  this.loadingMessage = 'Updating start…';

  try {
    const { layers, bounds } = await this.onChangeStart(point);

    // Route changed => clear stops & state
    this.committedStops = [];
    this.addedPoints = [];
    this.addPointMode = false;

    // Clear neutral marker
    this.clearSearchMarker();

    // Update modal view instantly
    this.baseLayers = [...layers];
    this.rebuildEditorLayers();
    if (bounds) this.routeBounds = bounds;

    // Refit
    requestAnimationFrame(() => {
      const map = this.mapComponent?.map;
      if (!map || !this.routeBounds) return;
      map.invalidateSize();
      map.fitBounds(this.routeBounds, { padding: [50, 50], animate: true });
    });
  } catch (e) {
    console.error('Change start failed', e);
  } finally {
    setTimeout(() => (this.isProcessing = false), 120);
  }
}
}
