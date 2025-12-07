import { Component, Input } from '@angular/core';
import { ModalController, IonicModule } from '@ionic/angular';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-weather-info',
  templateUrl: './weather.component.html',
  styleUrls: ['./weather.component.scss'],
  standalone: true,
  imports: [IonicModule, CommonModule]
})
export class WeatherInfoComponent {

  @Input() temperatureDescription!: string;
  @Input() windDescription!: string;
  @Input() precipitationDescription!: string;
  @Input() combinedSummary!: string;

  constructor(private modalCtrl: ModalController) {}

  dismiss() {
    this.modalCtrl.dismiss();
  }
}
