import {Component, EventEmitter, Input, Output, ViewChild} from '@angular/core';
import {ActionSheetController, IonicModule, ModalController} from '@ionic/angular';
import {CommonModule} from '@angular/common';
import {LatLng, LatLngBounds, Layer, marker} from 'leaflet';
import {MapComponent} from './map.component';
import {coloredMarker, emojiMarker, MAP_MARKER_COLORS} from './map-icon';
import {StopsService} from '../../../services/add-stops.service';
import {ViennaPointDto} from '../../dtos/ViennaPointsDto';
import {SanitarySettingsModalComponent} from './consider-fac/consider-fac.component';

export interface GeoJsonPosition {
  latitude: number;
  longitude: number;
  altitude?: number | null;
}

export interface RouteWithFacilityDefaults {
  originalRoute: GeoJsonPosition[];
  includeToilets: boolean;
  toiletIntervalMeters: number;
  includeFountains: boolean;
  fountainIntervalMeters: number;
  maxFacilityDistance: number;
}

export interface SanitarySettings {
  enabled: boolean;
  includeToilets: boolean;
  toiletIntervalMeters: number;
  includeFountains: boolean;
  fountainIntervalMeters: number;
  maxFacilityDistance: number;
}

@Component({
  standalone: true,
  selector: 'app-map-modal',
  imports: [IonicModule, CommonModule, MapComponent],
  templateUrl: './mapModal.component.html',
  styleUrls: ['./mapModal.component.scss']
})
export class MapModalComponent {
  @Input() layers: any[] = [];
  @Input() routeBounds!: LatLngBounds;
  @Output() confirmPoints = new EventEmitter<LatLng[]>();
  @Output() sanitarySettingsChanged = new EventEmitter<RouteWithFacilityDefaults>();

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

  private baseLayers: Layer[] = [];
  isMapReady = false;

  constructor(
    private modalCtrl: ModalController,
    private actionSheetCtrl: ActionSheetController,
    private stopsService: StopsService
  ) {}

  ngOnInit() {
    this.baseLayers = [...this.layers];
    this.localLayers = [...this.layers];
  }

  // Computed properties for counts
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

  toggleAddPointMode() {
    if (this.addPointMode) {
      this.addedPoints = [];
      this.rebuildLayers();
    }
    this.addPointMode = !this.addPointMode;
  }

  onPointAdded(point: LatLng) {
    this.addedPoints.push(point);
    const m = marker(point, {
      icon: coloredMarker(MAP_MARKER_COLORS.added)
    });
    this.localLayers = [...this.localLayers, m];
  }

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
    const routeConfig: RouteWithFacilityDefaults = {
      originalRoute: this.getRoutePoints(),
      includeToilets: this.sanitarySettings.includeToilets,
      toiletIntervalMeters: this.sanitarySettings.toiletIntervalMeters,
      includeFountains: this.sanitarySettings.includeFountains,
      fountainIntervalMeters: this.sanitarySettings.fountainIntervalMeters,
      maxFacilityDistance: this.sanitarySettings.maxFacilityDistance
    };

    // Emit the configuration so parent component can handle route recalculation
    //this.sanitarySettingsChanged.emit(routeConfig);

    // Optionally show loading indicator
    //this.isLoadingFacilities = true;

    // You can also process it here if you prefer
     this.processRouteWithFacilities(routeConfig);
  }

  getRoutePoints(): GeoJsonPosition[] {
    // Extract route points from base layers
    const routePoints: GeoJsonPosition[] = [];

    // If you have access to the original route data, use that
    // Otherwise, extract from addedPoints or layers
    this.addedPoints.forEach(point => {
      routePoints.push({
        latitude: point.lat,
        longitude: point.lng,
        altitude: null
      });
    });

    return routePoints;
  }

  processRouteWithFacilities(config: RouteWithFacilityDefaults) {
    this.isLoadingFacilities = true;

    this.stopsService.addFacilitiesStops(config).subscribe(
      (updatedRoute) => {
        console.log('Route updated with facilities:', updatedRoute);
        // Update the map with the new route
        //this.updateRouteOnMap(updatedRoute);
        this.isLoadingFacilities = false;
      },
      (error) => {
        console.error('Error processing route with facilities:', error);
        this.isLoadingFacilities = false;
        // Optionally show error toast
        //this.showErrorToast('Failed to add facilities to route');
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
          text: `🚰 Water Fountains (${this.fountainCount})`,
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
      // Transform API response to match expected format
      this.facilities = await this.stopsService.getAllFacilities().toPromise() || [];

      console.log('Facilities loaded:', this.facilities.length);
      this.rebuildLayers();
    } catch (error) {
      console.error('Error loading facilities:', error);
    } finally {
      this.isLoadingFacilities = false;
    }
  }

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
                <strong>${facility.type === 'Toilet' ? '🚻 Toilet' : '🚰 Water Fountain'}</strong><br>
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

    // Add user-added points
    this.addedPoints.forEach(point => {
      const m = marker(point, {
        icon: coloredMarker(MAP_MARKER_COLORS.added)
      });
      layers.push(m);
    });

    this.localLayers = layers;
  }

  ionViewDidEnter() {
    this.centerRouteInitially();
  }

  private centerRouteInitially() {
    requestAnimationFrame(() => {
      const map = this.mapComponent?.map;
      if (!map || !this.routeBounds) return;

      map.invalidateSize();

      map.fitBounds(this.routeBounds, {
        padding: [50, 50],
        animate: false
      });

      setTimeout(() => {
        this.isMapReady = true;
      }, 50);
    });
  }

  centerRoute() {
    const map = this.mapComponent?.map;
    if (!map || !this.routeBounds) return;

    map.fitBounds(this.routeBounds, {
      padding: [50, 50],
      animate: true
    });
  }

  close() {
    this.modalCtrl.dismiss({
      addedPoints: this.addedPoints
    });
  }

  confirm() {
    this.confirmPoints.emit([...this.addedPoints]);
    this.addedPoints = [];
    this.addPointMode = false;
    this.rebuildLayers();
  }

  // Deprecated - use openSanitarySettings() instead
  considerSanitariFacilities() {
    this.openSanitarySettings();
  }
}
