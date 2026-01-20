import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { IonicModule, ModalController } from '@ionic/angular';

export interface SanitarySettings {
  enabled: boolean;
  includeToilets: boolean;
  toiletIntervalMeters: number;
  includeFountains: boolean;
  fountainIntervalMeters: number;
  maxFacilityDistance: number;
}

@Component({
  selector: 'app-sanitary-settings-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, IonicModule],
  templateUrl: './consider-fac.component.html',
  styleUrls: ['./consider-fac.component.scss'],
})
export class SanitarySettingsModalComponent implements OnInit {
  settings: SanitarySettings = {
    enabled: false,
    includeToilets: true,
    toiletIntervalMeters: 5000,
    includeFountains: true,
    fountainIntervalMeters: 3000,
    maxFacilityDistance: 200
  };

  constructor(private modalCtrl: ModalController) {}

  ngOnInit() {
    // Settings are passed in via componentProps when modal is created
    // They will override the defaults above
  }

  cancel() {
    this.modalCtrl.dismiss(null, 'cancel');
  }

  confirm() {
    this.modalCtrl.dismiss(this.settings, 'confirm');
  }

  toggleEnabled() {
    this.settings.enabled = !this.settings.enabled;
  }

  // Format meters to km for display
  formatDistance(meters: number): string {
    if (meters >= 1000) {
      return `${(meters / 1000).toFixed(1)}km`;
    }
    return `${meters}m`;
  }
}
