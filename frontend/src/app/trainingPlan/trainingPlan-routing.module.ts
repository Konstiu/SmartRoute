import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { TrainingPlanPage } from './trainingPlan.page';

const routes: Routes = [
  {
    path: '',
    component: TrainingPlanPage,
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TrainingPlanPageRoutingModule {}
