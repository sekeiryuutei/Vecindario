import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) return router.createUrlTree(['/login']);
  const session = auth.session();
  if (!session?.activeTenantId && session?.user?.platformRole !== 'SUPER_ADMIN_PLATFORM') {
    return router.createUrlTree(['/seleccionar-copropiedad']);
  }
  return true;
};

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) return true;
  return router.createUrlTree([auth.homeRoute()]);
};

export const platformGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.user()?.platformRole === 'SUPER_ADMIN_PLATFORM') return true;
  return router.createUrlTree(['/app/sin-acceso']);
};

export const permissionGuard = (...permissions: string[]): CanActivateFn => () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.hasPermission(...permissions)) return true;
  return router.createUrlTree(['/app/sin-acceso']);
};

export const roleGuard = (...roles: string[]): CanActivateFn => () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.hasRole(...roles)) return true;
  return router.createUrlTree(['/app/sin-acceso']);
};

export const tenantSelectionGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) return router.createUrlTree(['/login']);
  if (auth.session()?.activeTenantId && auth.session()?.tenants?.length === 1) {
    return router.createUrlTree([auth.homeRoute()]);
  }
  return true;
};
