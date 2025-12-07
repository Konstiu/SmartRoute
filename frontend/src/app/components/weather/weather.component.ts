import { Component, Input } from '@angular/core';
import { IonicModule, ModalController } from '@ionic/angular';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-weather-info',
  templateUrl: './weather.component.html',
  styleUrls: ['./weather.component.scss'],
  standalone: true,
  imports: [IonicModule, CommonModule]
})
export class WeatherInfoComponent {

  @Input() temperatureText!: string;
  @Input() windText!: string;
  @Input() precipitationText!: string;

  constructor(private modalCtrl: ModalController) {}

  close() {
    this.modalCtrl.dismiss();
  }

  ngOnInit() {
    console.log("Weather modal received:", {
      temperature: this.temperatureText,
      wind: this.windText,
      precipitation: this.precipitationText
    });
  }
}
