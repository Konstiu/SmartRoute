import {Component, OnInit, ViewChild} from '@angular/core';
import {IonicModule, ToastController} from '@ionic/angular';
import {ConnectStravaComponent} from "../../connect-strava/connect-strava.component";
import {ConnectGarminComponent} from "../../connect-garmin/connect-garmin.component";
import {ActivitiesService} from "../../../../services/activities.service";
import {FormsModule} from '@angular/forms';
import {CommonModule} from '@angular/common';
import {StravaService} from "../../../../services/strava.service";
import {GarminService} from "../../../../services/garmin.service";

@Component({
  selector: 'app-sync-activities',
  templateUrl: './sync-activities.page.html',
  styleUrls: ['./sync-activities.page.scss'],
  imports: [
    IonicModule,
    ConnectStravaComponent,
    ConnectGarminComponent,
    FormsModule
  ]
})
export class SyncActivitiesPage implements OnInit {
  activitiesToSync: number = 10;
  isSyncing: boolean = false;
  syncComplete: boolean = false;
  isStravaConnected: boolean = false;
  isGarminConnected: boolean = false;
  readonly MAX_ACTIVITIES = 100;

  constructor(
    private activitiesService: ActivitiesService,
    private toastController: ToastController,
    private stravaService: StravaService,
    private garminService: GarminService
  ) {
  }

  ngOnInit() {
    this.checkConnections();
  }


  async syncActivities() {
    if (!this.hasAnyServiceConnected) {
      await this.showToast('Please connect a service first', 'warning');
      return;
    }

    if (!this.activitiesToSync || this.activitiesToSync < 1) {
      await this.showToast('Please enter a valid number of activities', 'warning');
      return;
    }
    if (this.activitiesToSync > this.MAX_ACTIVITIES) {
      await this.showToast(`You cannot import more than ${this.MAX_ACTIVITIES} activities at once`, 'warning', 3000)
      return
    }

    this.isSyncing = true;
    this.syncComplete = false;

    await this.showToast('Starting sync...', 'primary');

    this.activitiesService.refreshActivities(this.activitiesToSync).subscribe({
      next: async () => {
        this.isSyncing = false;
        this.syncComplete = true;

        await this.showToast(`✓ Successfully synced ${this.activitiesToSync} activities!`, 'success', 3000);

        setTimeout(() => {
          this.syncComplete = false;
        }, 3000);
      },
      error: async (error: any) => {
        this.isSyncing = false;
        console.error('Sync failed:', error);

        await this.showToast('✗ Sync failed. Please try again.', 'danger', 3000);
      }
    });
  }


  checkConnections() {
    // Check Strava connection
    this.stravaService.getConnectionState().subscribe({
      next: result => {
        this.isStravaConnected = result?.connected || false;
      },
      error: error => {
        console.error("Failed to load Strava connection state: " + error);
        this.isStravaConnected = false;
      }
    });

    // Check Garmin connection
    this.garminService.getConnectionState().subscribe({
      next: result => {
        this.isGarminConnected = result || false;
      },
      error: error => {
        console.error("Failed to load Garmin connection state: " + error);
        this.isGarminConnected = false;
      }
    });
  }

  onStravaConnectionChanged(connected: boolean) {
    this.isStravaConnected = connected;
  }

  onGarminConnectionChanged(connected: boolean) {
    this.isGarminConnected = connected;
  }

  get hasAnyServiceConnected(): boolean {
    return this.isStravaConnected || this.isGarminConnected;
  }

  private async showToast(message: string, color: string = 'primary', duration: number = 2000) {
    const toast = await this.toastController.create({
      message: message,
      duration: duration,
      position: 'top',
      color: color,
      buttons: [
        {
          text: 'Dismiss',
          role: 'cancel'
        }
      ]
    });
    await toast.present();
  }
}
