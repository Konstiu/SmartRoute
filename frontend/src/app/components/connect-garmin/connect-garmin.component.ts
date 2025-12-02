import {Component, inject, Input, OnInit} from '@angular/core';
import { AlertController, IonicModule, LoadingController, ToastController } from '@ionic/angular';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { GarminService } from 'src/services/garmin.service';

@Component({
  selector: 'app-connect-garmin',
  templateUrl: './connect-garmin.component.html',
  standalone: true,
  styleUrls: ['./connect-garmin.component.scss'],
  imports: [IonicModule, CommonModule, FormsModule, ReactiveFormsModule]
})
export class ConnectGarminComponent implements OnInit{

  protected connectionState: boolean | undefined;

  // reactive form for manual sync
  garminForm: FormGroup;
  showPassword = false;
  isSyncing: boolean = false;


  @Input()
  private garminService: GarminService = inject(GarminService);

  constructor(
    private fb: FormBuilder,
    private loadingCtrl: LoadingController,
    private toastCtrl: ToastController,
    private alertController: AlertController
  ) {
    this.garminForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required]],
      count: [10, [Validators.required, Validators.min(1)]]
    });
  }

  ngOnInit(): void {
    this.garminService.getConnectionState().subscribe({
      next: result => {
        this.connectionState = result;
      },
      error: error => {
        console.error("Failed to load Garmin connection state: " + error)
      }
    })
  }

  async doSync(): Promise<void> {
    
    const { email, password, count } = this.garminForm.value;

    this.isSyncing = true;

    const loading = await this.loadingCtrl.create({
      message: 'Syncing...',
      spinner: 'crescent'
    });
    await loading.present();

    this.garminService.sync(email, password, count).subscribe({
      next: async res => {
        await loading.dismiss();
        this.isSyncing = false;
        const toast = await this.toastCtrl.create({ message: 'Sync completed successfully.', color: 'success', duration: 3000, position: 'top' });
        await toast.present();
      },
      error: async err => {
        console.error(err);
        await loading.dismiss();
        this.isSyncing = false;
        const message = err?.error?.message || 'Sync failed. Please check credentials or try again.';
        const toast = await this.toastCtrl.create({ message, color: 'danger', duration: 4000, position: 'top' });
        await toast.present();
      }
    });
  }

  togglePasswordVisibility() {
    this.showPassword = !this.showPassword;
  }

  canSync(): boolean {
    if (this.isSyncing) {
      return false;
    }
    if (this.connectionState === true && this.garminForm.controls['count']?.value > 0) {
      return true;
    }
    return this.garminForm.valid;
  }

  async presentDisconnectGarminDialog() {
    const alert = await this.alertController.create({
      header: 'Disconnect Garmin',
      message: 'Are you sure you want to disconnect your Garmin account?',
      buttons: [
        {
          text: 'Cancel',
          role: 'cancel',
        },
        {
          text: 'Disconnect',
          role: 'confirm',
          handler: () => {
            this.disconnectGarmin();
          },
        },
      ],
    });

    await alert.present();
  }

  disconnectGarmin() {
    this.isSyncing = true;
    this.garminService.disconnect().subscribe({
      next: async () => {
        this.isSyncing = false;
        this.connectionState = false;
        const toast = await this.toastCtrl.create({ message: 'Garmin disconnected successfully.', color: 'success', duration: 3000, position: 'top' });
        await toast.present();
      },
      error: async (err) => {
        console.error(err);
        this.isSyncing = false;
        const message = err?.error?.message || 'Failed to disconnect Garmin. Please try again.';
        const toast = await this.toastCtrl.create({ message, color: 'danger', duration: 4000, position: 'top' });
        await toast.present();
      }
    });
  }

}
