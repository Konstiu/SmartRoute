import {Component, inject, Input, OnInit, Output, EventEmitter} from '@angular/core';
import {AlertController, IonicModule, LoadingController, ToastController} from '@ionic/angular';
import {CommonModule} from '@angular/common';
import {FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators} from '@angular/forms';
import {GarminService} from 'src/services/garmin.service';

@Component({
  selector: 'app-connect-garmin',
  templateUrl: './connect-garmin.component.html',
  standalone: true,
  styleUrls: ['./connect-garmin.component.scss'],
  imports: [IonicModule, CommonModule, FormsModule, ReactiveFormsModule]
})
export class ConnectGarminComponent implements OnInit {
  @Output() connectionChanged = new EventEmitter<boolean>();

  protected connectionState: boolean | undefined;

  garminForm: FormGroup;
  showPassword = false;
  isSyncing: boolean = false;


  @Input()
  private garminService: GarminService = inject(GarminService);

  constructor(
    private fb: FormBuilder,
    private loadingCtrl: LoadingController,
    private toastCtrl: ToastController,
    private alertController: AlertController,
    private toastController: ToastController,
  ) {
    this.garminForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required]]
    });
  }

  ngOnInit(): void {
    this.garminService.getConnectionState().subscribe({
      next: result => {
        this.connectionState = result;
        this.connectionChanged.emit(result || false);
      },
      error: error => {
        console.error("Failed to load Garmin connection state: " + error);
        this.connectionChanged.emit(false);
      }
    })
  }

  async doSync(): Promise<void> {
    const {email, password} = this.garminForm.value;

    this.isSyncing = true;

    const loading = await this.loadingCtrl.create({
      message: 'Connecting to Garmin...',
      spinner: 'crescent'
    });
    await loading.present();

    // Sync with only 1 activity to verify credentials
    this.garminService.sync(email, password, 1).subscribe({
      next: async res => {
        await loading.dismiss();
        this.isSyncing = false;
        this.refreshConnectionState();
        await this.showToast('Garmin connected successfully! Use the sync button to import more activities.', 'success')
      },
      error: async err => {
        console.error(err);
        await loading.dismiss();
        this.isSyncing = false;
        const message = err?.error?.message || 'Connection failed. Please check your credentials.';
        await this.showToast(message, 'danger')
      }
    });
  }

  private refreshConnectionState(): void {
    this.garminService.getConnectionState().subscribe({
      next: result => {
        this.connectionState = result;
        this.connectionChanged.emit(result || false);
      },
      error: error => {
        console.error("Failed to refresh Garmin connection state: " + error);
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
    if (this.connectionState === true) {
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
        this.connectionChanged.emit(false);
        await this.showToast('Garmin disconnected successfully.', 'success')
      },
      error: async (err) => {
        console.error(err);
        this.isSyncing = false;
        const message = err?.error?.message || 'Failed to disconnect Garmin. Please try again.';
        await this.showToast(message, 'danger');
      }
    });
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
