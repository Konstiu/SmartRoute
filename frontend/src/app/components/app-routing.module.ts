// src/app/components/app-routing.module.ts
import { NgModule } from '@angular/core';
import { PreloadAllModules, RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../../guards/auth.guard';

const routes: Routes = [
  {
    path: '',
    loadChildren: () => import('./tabs/tabs.module').then(m => m.TabsPageModule),
    canActivate:[AuthGuard]
  },
  {
    path: 'register',
    loadComponent: () => import('./register/register.page').then(m => m.RegisterPage),
    canActivate:[AuthGuard]
  },
  {
    path: 'login',
    loadComponent: () => import('./login/login.page').then(m => m.LoginPage),
    canActivate:[AuthGuard]
    // NO canActivate - public route!
  },
  {
    path: 'request-password-reset',
    loadComponent: () => import('./resetPassword/request-password-reset/request-reset-password.page').then(m => m.RequestResetPasswordPage),
  },
  {
    path: 'password_reset/:token',
    loadComponent: () => import('./resetPassword/reset-password.page').then(m => m.ResetPasswordPage)
  },
  {
    path: 'import-gpx',
    loadComponent: () => import('./import-gpx/import-gpx.page').then(m => m.ImportGpxPage),
    canActivate: [AuthGuard]
  },
  {
    path: 'user-data',
    loadComponent: () => import('./user-data/user-data.component').then(m => m.UserDataComponent),
    canActivate: [AuthGuard]
  },
  {
    path: '**',
    loadComponent: () => import('./register/register.page').then(m => m.RegisterPage),
    canActivate:[AuthGuard]
  }
];

@NgModule({
  imports: [
    RouterModule.forRoot(routes, { preloadingStrategy: PreloadAllModules })
  ],
  exports: [RouterModule]
})
export class AppRoutingModule {}
