import { Component, Input, ViewChild } from '@angular/core';
import { IonicModule, ModalController } from '@ionic/angular';
import { CommonModule } from '@angular/common';
import { LatLngBounds } from 'leaflet';
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

  constructor(private modalCtrl: ModalController) {}

  /** Ionic lifecycle: modal is fully visible */
  ionViewDidEnter() {
    this.centerRouteImmediately();
  }

  private centerRouteImmediately() {
    requestAnimationFrame(() => {
      const map = this.mapComponent?.map;
      if (!map || !this.routeBounds) return;

      map.invalidateSize();

      map.fitBounds(this.routeBounds, {
        padding: [50, 50],
        animate: false   // instant on open
      });
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
    this.modalCtrl.dismiss();
  }
}
