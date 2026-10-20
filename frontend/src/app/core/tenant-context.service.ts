import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from './api.service';
import { TenantView } from './models';

@Injectable({ providedIn: 'root' })
export class TenantContextService {
  private readonly api = inject(ApiService);
  private readonly tenantState = signal<TenantView | null>(null);
  private loadingState = signal(false);
  private loadedFor: string | null = null;

  readonly tenant = this.tenantState.asReadonly();
  readonly loading = this.loadingState.asReadonly();

  async load(activeTenantId: string | null | undefined, force = false): Promise<void> {
    if (!activeTenantId) {
      this.tenantState.set(null);
      this.loadedFor = null;
      return;
    }
    if (!force && this.loadedFor === activeTenantId && this.tenantState()) return;
    this.loadingState.set(true);
    try {
      const tenant = await firstValueFrom(this.api.get<TenantView>('/tenants/current'));
      this.tenantState.set(tenant);
      this.loadedFor = activeTenantId;
    } catch {
      // La sesión y los permisos siguen siendo la fuente principal; el contexto se reintenta al navegar.
    } finally {
      this.loadingState.set(false);
    }
  }

  clear(): void {
    this.tenantState.set(null);
    this.loadedFor = null;
  }
}
