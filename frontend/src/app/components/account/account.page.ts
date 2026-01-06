import {Component, inject, OnInit} from '@angular/core';
import {ConnectStravaComponent} from '../connect-strava/connect-strava.component'
import {AlertController, IonicModule, ToastController} from '@ionic/angular';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ExploreContainerComponentModule} from '../explore-container/explore-container.module';
import {AuthService} from 'src/services/auth.service';
import {ConnectGarminComponent} from "../connect-garmin/connect-garmin.component";
import {UserDataDisplayComponent} from '../user-data-display/user-data-display.component';
import {Router} from "@angular/router";
import {UserService} from "../../../services/user.service";
import {PushNotificationService} from "../../../services/push-notification.service";

@Component({
  selector: 'app-account',
  templateUrl: 'account.page.html',
  styleUrls: ['account.page.scss'],
  standalone: true,
  imports: [IonicModule, CommonModule, FormsModule, ExploreContainerComponentModule, UserDataDisplayComponent]
})
export class AccountPage implements OnInit {
  private userService = inject(UserService);
  emergencyAlertEnabled = true;

  constructor(
    private authService: AuthService,
    private alertController: AlertController,
    private router: Router,
    private toastController: ToastController,
    private pushService: PushNotificationService,
  ) {
  }

  ngOnInit() {
    this.pushService.listenToNotifications();
    this.pushService.emergencyAlertEnabled$.subscribe(enabled => {
      this.emergencyAlertEnabled = enabled;
    });
  }

  toggleEmergencyAlert(event: any) {
    const enabled = event.detail.checked;
    this.pushService.setEmergencyAlertEnabled(enabled);
  }

  async presentLogoutConfirm() {
    const alert = await this.alertController.create({
      header: 'Logout',
      message: 'Are you sure you want to logout?',
      buttons: [
        {
          text: 'Cancel',
          role: 'cancel',
        },
        {
          text: 'Logout',
          role: 'confirm',
          handler: () => {
            this.logout();
          },
        },
      ],
    });

    await alert.present();
  }

  async deleteAccount() {
    this.userService.deleteAccount().subscribe({
      next: async () => {
        const toast = await this.toastController.create({
          message: 'Account deleted successfully',
          color: 'success',
          duration: 2000
        });
        await toast.present();

        // Logout and redirect to login page
        this.authService.logoutUser();
      },
      error: async (error) => {
        console.error('Error deleting account:', error);
        const toast = await this.toastController.create({
          message: 'Failed to delete account. Please try again.',
          color: 'danger',
          duration: 3000
        });
        await toast.present();
      }
    });
  }

  navigateToSync() {
    this.router.navigate(['/sync-activities']);
  }

  navigateToInjuries() {
    this.router.navigate(['/injuries']);
  }

  navigateToFriends() {
    this.router.navigate(['/friends']);
  }

  logout() {
    this.authService.logoutUser();
  }

  delete() {
    this.userService.deleteAccount();
  }
}
