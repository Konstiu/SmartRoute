import { Component } from '@angular/core';
import { Icon, icon, LatLng, Layer, marker } from 'leaflet';

@Component({
  selector: 'app-trainingplan',
  templateUrl: 'trainingPlan.page.html',
  styleUrls: ['trainingPlan.page.scss'],
  standalone: false,
})
export class TrainingPlanPage {

  constructor() { }

  markerOptions = {
    icon: icon({
      ...Icon.Default.prototype.options,
      iconUrl: 'assets/marker-icon.png',
      iconRetinaUrl: 'assets/marker-icon-2x.png',
      shadowUrl: 'assets/marker-shadow.png'
    })
  };

  layers: Layer[] = []

  newLocation(location: LatLng) {
    this.layers.push(marker(location, this.markerOptions));
  }
}
