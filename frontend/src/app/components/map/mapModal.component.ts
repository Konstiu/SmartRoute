import { Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { ActionSheetController, IonicModule, ModalController, ToastController } from '@ionic/angular';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LatLng, LatLngBounds, Layer, marker, polyline as leafletPolyline, Marker, latLng } from 'leaflet';
import { NgZone } from '@angular/core';
import { MapComponent } from './map.component';
import { coloredMarker, emojiMarker, MAP_MARKER_COLORS } from './map-icon';
import { StopsService } from '../../../services/add-stops.service';
import { ViennaPointDto } from '../../dtos/ViennaPointsDto';
import { SanitarySettings, SanitarySettingsModalComponent } from './consider-fac/consider-fac.component';
import { RouteWithFacilityDefaults } from "../../dtos/RouteWithFacilitiesDto";
import { GeoJsonPosition } from "../../dtos/add-stops";
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

  @Input() onConfirm!: (points: LatLng[], mode: AddStopsMode) => Promise<{ layers: Layer[]; bounds: LatLngBounds | null }>;
  @Input() onReset?: () => Promise<{ layers: Layer[]; bounds: LatLngBounds | null }>;
  @Input() onChangeStart?: (start: LatLng) => Promise<{ layers: Layer[]; bounds: LatLngBounds | null }>;

  @Output() confirmPoints = new EventEmitter<LatLng[]>();
  @Output() sanitarySettingsChanged = new EventEmitter<RouteWithFacilityDefaults>();
  @Output() routeUpdated = new EventEmitter<string>();


  @ViewChild(MapComponent) mapComponent!: MapComponent;

  addPointMode = false;
  addedPoints: LatLng[] = [];
  localLayers: Layer[] = [];

  // Facilities
  showFacilities = false;
  showToilets = false;
  showFountains = false;
  facilities: ViennaPointDto[] = [];
  facilityLayers: Layer[] = [];
  isLoadingFacilities = false;

  // Sanitary facilities settings
  sanitarySettings: SanitarySettings = {
    enabled: false,
    includeToilets: true,
    toiletIntervalMeters: 5000,
    includeFountains: true,
    fountainIntervalMeters: 3000,
    maxFacilityDistance: 200
  };

  // Search functionality
  searchQuery = '';
  searchResults: GeocodeResult[] = [];
  isSearching = false;
  private searchTimer?: any;
  searchMarker: Marker | null = null;
  private searchMarkerPos: LatLng | null = null;

  private baseLayers: Layer[] = [];
  isMapReady = false;
  isProcessing = false;
  loadingMessage = 'Loading map…';

  private readonly fallbackCenter = latLng(48.208169, 16.373817);
  private readonly fallbackZoom = 10;

  constructor(
    private modalCtrl: ModalController,
    private actionSheetCtrl: ActionSheetController,
    private stopsService: StopsService,
    private toastCtrl: ToastController,
    private geocoding: GeocodingService,
    private zone: NgZone
  ) { }

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
    return (this.committedStops?.length ?? 0) + this.addedPoints.length;
  }

  get remainingStops(): number {
    return Math.max(0, MapModalComponent.MAX_STOPS - this.totalStops);
  }

  get toiletCount(): number {
    return this.facilities.filter(f => f.type === 'Toilet').length;
  }

  get fountainCount(): number {
    return this.facilities.filter(f => f.type === 'Fountain').length;
  }

  get visibleFacilitiesCount(): number {
    let count = 0;
    if (this.showToilets) count += this.toiletCount;
    if (this.showFountains) count += this.fountainCount;
    return count;
  }

  get isEditingStops(): boolean {
    return this.addPointMode;
  }

  // =====================================================
  // Lifecycle
  // =====================================================

  ngOnInit() {
    this.baseLayers = [...this.layers];
    const firstPolyline = this.baseLayers.find(l => (l as any).getLatLngs);
    if (firstPolyline) (firstPolyline as any).__isRouteLayer = true;

    if (this.isSetStartMode) {
      this.addPointMode = true; // allow click/tap immediately
    }

    this.rebuildLayers();
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
      this.addedPoints = [];
      this.clearSearchMarker();
    }

    this.addPointMode = !this.addPointMode;
    this.rebuildLayers();
  }

  async onPointAdded(point: LatLng) {
    if (this.isProcessing) return;

    // In EDIT_STOPS mode: ignore clicks unless explicitly in addPointMode
    if (!this.isSetStartMode && !this.addPointMode) return;

    // SET_START mode: apply immediately
    if (this.isSetStartMode) {
      this.setSearchMarker(point);
      await this.chooseSearchedPointAsStart(point);
    } else {
      // EDIT_STOPS mode: add to pending points
      this.addedPoints.push(point);
      this.setSearchMarker(point);
      this.rebuildLayers();
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

  private rebuildLayers() {
    const layers: Layer[] = [...this.baseLayers];

    // Add facility markers if enabled
    if (this.showFacilities) {
      this.facilities.forEach((facility, index) => {
        const shouldShow =
          (facility.type === 'Toilet' && this.showToilets) ||
          (facility.type === 'Fountain' && this.showFountains);

        if (shouldShow) {
          // Validate coordinates exist
          if (!facility.coordinate ||
            facility.coordinate.latitude === undefined ||
            facility.coordinate.longitude === undefined) {
            console.warn(`Facility ${index} has invalid coordinates:`, facility);
            return;
          }

          const lat = Number(facility.coordinate.latitude);
          const lon = Number(facility.coordinate.longitude);

          if (isNaN(lat) || isNaN(lon)) {
            console.warn(`Facility ${index} has non-numeric coordinates:`, facility);
            return;
          }

          try {
            // Use emoji markers
            const facilityType = facility.type.toLowerCase() as 'toilet' | 'fountain';
            const facilityMarkerInstance = marker(
              [lat, lon],
              { icon: emojiMarker(facilityType) }
            );

            // Add popup with facility info
            facilityMarkerInstance.bindPopup(`
              <div style="text-align: center;">
                <strong>${facility.type === 'Toilet' ? '🚻 Toilet' : '💧 Water Fountain'}</strong><br>
                <small style="color: #999;">Lat: ${lat.toFixed(4)}, Lon: ${lon.toFixed(4)}</small>
              </div>
            `);

            layers.push(facilityMarkerInstance);
          } catch (error) {
            console.error(`Error creating marker for facility ${index}:`, error, facility);
          }
        }
      });
    }

    // Add committed stops
    for (const p of this.committedStops) {
      layers.push(marker(p, { icon: coloredMarker(MAP_MARKER_COLORS.confirmed) }));
    }

    // Add user-added points
    this.addedPoints.forEach(point => {
      const m = marker(point, {
        icon: coloredMarker(MAP_MARKER_COLORS.added)
      });
      layers.push(m);
    });

    // Add search marker
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

    this.rebuildLayers();
  }

  private clearSearchMarker() {
    this.searchMarker = null;
    this.searchMarkerPos = null;
    this.rebuildLayers();
  }

  // =====================================================
  // Actions
  // =====================================================

  close() {
    this.modalCtrl.dismiss({
      addedPoints: this.addedPoints,
      committedStops: this.committedStops
    });
  }

  confirm() {
    this.confirmPoints.emit([...this.addedPoints]);
    this.addedPoints = [];
    this.addPointMode = false;
    this.rebuildLayers();
  }

  async reset() {
    if (!this.onReset || this.isProcessing) return;

    this.isProcessing = true;

    try {
      const { layers, bounds } = await this.onReset();

      // Parent reset => clear local stop edits
      this.addPointMode = false;
      this.committedStops = [];
      this.addedPoints = [];
      this.clearSearchMarker();

      this.baseLayers = [...layers];
      if (bounds) this.routeBounds = bounds;

      this.rebuildLayers();
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
      this.rebuildLayers();
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
      this.addedPoints = [];
      this.addPointMode = false;
      this.clearSearchMarker();

      // Update modal view
      this.baseLayers = [...layers];
      if (bounds) this.routeBounds = bounds;

      // Exit start-selection mode after success
      this.initialMode = 'EDIT_STOPS';

      this.rebuildLayers();
      this.refitToBounds();
    } catch (e) {
      console.error('Change start failed', e);
      await this.showToast('Could not update start.');
    } finally {
      setTimeout(() => (this.isProcessing = false), 120);
    }
  }

  // =====================================================
  // Sanitary Facilities
  // =====================================================

  async openSanitarySettings() {
    const modal = await this.modalCtrl.create({
      component: SanitarySettingsModalComponent,
      componentProps: {
        settings: { ...this.sanitarySettings }
      }
    });

    await modal.present();

    const { data, role } = await modal.onWillDismiss();

    if (role === 'confirm' && data) {
      this.sanitarySettings = data;

      // If settings are enabled, emit the configuration
      if (this.sanitarySettings.enabled) {
        this.applySanitaryFacilities();
      }
    }
  }

  applySanitaryFacilities() {
    if (!this.sanitarySettings.enabled) {
      return;
    }

    // Build the route configuration
    const route = this.encodePolylineFromGeoJsonPositions(this.getRoutePoints(), 5);
    console.log(route);

    const routeConfig: RouteWithFacilityDefaults = {
      originalRoute: route,
      includeToilets: this.sanitarySettings.includeToilets,
      toiletIntervalMeters: this.sanitarySettings.toiletIntervalMeters,
      includeFountains: this.sanitarySettings.includeFountains,
      fountainIntervalMeters: this.sanitarySettings.fountainIntervalMeters,
      maxFacilityDistance: this.sanitarySettings.maxFacilityDistance
    };

    this.processRouteWithFacilities(routeConfig);
  }

  getRoutePoints(): GeoJsonPosition[] {
    const latlngs = this.extractRouteLatLngsFromLayers();

    return latlngs.map(p => ({
      latitude: p.lat,
      longitude: p.lng,
      altitude: null
    }));
  }

  processRouteWithFacilities(config: RouteWithFacilityDefaults) {
    this.isLoadingFacilities = true;

    this.stopsService.addFacilitiesStops(config).subscribe(
      (updatedRoute) => {
        this.updateRouteOnMap(updatedRoute.polyline);
        this.routeUpdated.emit(updatedRoute.polyline); // ← Emit to parent
        this.isLoadingFacilities = false;
      },
      (error) => {
        console.error('Error processing route with facilities:', error);
        this.isLoadingFacilities = false;
      }
    );
  }

  async openFacilitiesMenu() {
    // Load facilities if not loaded yet
    if (this.facilities.length === 0) {
      await this.loadFacilities();
    }

    // Determine current selection
    let currentSelection = 'None';
    if (this.showToilets && this.showFountains) {
      currentSelection = 'Both';
    } else if (this.showToilets) {
      currentSelection = 'Toilets only';
    } else if (this.showFountains) {
      currentSelection = 'Fountains only';
    }

    const actionSheet = await this.actionSheetCtrl.create({
      header: 'Show Facilities',
      subHeader: `Current: ${currentSelection}`,
      buttons: [
        {
          text: `🚻 Toilets (${this.toiletCount})`,
          icon: this.showToilets && !this.showFountains ? 'checkmark-circle' : 'ellipse-outline',
          handler: () => {
            this.setFacilityFilter('toilets');
          }
        },
        {
          text: `💧 Water Fountains (${this.fountainCount})`,
          icon: this.showFountains && !this.showToilets ? 'checkmark-circle' : 'ellipse-outline',
          handler: () => {
            this.setFacilityFilter('fountains');
          }
        },
        {
          text: `Both (${this.toiletCount + this.fountainCount})`,
          icon: this.showToilets && this.showFountains ? 'checkmark-circle' : 'ellipse-outline',
          handler: () => {
            this.setFacilityFilter('both');
          }
        },
        {
          text: 'None',
          icon: !this.showToilets && !this.showFountains ? 'checkmark-circle' : 'ellipse-outline',
          handler: () => {
            this.setFacilityFilter('none');
          }
        },
        {
          text: 'Cancel',
          role: 'cancel',
          icon: 'close'
        }
      ]
    });

    await actionSheet.present();
  }

  setFacilityFilter(filter: 'none' | 'toilets' | 'fountains' | 'both') {
    // Set filters based on selection
    switch (filter) {
      case 'none':
        this.showToilets = false;
        this.showFountains = false;
        this.showFacilities = false;
        break;
      case 'toilets':
        this.showToilets = true;
        this.showFountains = false;
        this.showFacilities = true;
        break;
      case 'fountains':
        this.showToilets = false;
        this.showFountains = true;
        this.showFacilities = true;
        break;
      case 'both':
        this.showToilets = true;
        this.showFountains = true;
        this.showFacilities = true;
        break;
    }

    this.rebuildLayers();
  }

  async loadFacilities() {
    this.isLoadingFacilities = true;
    try {
      this.facilities = await this.stopsService.getAllFacilities().toPromise() || [];
      console.log('Facilities loaded:', this.facilities.length);
      this.rebuildLayers();
    } catch (error) {
      console.error('Error loading facilities:', error);
    } finally {
      this.isLoadingFacilities = false;
    }
  }

  // =====================================================
  // Misc UI helpers
  // =====================================================

  cancelPending() {
    this.clearSearchMarker();
    this.addedPoints = [];
    this.addPointMode = false;
    this.rebuildLayers();
  }

  onAddCancelClick() {
    if (this.isEditingStops) this.cancelPending();
    else void this.toggleAddPointMode();
  }

  // Deprecated - use openSanitarySettings() instead
  considerSanitariFacilities() {
    this.openSanitarySettings();
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

  // =====================================================
  // Route extraction & encoding
  // =====================================================

  private extractRouteLatLngsFromLayers(): LatLng[] {
    for (const l of this.baseLayers) {
      // Case A: Leaflet Polyline (common)
      if ((l as any).getLatLngs) {
        const latlngs = (l as any).getLatLngs();

        // Could be LatLng[] or nested arrays (MultiPolyline)
        const flat = this.flattenLatLngs(latlngs);
        if (flat.length > 1) return flat;
      }

      // Case B: Leaflet GeoJSON layer (also common)
      if ((l as any).toGeoJSON) {
        const gj = (l as any).toGeoJSON();
        const coords = this.geoJsonLineStringToLatLngs(gj);
        if (coords.length > 1) return coords;
      }
    }
    return [];
  }

  private flattenLatLngs(input: any): LatLng[] {
    if (!Array.isArray(input)) return [];

    // Already flat LatLng[]
    if (input.length && input[0] instanceof LatLng) {
      return input as LatLng[];
    }

    // Replace flatMap with reduce
    return input.reduce((acc: LatLng[], x: any) => {
      acc.push(...this.flattenLatLngs(x));
      return acc;
    }, []);
  }

  private geoJsonLineStringToLatLngs(gj: any): LatLng[] {
    // supports Feature / FeatureCollection, LineString / MultiLineString
    const features = gj?.type === 'FeatureCollection' ? gj.features : [gj?.type === 'Feature' ? gj : null].filter(Boolean);

    const out: LatLng[] = [];
    for (const f of features) {
      const g = f.geometry ?? f;
      if (!g) continue;

      if (g.type === 'LineString') {
        for (const [lon, lat] of g.coordinates) out.push(new LatLng(lat, lon));
      } else if (g.type === 'MultiLineString') {
        for (const line of g.coordinates) {
          for (const [lon, lat] of line) out.push(new LatLng(lat, lon));
        }
      }
    }
    return out;
  }

  private encodePolylineFromGeoJsonPositions(points: GeoJsonPosition[], precision = 6): string {
    const factor = Math.pow(10, precision);
    let out = '';
    let prevLat = 0;
    let prevLng = 0;

    for (const p of points) {
      const lat = Math.round(p.latitude * factor);
      const lng = Math.round(p.longitude * factor);

      out += this.encodeSignedVarint(lat - prevLat);
      out += this.encodeSignedVarint(lng - prevLng);

      prevLat = lat;
      prevLng = lng;
    }

    return out;
  }

  private encodeSignedVarint(num: number): string {
    let s = num << 1;
    if (num < 0) s = ~s;
    return this.encodeUnsignedVarint(s);
  }

  private encodeUnsignedVarint(num: number): string {
    let out = '';
    while (num >= 0x20) {
      out += String.fromCharCode((0x20 | (num & 0x1f)) + 63);
      num >>= 5;
    }
    out += String.fromCharCode(num + 63);
    return out;
  }

  private updateRouteOnMap(encodedPolyline: string) {
    // decode encoded polyline -> LatLng[]
    const latlngs = this.decodePolylineToLatLngs(encodedPolyline, 6);
    if (latlngs.length < 2) return;
    this.setRouteLayer(latlngs);
  }

  private setRouteLayer(latlngs: LatLng[]) {
    const newRoute = leafletPolyline(latlngs);
    (newRoute as any).__isRouteLayer = true;

    // replace old route if exists
    const idx = this.baseLayers.findIndex(l => (l as any).__isRouteLayer);
    if (idx >= 0) {
      this.baseLayers[idx] = newRoute;
    } else {
      // fallback: replace first polyline-like layer
      const fallback = this.baseLayers.findIndex(l => typeof (l as any).getLatLngs === 'function');
      if (fallback >= 0) this.baseLayers[fallback] = newRoute;
      else this.baseLayers.unshift(newRoute);
    }

    // now rebuild the final rendered layer list
    this.rebuildLayers();

    // update bounds and recenter
    this.routeBounds = new LatLngBounds(latlngs);
    this.centerRoute();
  }

  private decodePolylineToLatLngs(poly: string, precision = 6): LatLng[] {
    const factor = Math.pow(10, precision);
    let index = 0;
    let lat = 0;
    let lng = 0;
    const coords: LatLng[] = [];

    while (index < poly.length) {
      const dLat = this.decodeSignedVarint(poly, index);
      index = dLat.nextIndex;
      lat += dLat.value;

      const dLng = this.decodeSignedVarint(poly, index);
      index = dLng.nextIndex;
      lng += dLng.value;

      coords.push(new LatLng(lat / factor, lng / factor));
    }
    return coords;
  }

  private decodeSignedVarint(str: string, start: number): { value: number; nextIndex: number } {
    const r = this.decodeUnsignedVarint(str, start);
    let v = r.value;
    const neg = v & 1;
    v >>= 1;
    return { value: neg ? ~v : v, nextIndex: r.nextIndex };
  }

  private decodeUnsignedVarint(str: string, start: number): { value: number; nextIndex: number } {
    let result = 0;
    let shift = 0;
    let index = start;

    while (index < str.length) {
      const b = str.charCodeAt(index++) - 63;
      result |= (b & 0x1f) << shift;
      shift += 5;
      if ((b & 0x20) === 0) break;
    }

    return { value: result, nextIndex: index };
  }
}
