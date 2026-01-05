import { Component, Input, ViewChild } from '@angular/core';
import { IonicModule, ModalController, ToastController } from '@ionic/angular';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms'
import { LatLngBounds, LatLng, Layer, marker } from 'leaflet';
import { MapComponent } from './map.component';
import { MAP_MARKER_COLORS, coloredMarker } from './map-icon';

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

  @ViewChild(MapComponent) mapComponent!: MapComponent;

  mode: AddStopsMode = 'KEEP_SHAPE';
  addPointMode = false;

  addedPoints: LatLng[] = []; // pending points (not confirmed yet)
  localLayers: Layer[] = [];
  private baseLayers: Layer[] = [];

  isMapReady = false;
  isProcessing = false;
  loadingMessage = 'Loading map…';

  constructor(
    private modalCtrl: ModalController,
    private toastCtrl: ToastController
  ) {}

  ngOnInit() {
    this.baseLayers = [...this.layers];

    const stopMarkers = this.committedStops.map(p =>
      marker(p, { icon: coloredMarker(MAP_MARKER_COLORS.added) })
    );

    this.baseLayers = [...this.baseLayers, ...stopMarkers];
    this.localLayers = [...this.baseLayers];
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
      this.localLayers = [...this.baseLayers];
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

    const m = marker(point, { icon: coloredMarker(MAP_MARKER_COLORS.added) });
    this.localLayers = [...this.localLayers, m];

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

      // update layers
      this.baseLayers = [...layers];
      this.localLayers = [...layers];
      if (bounds) this.routeBounds = bounds;

      // reset pending editor state
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
