import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { TabsPage } from './tabs.page';

const routes: Routes = [
  {
    path: 'tabs',
    component: TabsPage,
    children: [
      {
        path: 'trainingPlan',
        loadComponent: () => import('../trainingPlan/trainingPlan.page').then(m => m.TrainingPlanPage)
      },
      {
        path: 'route',
        loadChildren: () => import('../route/route.module').then(m => m.RoutePageModule)
      },
      {
        path: 'account',
        loadChildren: () => import('../account/account.module').then(m => m.AccountPageModule)
      },
      {
        path: 'recentRuns',
        loadComponent: () => import('../recentRuns/recent-runs.page').then(m => m.RecentRunsPage)
      },
      {
        path: 'gym',
        loadChildren: () =>
          import('../gym-workout-tab/gym-workout-tab.module').then(
            m => m.GymWorkoutTabPageModule,
          ),
      },

      {
        path: 'activ',
        loadComponent: () => import('../recentRuns/recent-runs.page').then(m => m.RecentRunsPage)
      },
      {
        path: '',
        redirectTo: '/tabs/trainingPlan',
        pathMatch: 'full'
      }
    ]
  },
  {
    path: '',
    redirectTo: '/tabs/trainingPlan',
    pathMatch: 'full'
  },
  {
    path: 'activity/:id',
    loadComponent: () => import('../../components/recentRuns/activity-details/activity-details.page').then(m => m.ActivityDetailPage)
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
})
export class TabsPageRoutingModule {
}
