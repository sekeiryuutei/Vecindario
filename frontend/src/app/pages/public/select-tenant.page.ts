import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { BrandingService } from '../../core/branding.service';
import { TenantContextService } from '../../core/tenant-context.service';
import { roleLabel } from '../../core/labels';
import { TenantSummary } from '../../core/models';
import { describeError, initials } from '../../core/utils';

@Component({
  selector: 'app-select-tenant-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="auth-page">
      <section class="auth-hero">
        <div class="auth-brand">
          <div class="brand-mark">{{ initial() }}</div>
          <div>
            <div class="auth-brand-name">{{ branding.appName() }}</div>
            <div class="auth-brand-company">{{ branding.company() }}</div>
          </div>
        </div>
        <div class="auth-hero-copy">
          <h1>Selecciona la copropiedad que vas a administrar.</h1>
          <p>Tu usuario tiene acceso a más de una copropiedad. La información, los permisos y los datos que verás dependen de la copropiedad seleccionada.</p>
        </div>
        <div class="auth-foot">© {{ year }} {{ branding.company() || branding.appName() }}</div>
      </section>
      <section class="auth-panel">
        <div class="auth-card">
          <h2>Seleccionar copropiedad</h2>
          <p class="lead">Hola, {{ auth.user()?.fullName }}. Elige con cuál deseas continuar.</p>
          @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
          @if (!auth.tenants().length) {
            <div class="alert alert-info">Tu usuario no tiene copropiedades asignadas. Contacta a la administración para completar la configuración de tu acceso.</div>
          } @else {
            <div class="stack">
              @for (tenant of auth.tenants(); track tenant.id) {
                <button type="button" class="login-tenant" [disabled]="loadingId() !== null" (click)="select(tenant)">
                  <span style="display:flex;align-items:center;gap:11px;min-width:0">
                    <span class="avatar" style="background:#ccfbf1;color:#0f766e">{{ initials(tenant.name) }}</span>
                    <span style="min-width:0">
                      <strong>{{ tenant.name }}</strong>
                      <span>{{ roleLabel(tenant.role) }}</span>
                    </span>
                  </span>
                  @if (loadingId() === tenant.id) { <span class="spinner"></span> } @else { <span class="muted" aria-hidden="true">→</span> }
                </button>
              }
            </div>
          }
          @if (auth.isPlatformAdmin()) {
            <a class="btn btn-secondary btn-block" style="margin-top:14px" routerLink="/app/plataforma">Ir a la plataforma</a>
          }
          <button type="button" class="btn btn-ghost btn-block" style="margin-top:10px" (click)="auth.logout()">Cerrar sesión</button>
        </div>
      </section>
    </div>
  `
})
export class SelectTenantPage {
  readonly auth = inject(AuthService);
  readonly branding = inject(BrandingService);
  private readonly tenantContext = inject(TenantContextService);
  private readonly router = inject(Router);
  readonly year = new Date().getFullYear();
  readonly loadingId = signal<string | null>(null);
  readonly error = signal('');
  readonly initials = initials;
  readonly roleLabel = roleLabel;

  initial(): string {
    return this.branding.appName().trim().charAt(0).toUpperCase() || 'V';
  }

  async select(tenant: TenantSummary): Promise<void> {
    if (!tenant.id) return;
    this.error.set('');
    this.loadingId.set(tenant.id);
    try {
      await this.auth.selectTenant(tenant.id);
      this.tenantContext.clear();
      await this.router.navigateByUrl('/app/inicio');
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.loadingId.set(null);
    }
  }
}
