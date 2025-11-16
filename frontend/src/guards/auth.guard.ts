// src/app/guards/auth.guard.ts
import {Injectable} from '@angular/core';
import {CanActivate, ActivatedRouteSnapshot, RouterStateSnapshot, Router} from '@angular/router';
import {AuthService} from '../services/auth.service';

@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate {
  constructor(private authService: AuthService, private router: Router) {
  }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    const isLoggedIn = this.authService.isLoggedIn();
    const url = state.url;

    console.log('AuthGuard - checking:', state.url, 'isLoggedIn:', isLoggedIn);

    if (!isLoggedIn) {
      if (url === '/login' || url.startsWith('/register') || url.startsWith('/password_reset')) {
        return true;
      }
      // Redirect unauthenticated users to login page for all other routes
      this.router.navigate(['/login']);
      return false;
    } else {
      // Prevent authenticated users from accessing login, register, and password reset pages
      if (url === '/login' || url.startsWith('/register') || url.startsWith('/password_reset')) {
        this.router.navigate(['']);
        return false;
      }
    }

    console.log('Logged in, allowing access to:', state.url);
    return true;
  }
}
