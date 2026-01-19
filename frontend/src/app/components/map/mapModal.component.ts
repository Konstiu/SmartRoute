import { Component, Input, ViewChild } from '@angular/core';
import { ActionSheetController, IonicModule, ModalController, ToastController } from '@ionic/angular';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LatLngBounds, LatLng, latLng, Layer, marker, Marker } from 'leaflet';
import { NgZone } from '@angular/core';

import { MapComponent } from './map.component';
import { MAP_MARKER_COLORS, coloredMarker } from './map-icon';
import { GeocodingService, GeocodeResult } from 'src/services/geocoding.service';

type AddStopsMode = 'KEEP_SHAPE' | 'KEEP_LENGTH';
type InitialMode = 'SET_START' | 'EDIT_STOPS';

@Component({
  standalone: true,
  selector: 'app-map-modal',
  imports: [IonicModule, CommonModule, MapComponent, FormsModule],
  templateUrl: './mapModal.component.html',
  styleUrls: ['./mapModal.component.scss']
})
export class MapModalComponent {
  private static readonly MAX_STOPS = 2;

  @Input() layers: Layer[] = [];
  @Input() routeBounds!: LatLngBounds;
  @Input() committedStops: LatLng[] = [];
  @Input() originalLayers!: Layer[];
  @Input() originalBounds!: LatLngBounds;
  @Input() initialMode: InitialMode = 'EDIT_STOPS';

  @Input() onConfirm!: ( points: LatLng[], mode: AddStopsMode ) => Promise<{ layers: Layer[]; bounds: LatLngBounds | null }>;
  @Input() onReset?: () => Promise<{ layers: Layer[]; bounds: LatLngBounds | null }>;
  @Input() onChangeStart?: (start: LatLng) => Promise<{ layers: Layer[]; bounds: LatLngBounds | null }>;

  @ViewChild(MapComponent) mapComponent!: MapComponent;

  addPointMode = false;
  localLayers: Layer[] = [];
  private baseLayers: Layer[] = [];

  isMapReady = false;
  isProcessing = false;
  loadingMessage = 'Loading map…';

  searchQuery = '';
  searchResults: GeocodeResult[] = [];
  isSearching = false;
  private searchTimer?: any;

  searchMarker: Marker | null = null;
  private searchMarkerPos: LatLng | null = null;

  private readonly fallbackCenter = latLng(48.208169, 16.373817);
  private readonly fallbackZoom = 10;

  constructor(
    private modalCtrl: ModalController,
    private toastCtrl: ToastController,
    private geocoding: GeocodingService,
    private actionSheetCtrl: ActionSheetController,
    private zone: NgZone
  ) {}

  // =====================================================
  // Computed UI helpers
  // =====================================================

  private get isSetStartMode(): boolean {
    return this.initialMode === 'SET_START';
  }

  get showStopFabs(): boolean {
    return !this.isSetStartMode && !this.isProcessing;
  }

  get totalStops(): number {
    return this.committedStops?.length ?? 0;
  }

  get remainingStops(): number {
    return Math.max(0, MapModalComponent.MAX_STOPS - this.totalStops);
  }

  // =====================================================
  // Lifecycle
  // =====================================================

  ngOnInit() {
    this.baseLayers = [...this.layers];

    if (this.isSetStartMode) {
      this.addPointMode = true; // allow click/tap immediately
    }

    this.rebuildEditorLayers();
  }

  ionViewDidEnter() {
    this.centerRouteInitially();
  }

  // =====================================================
  // Map camera
  // =====================================================

  private centerRouteInitially() {
    requestAnimationFrame(() => {
      const map = this.mapComponent?.map;
      if (!map) return;

      map.invalidateSize(true);

      if (this.routeBounds) {
        this.isMapReady = false;

        map.fitBounds(this.routeBounds, { padding: [50, 50], animate: false });

        setTimeout(() => {
          this.isMapReady = true;
        }, 80);
      } else {
        map.setView(this.fallbackCenter, this.fallbackZoom, { animate: false });
        this.isMapReady = true;
      }
    });
  }

