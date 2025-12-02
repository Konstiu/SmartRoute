import { Component } from '@angular/core';
import { Router } from '@angular/router';
import {ConnectStravaComponent} from '../connect-strava/connect-strava.component'
import { IonicModule } from '@ionic/angular';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ExploreContainerComponentModule } from '../explore-container/explore-container.module';


@Component({
  selector: 'app-account',
  templateUrl: 'account.page.html',
  styleUrls: ['account.page.scss'],
  standalone: true,
  imports: [ConnectStravaComponent, IonicModule, CommonModule, FormsModule, ExploreContainerComponentModule]
})
export class AccountPage {

  constructor(private router: Router) {}

  editUserData() {
    this.router.navigate(['/user-data']);
  }

}
