import { Component, Input, ViewChild } from '@angular/core';
import { IonicModule, ModalController } from '@ionic/angular';
import { CommonModule } from '@angular/common';
import { LatLngBounds, LatLng, Layer, marker } from 'leaflet';
import { MapComponent } from './map.component';
import { MAP_MARKER_COLORS, coloredMarker } from './map-icon';

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
  @Input() onConfirm!: (points: LatLng[]) => Promise<{ layers: Layer[]; bounds: LatLngBounds | null }>;

  @ViewChild(MapComponent) mapComponent!: MapComponent;

  addPointMode = false;
  addedPoints: LatLng[] = [];
  localLayers: Layer[] = [];
  private baseLayers: Layer[] = [];

  isMapReady = false; // initial "map is centered, show it"
  isProcessing = false; // confirming points (backend call)
  loadingMessage = 'Loading map…';

  constructor(private modalCtrl: ModalController) {}

  ngOnInit() {
    this.baseLayers = [...this.layers];
    this.localLayers = [...this.layers];
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

      map.fitBounds(this.routeBounds, {
        padding: [50, 50],
        animate: false
      });

      // reveal map AFTER centering
      setTimeout(() => {
        this.isMapReady = true;
      }, 80);
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

  toggleAddPointMode() {
    // cancel -> remove unconfirmed points
    if (this.addPointMode) {
      this.addedPoints = [];
      this.localLayers = [...this.baseLayers];
    }

    this.addPointMode = !this.addPointMode;
  }

  onPointAdded(point: LatLng) {
    if (!this.addPointMode || this.isProcessing) return;

    this.addedPoints.push(point);

    const m = marker(point, {
      icon: coloredMarker(MAP_MARKER_COLORS.added)
    });

    this.localLayers = [...this.localLayers, m];
  }

  close() {
    this.modalCtrl.dismiss({
      addedPoints: this.addedPoints
    });
  }

  async confirm() {
    if (!this.onConfirm || this.addedPoints.length === 0 || this.isProcessing) return;

    const points = [...this.addedPoints];

    this.isProcessing = true;
    this.loadingMessage = 'Recalculating route…';

    try {
      const { layers, bounds } = await this.onConfirm(points);

      // update modal immediately
      this.baseLayers = [...layers];
      this.localLayers = [...layers];
      if (bounds) this.routeBounds = bounds;

      // reset editor state
      this.addedPoints = [];
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

        // small delay to avoid grey tile flash
        setTimeout(() => {
          this.isProcessing = false;
        }, 120);
      });

    } catch (e) {
      console.error('Confirm failed', e);
      this.isProcessing = false;
    }
  }
}
