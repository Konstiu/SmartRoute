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
  @Input() weatherScore!: number;

  constructor(private modalCtrl: ModalController) {}

  close() {
    this.modalCtrl.dismiss();
  }

  ngOnInit() {
    console.log("Weather modal received:", {
      temperature: this.temperatureText,
      wind: this.windText,
      precipitation: this.precipitationText,
      weatherScore: this.weatherScore
    });
  }

  getWeatherScoreSummary(weatherScore: number): string {
    if (weatherScore < 0.0 || weatherScore > 1.0) {
        return "Invalid weather score.";
    }

    if (weatherScore <= 0.1) {
        return "Extremely unfavorable conditions, running is not recommended.";
    } else if (weatherScore <= 0.2) {
        return "Very challenging conditions with high performance loss.";
    } else if (weatherScore <= 0.3) {
        return "Unfavorable weather, expect significant effort and reduced safety.";
    } else if (weatherScore <= 0.4) {
        return "Challenging conditions, manageable but far from ideal.";
    } else if (weatherScore <= 0.5) {
        return "Some impairments present, the run will feel harder than usual.";
    } else if (weatherScore <= 0.6) {
        return "Acceptable conditions with mild performance impact.";
    } else if (weatherScore <= 0.7) {
        return "Good running conditions with only small drawbacks.";
    } else if (weatherScore <= 0.8) {
        return "Very favorable conditions, strong running performance expected.";
    } else if (weatherScore <= 0.9) {
        return "Excellent weather, almost optimal for running.";
    } else {
        return "Near-perfect conditions, best possible weather for running.";
    }
  }
}
