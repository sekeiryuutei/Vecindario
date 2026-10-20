import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { ApiService } from './api.service';
import {
  ChangePasswordRequest,
  ForgotPasswordRequest,
  LoginRequest,
  MeResult,
  ResetPasswordRequest,
  SelectTenantRequest,
  SessionResult,
  TenantTokenResult
} from './models';

const STORAGE_KEY = 'vecindad.session.v1';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly sessionState = signal<SessionResult | null>(this.readStoredSession());
  private refreshInFlight: Promise<SessionResult | null> | null = null;

  readonly session = this.sessionState.asReadonly();
  readonly user = computed(() => this.sessionState()?.user ?? null);
  readonly tenants = computed(() => this.sessionState()?.tenants ?? []);
  readonly activeRole = computed(() => this.sessionState()?.activeRole ?? '');
  readonly permissions = computed(() => new Set(this.sessionState()?.permissions ?? []));
  readonly isAuthenticated = computed(() => Boolean(this.sessionState()?.accessToken));
  readonly hasTenant = computed(() => Boolean(this.sessionState()?.activeTenantId));
  readonly isPlatformAdmin = computed(() => this.sessionState()?.user?.platformRole === 'SUPER_ADMIN_PLATFORM');
  readonly isAdmin = computed(() => this.activeRole() === 'ADMINISTRADOR');
  readonly isPorter = computed(() => ['PORTERO', 'SUPERVISOR_PORTERIA'].includes(this.activeRole()));
  readonly isResident = computed(() => ['PROPIETARIO', 'ARRENDATARIO', 'RESIDENTE'].includes(this.activeRole()));

  accessToken(): string | null {
    return this.sessionState()?.accessToken ?? null;
  }

  refreshToken(): string | null {
    return this.sessionState()?.refreshToken ?? null;
  }

  hasPermission(...permissions: string[]): boolean {
    const session = this.sessionState();
    if (!session) return false;
    if (session.activeRole === 'ADMINISTRADOR') return true;
    if (!session.activeTenantId && session.user?.platformRole === 'SUPER_ADMIN_PLATFORM') return true;
    const granted = new Set(session.permissions ?? []);
    return permissions.some((permission) => granted.has(permission));
  }

  hasAnyPermission(permissions: string[]): boolean {
    return this.hasPermission(...permissions);
  }

  hasRole(...roles: string[]): boolean {
    return roles.includes(this.sessionState()?.activeRole ?? '');
  }

  homeRoute(): string {
    const session = this.sessionState();
    if (!session) return '/login';
    if (session.user?.platformRole === 'SUPER_ADMIN_PLATFORM' && !session.activeTenantId) return '/app/plataforma';
    if (!session.activeTenantId) return '/seleccionar-copropiedad';
    return '/app/inicio';
  }

  async login(credentials: LoginRequest): Promise<SessionResult> {
    const session = await firstValueFrom(this.api.post<SessionResult>('/auth/login', credentials));
    this.persist(session);
    return session;
  }

  async selectTenant(tenantId: string): Promise<TenantTokenResult> {
    const body: SelectTenantRequest = { tenantId };
    const result = await firstValueFrom(this.api.post<TenantTokenResult>('/auth/select-tenant', body));
    const current = this.sessionState();
    if (!current) throw new Error('La sesión no está disponible.');
    const updated: SessionResult = {
      ...current,
      accessToken: result.accessToken,
      tokenType: result.tokenType,
      expiresIn: result.expiresIn,
      activeTenantId: result.tenantId,
      activeRole: result.role,
      permissions: result.permissions ?? [],
      tenants: current.tenants?.map((tenant) =>
        tenant.id === result.tenantId ? { ...tenant, name: result.tenantName ?? tenant.name, role: result.role ?? tenant.role } : tenant
      )
    };
    this.persist(updated);
    return result;
  }

  async refresh(): Promise<SessionResult | null> {
    const token = this.refreshToken();
    if (!token) return null;
    if (this.refreshInFlight) return this.refreshInFlight;
    this.refreshInFlight = (async () => {
      try {
        const session = await firstValueFrom(this.api.post<SessionResult>('/auth/refresh', { refreshToken: token }));
        this.persist(session);
        return session;
      } catch {
        this.clearSession();
        return null;
      } finally {
        this.refreshInFlight = null;
      }
    })();
    return this.refreshInFlight;
  }

  async restore(): Promise<void> {
    const current = this.sessionState();
    if (!current?.accessToken) return;
    try {
      const me = await firstValueFrom(this.api.get<MeResult>('/auth/me'));
      const updated: SessionResult = {
        ...current,
        user: me.user ?? current.user,
        tenants: me.tenants ?? current.tenants,
        activeTenantId: me.activeTenantId,
        activeRole: me.activeRole,
        permissions: me.permissions ?? []
      };
      this.persist(updated);
    } catch (error) {
      const status = (error as { status?: number })?.status;
      if (status === 401) this.clearSession();
    }
  }

  async logout(): Promise<void> {
    const token = this.refreshToken();
    try {
      if (token) {
        await firstValueFrom(this.api.post<void>('/auth/logout', { refreshToken: token }));
      }
    } catch {
      // El cierre local prevalece aunque el servidor no responda.
    } finally {
      this.clearSession();
      await this.router.navigateByUrl('/login');
    }
  }

  clearSession(): void {
    this.sessionState.set(null);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // Almacenamiento no disponible (modo privado); la sesión vive en memoria.
    }
  }

  handleUnauthorized(): void {
    if (!this.sessionState()) return;
    this.clearSession();
    void this.router.navigateByUrl('/login');
  }

  async changePassword(body: ChangePasswordRequest): Promise<void> {
    await firstValueFrom(this.api.post<void>('/auth/change-password', body));
  }

  async forgotPassword(body: ForgotPasswordRequest): Promise<void> {
    await firstValueFrom(this.api.post<void>('/auth/forgot-password', body));
  }

  async resetPassword(body: ResetPasswordRequest): Promise<void> {
    await firstValueFrom(this.api.post<void>('/auth/reset-password', body));
  }

  private persist(session: SessionResult): void {
    this.sessionState.set(session);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } catch {
      // La sesión permanece disponible en memoria.
    }
  }

  private readStoredSession(): SessionResult | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw) as SessionResult;
      return parsed?.accessToken ? parsed : null;
    } catch {
      return null;
    }
  }
}
