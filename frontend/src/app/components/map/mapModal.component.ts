import { Component, Input, ViewChild } from '@angular/core';
import { IonicModule, ModalController } from '@ionic/angular';
import { CommonModule } from '@angular/common';
import { LatLngBounds, LatLng, Layer, marker } from 'leaflet';
import { MapComponent } from './map.component';

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

  @ViewChild(MapComponent) mapComponent!: MapComponent;

  addPointMode = false;
  addedPoints: LatLng[] = [];
  localLayers: Layer[] = [];
  private baseLayers: Layer[] = [];


  constructor(private modalCtrl: ModalController) {}

  ngOnInit() {
    // immutable snapshot of initial layers
    this.baseLayers = [...this.layers];
    this.localLayers = [...this.layers];
  }


  toggleAddPointMode() {
    if (this.addPointMode) {
      // CANCEL → remove unconfirmed points
      this.addedPoints = [];
      this.localLayers = [...this.baseLayers];
    }

    this.addPointMode = !this.addPointMode;
  }

  onPointAdded(point: LatLng) {
    this.addedPoints.push(point);

    const m = marker(point);
    this.localLayers = [...this.localLayers, m];
  }

  ionViewDidEnter() {
    this.centerRouteInitially();
  }

  isMapReady = false;

  private centerRouteInitially() {
    requestAnimationFrame(() => {
      const map = this.mapComponent?.map;
      if (!map || !this.routeBounds) return;

      map.invalidateSize();

      // Fit route immediately
      map.fitBounds(this.routeBounds, {
        padding: [50, 50],
        animate: false
      });

      // Reveal map AFTER centering
      setTimeout(() => {
        this.isMapReady = true;
      }, 50); // tiny delay ensures tiles settle
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
}