  centerRoute() {
    const map = this.mapComponent?.map;
    if (!map || !this.routeBounds) return;
    map.fitBounds(this.routeBounds, { padding: [50, 50], animate: true });
  }

  private refitToBounds() {
    requestAnimationFrame(() => {
      const map = this.mapComponent?.map;
      if (!map) return;

      map.invalidateSize(true);

      if (this.routeBounds) {
        map.fitBounds(this.routeBounds, { padding: [50, 50], animate: true });
      }
    });
  }

  // =====================================================
  // Editing modes
  // =====================================================

  async toggleAddPointMode() {
    if (!this.addPointMode && this.remainingStops === 0) {
      await this.showMaxStopsToast();
      return;
    }

    // Leaving add mode cancels pending marker
    if (this.addPointMode) {
      this.clearSearchMarker();
    }

    this.addPointMode = !this.addPointMode;
    this.rebuildEditorLayers();
  }

  async onPointAdded(point: LatLng) {
    if (this.isProcessing) return;

    // In EDIT_STOPS mode: ignore clicks unless explicitly in addPointMode
    if (!this.isSetStartMode && !this.addPointMode) return;

    this.setSearchMarker(point);

    // SET_START mode: apply immediately
    if (this.isSetStartMode) {
      await this.chooseSearchedPointAsStart(point);
    }
  }

  // =====================================================
  // Search
  // =====================================================

  onSearchInput(ev: any) {
    const value = (ev?.target?.value ?? '').toString();
    this.searchQuery = value;

    clearTimeout(this.searchTimer);

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

    const map = this.mapComponent?.map;
    if (map) map.setView(point, Math.max(map.getZoom(), 15), { animate: true });

    this.clearSearch();
    this.setSearchMarker(point);

    if (this.isSetStartMode) {
      await this.chooseSearchedPointAsStart(point);
    }
  }

  // =====================================================
  // Marker / layers
  // =====================================================

  private rebuildEditorLayers() {
    const layers: Layer[] = [...this.baseLayers];

    for (const p of this.committedStops) {
      layers.push(marker(p, { icon: coloredMarker(MAP_MARKER_COLORS.confirmed) }));
    }

    if (this.searchMarker) layers.push(this.searchMarker);

    this.localLayers = layers;
  }

  private setSearchMarker(point: LatLng) {
    this.searchMarkerPos = point;

    if (!this.searchMarker) {
      this.searchMarker = marker(point, { icon: coloredMarker(MAP_MARKER_COLORS.added) });

      this.searchMarker.on('click', () => {
        this.zone.run(() => this.openSearchMarkerActions());
      });
    } else {
      this.searchMarker.setLatLng(point);
    }

    this.rebuildEditorLayers();
  }

  private clearSearchMarker() {
    this.searchMarker = null;
    this.searchMarkerPos = null;
    this.rebuildEditorLayers();
  }

  // =====================================================
  // Actions
  // =====================================================

  close() {
    this.modalCtrl.dismiss({ committedStops: this.committedStops });
  }

  async reset() {
    if (!this.onReset || this.isProcessing) return;

    this.isProcessing = true;

    try {
      const { layers, bounds } = await this.onReset();

      // Parent reset => clear local stop edits
      this.addPointMode = false;
      this.committedStops = [];
      this.clearSearchMarker();

      this.baseLayers = [...layers];
      if (bounds) this.routeBounds = bounds;

      this.rebuildEditorLayers();
      this.refitToBounds();
    } catch (e) {
      console.error('Reset failed', e);
      await this.showToast('Reset failed.');
    } finally {
      setTimeout(() => (this.isProcessing = false), 120);
    }
  }

