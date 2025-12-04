import {IonicModule} from '@ionic/angular';
import {NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {GymWorkoutTabPage} from './gym-workout-tab.page';
import {ExploreContainerComponentModule} from '../explore-container/explore-container.module';
import {GymWorkoutTabPageRoutingModule} from "./gym-workout-tab-routing.module";


@NgModule({
  imports: [
    IonicModule,
    CommonModule,
    FormsModule,
    ExploreContainerComponentModule,
    GymWorkoutTabPageRoutingModule,
    GymWorkoutTabPage
  ],
  declarations: []
})
export class GymWorkoutTabPageModule {

}
