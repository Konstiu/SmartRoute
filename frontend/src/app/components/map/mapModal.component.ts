import { Component, Input } from '@angular/core';
import { IonicModule, ModalController } from '@ionic/angular';
import { CommonModule } from '@angular/common';
import { Layer } from 'leaflet';
import { MapComponent } from '../map/map.component';

@Component({
  standalone: true,
  selector: 'app-map-modal',
  imports: [IonicModule, CommonModule, MapComponent],
  templateUrl: './mapModal.component.html',
  styleUrls: ['./mapModal.component.scss']
})
export class MapModalComponent {
  @Input() layers: Layer[] = [];

  constructor(private modalCtrl: ModalController) {}

  close() {
    this.modalCtrl.dismiss();
  }
}
