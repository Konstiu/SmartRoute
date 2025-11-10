import { IonicModule } from '@ionic/angular';
import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {TrainingPlanPage} from './trainingPlan.page';
import { ExploreContainerComponentModule } from '../explore-container/explore-container.module';

import { TrainingPlanPageRoutingModule } from './trainingPlan-routing.module';

@NgModule({
  imports: [
    IonicModule,
    CommonModule,
    FormsModule,
    ExploreContainerComponentModule,
    TrainingPlanPageRoutingModule
  ],
  declarations: [TrainingPlanPage]
})
export class TrainingPlanPageModule {}
