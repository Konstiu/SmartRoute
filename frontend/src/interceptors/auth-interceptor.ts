import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { Globals } from '../global/globals';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const globals = inject(Globals);

  const authUri = globals.backendUri + '/authentication';
  const registerUri = globals.backendUri + '/user';
  const verifyUri = globals.backendUri + '/user/verify';
  const resetUri = globals.backendUri + '/user/reset_password';

  if (req.url === authUri ||
    (req.url === registerUri && req.method === "POST") ||
    req.url.startsWith(verifyUri) ||
    req.url.startsWith(resetUri)) {

    return next(req);
  }

  // Get the token
  const token = authService.getToken();

  // Only add Authorization header if token exists and is not empty
  if (token && token.trim()) {
    const authReq = req.clone({
      setHeaders: {
        Authorization: 'Bearer ' + token
      }
    });
    return next(authReq);
  }

  // If no token, pass the request as-is
  return next(req);
};
