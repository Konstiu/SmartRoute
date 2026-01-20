import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { ShareRouteFriendsPageRoutingModule } from './share-route-friends-routing.module';

import { ShareRouteFriendsPage } from './share-route-friends.page';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    ShareRouteFriendsPageRoutingModule,
    ShareRouteFriendsPage
  ],
  declarations: []
})
export class ShareRouteFriendsPageModule {}
