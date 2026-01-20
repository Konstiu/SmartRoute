import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { ShareRouteFriendsPage } from './share-route-friends.page';

const routes: Routes = [
  {
    path: '',
    component: ShareRouteFriendsPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ShareRouteFriendsPageRoutingModule {}
