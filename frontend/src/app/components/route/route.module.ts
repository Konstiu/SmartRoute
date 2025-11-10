import { IonicModule } from '@ionic/angular';
import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RoutePage } from './route.page';
import { ExploreContainerComponentModule } from '../explore-container/explore-container.module';

import { RoutePageRoutingModule } from './route-routing.module';

@NgModule({
  imports: [
    IonicModule,
    CommonModule,
    FormsModule,
    ExploreContainerComponentModule,
    RoutePageRoutingModule
  ],
  declarations: [RoutePage]
})
export class RoutePageModule {}
