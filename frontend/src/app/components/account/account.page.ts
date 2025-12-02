import { Component } from '@angular/core';
import { Router } from '@angular/router';
import {ConnectStravaComponent} from '../connect-strava/connect-strava.component'
import { AlertController, IonicModule } from '@ionic/angular';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ExploreContainerComponentModule } from '../explore-container/explore-container.module';
import { AuthService } from 'src/services/auth.service';
import { ConnectGarminComponent } from "../connect-garmin/connect-garmin.component";


@Component({
  selector: 'app-account',
  templateUrl: 'account.page.html',
  styleUrls: ['account.page.scss'],
  standalone: true,
  imports: [ConnectStravaComponent, IonicModule, CommonModule, FormsModule, ExploreContainerComponentModule, ConnectGarminComponent]
})
export class AccountPage {

  constructor(
    private authService: AuthService,
    private alertController: AlertController,
    private router: Router
  ) {}

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

  editUserData() {
    this.router.navigate(['/user-data']);
  }

  logout() {
    this.authService.logoutUser();
  }

}
