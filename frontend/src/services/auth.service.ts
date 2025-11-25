import { Injectable } from '@angular/core';
import { AuthRequest } from '../app/dtos/auth-request';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs/operators';
import { jwtDecode } from 'jwt-decode';
import { Globals } from '../global/globals';
import { Router } from '@angular/router';

interface JwtPayload {
  rol: string[];
  exp: number;
  sub: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private authBaseUri: string = this.globals.backendUri + '/authentication';

  constructor(
    private httpClient: HttpClient,
    private globals: Globals,
    private router: Router
  ) {}

  /**
   * Login in the user. If it was successful, a valid JWT token will be stored
   *
   * @param authRequest User data
   */
  loginUser(authRequest: AuthRequest): Observable<string> {
    return this.httpClient.post(this.authBaseUri, authRequest, { responseType: 'text' })
      .pipe(
        tap((authResponse: string) => {
          this.setToken(authResponse);
        })
      );
  }

  /**
   * Check if a valid JWT token is saved in the localStorage
   */
  isLoggedIn(): boolean {
    const token = this.getToken();
    if (!token) {
      return false;
    }

    const expirationDate = this.getTokenExpirationDate(token);
    if (!expirationDate) {
      return false;
    }

    return expirationDate.valueOf() > new Date().valueOf();
  }

  /**
   * Logout the user and redirect to login page
   */
  logoutUser(): void {
    localStorage.removeItem('authToken');
    this.router.navigate(['/login']);
  }

  /**
   * Get the stored JWT token
   */
  getToken(): string | null {
    return localStorage.getItem('authToken');
  }


  /**
   * Get user email from token
   */
  getUserEmail(): string | null {
    const token = this.getToken();
    if (!token) {
      return null;
    }

    try {
      const decoded = jwtDecode<JwtPayload>(token);
      return decoded.sub || null;
    } catch (error) {
      console.error('Error decoding token:', error);
      return null;
    }
  }

  /**
   * Store the JWT token in localStorage
   */
  private setToken(authResponse: string): void {
    localStorage.setItem('authToken', authResponse);
  }

  /**
   * Get the expiration date from the JWT token
   */
  private getTokenExpirationDate(token: string): Date | null {
    try {
      const decoded = jwtDecode<JwtPayload>(token);

      if (decoded.exp === undefined) {
        return null;
      }

      const date = new Date(0);
      date.setUTCSeconds(decoded.exp);
      return date;
    } catch (error) {
      console.error('Error decoding token expiration:', error);
      return null;
    }
  }
}