  private async openSearchMarkerActions() {
    if (!this.searchMarkerPos || this.isProcessing) return;

    const point = this.searchMarkerPos;
    const atLimit = this.committedStops.length >= MapModalComponent.MAX_STOPS;

    const buttons: any[] = [];

    if (!this.isSetStartMode) {
      buttons.push(
        {
          text: 'Add to route (Fast insert)',
          icon: 'flash-outline',
          handler: () => this.addPointToRoute(point, 'KEEP_SHAPE'),
          cssClass: atLimit ? 'action-disabled' : ''
        },
        {
          text: 'Add to route (Keep length)',
          icon: 'resize-outline',
          handler: () => this.addPointToRoute(point, 'KEEP_LENGTH'),
          cssClass: atLimit ? 'action-disabled' : ''
        }
      );
    }

    buttons.push(
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
    );

    const sheet = await this.actionSheetCtrl.create({
      header: 'Location',
      buttons
    });

    await sheet.present();
  }

  private async addPointToRoute(point: LatLng, mode: AddStopsMode) {
    if (!this.onConfirm || this.isProcessing) return;

    if (this.committedStops.length >= MapModalComponent.MAX_STOPS) {
      await this.showMaxStopsToast();
      return;
    }

    this.isProcessing = true;
    this.loadingMessage = 'Recalculating route…';
    await this.nextPaint();

    try {
      const { layers, bounds } = await this.onConfirm([point], mode);

      // Commit stop in modal state
      this.committedStops = [...this.committedStops, point];

      // Update route layers from parent
      this.baseLayers = [...layers];
      if (bounds) this.routeBounds = bounds;

      this.clearSearchMarker();
      this.rebuildEditorLayers();
      this.refitToBounds();
    } catch (e: any) {
      const code = e?.error?.code;

      if (code === 'STOP_TOO_FAR_FROM_ROUTE') {
        const max = e.error.details?.maxAllowedMeters;
        const actual = e.error.details?.actualMeters;

        await this.showToast(
          `Point too far from route (allowed ${Math.round(max)}m, got ${Math.round(actual)}m).`
        );
        return;
      }

      console.error('Add to route failed', e);
      await this.showToast('Could not add point to route.');
    } finally {
      setTimeout(() => (this.isProcessing = false), 120);
      if (!this.isSetStartMode) this.loadingMessage = '';
    }
  }

  private async chooseSearchedPointAsStart(point: LatLng) {
    if (!this.onChangeStart || this.isProcessing) return;

    this.isProcessing = true;

    try {
      const { layers, bounds } = await this.onChangeStart(point);

      // Route changed => clear stops & state
      this.committedStops = [];
      this.addPointMode = false;
      this.clearSearchMarker();

      // Update modal view
      this.baseLayers = [...layers];
      if (bounds) this.routeBounds = bounds;

      // Exit start-selection mode after success
      this.initialMode = 'EDIT_STOPS';

      this.rebuildEditorLayers();
      this.refitToBounds();
    } catch (e) {
      console.error('Change start failed', e);
      await this.showToast('Could not update start.');
    } finally {
      setTimeout(() => (this.isProcessing = false), 120);
    }
  }

  // =====================================================
  // Misc UI helpers
  // =====================================================

  cancelPending() {
    this.clearSearchMarker();
    this.addPointMode = false;
    this.rebuildEditorLayers();
  }

  get isEditingStops(): boolean {
    return this.addPointMode;
  }

  onAddCancelClick() {
    if (this.isEditingStops) this.cancelPending();
    else void this.toggleAddPointMode();
  }

  confirm() {
    this.addPointMode = false;
  }

  private async showMaxStopsToast() {
    const toast = await this.toastCtrl.create({
      message: `You can add at most ${MapModalComponent.MAX_STOPS} stops per route.`,
      duration: 1800,
      position: 'bottom'
    });
    await toast.present();
  }

  private async showToast(message: string) {
    const toast = await this.toastCtrl.create({
      message,
      duration: 3000,
      position: 'top',
      color: 'warning',
      cssClass: 'toast-above-modal',
      buttons: [{ text: 'OK', role: 'cancel' }]
    });
    await toast.present();
  }

  private async nextPaint(): Promise<void> {
    await new Promise<void>(resolve => requestAnimationFrame(() => resolve()));
  }
}
