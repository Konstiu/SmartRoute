import { Component } from '@angular/core';
import {IonicModule, LoadingController, ToastController} from '@ionic/angular';
import {ConnectStravaComponent} from "../../connect-strava/connect-strava.component";
import {ConnectGarminComponent} from "../../connect-garmin/connect-garmin.component";

@Component({
  selector: 'app-sync-activities',
  templateUrl: './sync-activities.page.html',
  styleUrls: ['./sync-activities.page.scss'],
  imports: [
    IonicModule,
    ConnectStravaComponent,
    ConnectGarminComponent
  ]
})
export class SyncActivitiesPage  {

  constructor(
    private loadingController: LoadingController,
    private toastController: ToastController
  ) {}



  async syncNow() {
    const loading = await this.loadingController.create({
      message: 'Syncing activities...',
    });
    await loading.present();

    try {
      // TODO: Call your sync service
      // await this.syncService.syncAll();

      // Simulate sync
      await new Promise(resolve => setTimeout(resolve, 2000));

      await loading.dismiss();

      const toast = await this.toastController.create({
        message: 'Activities synced successfully!',
        duration: 2000,
        color: 'success',
        icon: 'checkmark-circle'
      });
      await toast.present();

    } catch (error) {
      await loading.dismiss();

      const toast = await this.toastController.create({
        message: 'Sync failed. Please try again.',
        duration: 3000,
        color: 'danger',
        icon: 'alert-circle'
      });
      await toast.present();
    }
  }
}
