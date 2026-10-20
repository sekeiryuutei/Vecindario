import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { ToastService } from './toast.service';

const RETRIED = new HttpContextToken<boolean>(() => false);

function isAuthEndpoint(url: string): boolean {
  return /\/(auth\/login|auth\/refresh|auth\/forgot-password|auth\/reset-password|public\/)/.test(url);
}

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const token = auth.accessToken();
  if (!token || isAuthEndpoint(request.url)) return next(request);
  return next(request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};

export const refreshInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      const canRefresh = error.status === 401
        && !isAuthEndpoint(request.url)
        && !request.context.get(RETRIED)
        && Boolean(auth.refreshToken());
      if (!canRefresh) return throwError(() => error);
      return from(auth.refresh()).pipe(
        switchMap((session) => {
          if (!session?.accessToken) return throwError(() => error);
          return next(request.clone({
            context: request.context.set(RETRIED, true),
            setHeaders: { Authorization: `Bearer ${session.accessToken}` }
          }));
        }),
        catchError(() => throwError(() => error))
      );
    })
  );
};

export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const toast = inject(ToastService);
  const auth = inject(AuthService);
  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      const url = request.url;
      const isLogin = url.includes('/auth/login');
      const isProbe = url.includes('/auth/me') || url.includes('/auth/refresh');
      let message = 'No fue posible completar la solicitud.';

      if (error.status === 0) {
        message = 'No se pudo conectar con el servidor. Verifica tu conexión e inténtalo nuevamente.';
      } else if (error.status === 401) {
        message = isLogin ? 'Correo o contraseña incorrectos.' : 'Tu sesión expiró. Inicia sesión nuevamente.';
      } else if (error.status === 403) {
        message = 'No tienes permisos para realizar esta acción.';
      } else if (error.status === 404) {
        message = 'No se encontró la información solicitada.';
      } else if (error.status === 409) {
        message = typeof error.error?.message === 'string' ? error.error.message : 'La operación entra en conflicto con el estado actual.';
      } else if (error.status === 413) {
        message = 'El archivo supera el tamaño permitido.';
      } else if (error.status === 429) {
        message = 'Demasiadas solicitudes. Espera un momento antes de volver a intentarlo.';
      } else if (error.status >= 500) {
        message = 'Ocurrió un error en el servidor. Inténtalo nuevamente en unos minutos.';
      } else if (typeof error.error?.message === 'string' && error.error.message.trim()) {
        message = error.error.message;
      } else if (typeof error.error?.error === 'string' && error.error.error.trim()) {
        message = error.error.error;
      }

      (error as HttpErrorResponse & { friendlyMessage?: string }).friendlyMessage = message;

      if (error.status === 401 && !isLogin && !isProbe) {
        toast.error(message);
        auth.handleUnauthorized();
      } else if (!isLogin && !isProbe && error.status !== 401) {
        toast.error(message);
      }
      return throwError(() => error);
    })
  );
};
