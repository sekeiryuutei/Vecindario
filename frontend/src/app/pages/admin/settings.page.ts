import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { label } from '../../core/labels';
import {
  TenantNotificationSettings,
  TenantView,
  UpdateSettingsRequest,
  UpdateTenantRequest
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, PageHeaderComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-settings-page',
  standalone: true,
  imports: [FormsModule, RouterLink, PageHeaderComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Configuración" subtitle="Datos de la copropiedad, funcionalidades activas y canales de notificación.">
      <button type="button" class="btn btn-secondary" [disabled]="loading()" (click)="load()">Actualizar</button>
    </app-page-header>

    @if (loading()) {
      <div class="loading"><span class="spinner"></span> Cargando configuración...</div>
    } @else {
      <div class="grid grid-2">
        <div class="card">
          <div class="card-header">
            <div><h2>Datos de la copropiedad</h2><p>Información visible para residentes, portería y estados de cuenta.</p></div>
            <span class="badge" [class]="tenant()?.status === 'ACTIVE' ? 'badge success' : 'badge neutral'">{{ label(tenant()?.status) }}</span>
          </div>
          <div class="card-body">
            @if (tenantError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ tenantError() }}</div> }
            @if (canManageTenant()) {
              <div class="form-grid">
                <div class="field span-2">
                  <label for="tenantName">Nombre *</label>
                  <input id="tenantName" name="tenantName" class="input" maxlength="200" [(ngModel)]="tenantForm.name">
                </div>
                <div class="field">
                  <label for="tenantNit">NIT</label>
                  <input id="tenantNit" name="tenantNit" class="input" maxlength="30" [(ngModel)]="tenantForm.nit">
                </div>
                <div class="field">
                  <label for="tenantPhone">Teléfono</label>
                  <input id="tenantPhone" name="tenantPhone" class="input" maxlength="40" [(ngModel)]="tenantForm.phone">
                </div>
                <div class="field span-2">
                  <label for="tenantAddress">Dirección</label>
                  <input id="tenantAddress" name="tenantAddress" class="input" maxlength="250" [(ngModel)]="tenantForm.address">
                </div>
                <div class="field">
                  <label for="tenantCity">Ciudad</label>
                  <input id="tenantCity" name="tenantCity" class="input" maxlength="100" [(ngModel)]="tenantForm.city">
                </div>
                <div class="field">
                  <label for="tenantDepartment">Departamento</label>
                  <input id="tenantDepartment" name="tenantDepartment" class="input" maxlength="100" [(ngModel)]="tenantForm.department">
                </div>
                <div class="field span-2">
                  <label for="tenantEmail">Correo de la copropiedad</label>
                  <input id="tenantEmail" name="tenantEmail" type="email" class="input" maxlength="254" [(ngModel)]="tenantForm.email">
                </div>
              </div>
              <div class="form-actions">
                <button type="button" class="btn btn-primary" [disabled]="savingTenant()" (click)="saveTenant()">
                  {{ savingTenant() ? 'Guardando...' : 'Guardar datos' }}
                </button>
              </div>
            } @else if (tenant(); as current) {
              <dl class="detail-list">
                <dt>Nombre</dt><dd>{{ current.name }}</dd>
                <dt>NIT</dt><dd>{{ current.nit || '—' }}</dd>
                <dt>Identificador</dt><dd class="mono">{{ current.slug }}</dd>
                <dt>Dirección</dt><dd>{{ current.address || '—' }}</dd>
                <dt>Ciudad</dt><dd>{{ current.city || '—' }}{{ current.department ? ' · ' + current.department : '' }}</dd>
                <dt>Teléfono</dt><dd>{{ current.phone || '—' }}</dd>
                <dt>Correo</dt><dd>{{ current.email || '—' }}</dd>
                <dt>Moneda</dt><dd>{{ current.currency || 'COP' }}</dd>
                <dt>Zona horaria</dt><dd>{{ current.timezone || 'America/Bogota' }}</dd>
                <dt>Idioma</dt><dd>{{ current.locale || 'es-CO' }}</dd>
              </dl>
            }
          </div>
        </div>

        <div class="card">
          <div class="card-header"><div><h2>Funcionalidades activas</h2><p>Módulos habilitados para esta copropiedad.</p></div></div>
          <div class="card-body">
            @if (features().length) {
              <div class="grid" style="gap:9px">
                @for (feature of features(); track feature.key) {
                  <div class="feature-row" [class.enabled]="feature.enabled">
                    <span>
                      <strong>{{ label(feature.key) }}</strong>
                      <small>{{ feature.enabled ? 'Habilitada' : 'Deshabilitada' }}</small>
                    </span>
                    @if (canManageTenant() && isSelfService(feature.key)) {
                      <label class="check" style="margin:0">
                        <input type="checkbox" [checked]="feature.enabled" [disabled]="savingFeature() === feature.key"
                               (change)="toggleFeature(feature.key, $any($event.target).checked)">
                        <span>{{ feature.enabled ? 'Activa' : 'Inactiva' }}</span>
                      </label>
                    } @else {
                      <span class="badge" [class]="feature.enabled ? 'badge success' : 'badge neutral'">{{ feature.enabled ? 'Activa' : 'Inactiva' }}</span>
                    }
                  </div>
                }
              </div>
              @if (canManageTenant()) {
                <div class="notice" style="margin-top:14px">Puedes activar WhatsApp, cámaras, LPR y asambleas. Los pagos en línea y el estado de la copropiedad los administra la plataforma.</div>
              }
            } @else {
              <app-empty-state symbol="⚙" title="Sin funcionalidades" message="No hay funcionalidades adicionales configuradas para esta copropiedad." />
            }
          </div>
        </div>
      </div>

      @if (auth.hasPermission('NOTIFICATIONS_MANAGE')) {
        <div class="card">
          <div class="card-header"><div><h2>Canales de notificación</h2><p>Configuración de correo, push y WhatsApp para los avisos de la copropiedad.</p></div></div>
          <div class="card-body">
            @if (settingsError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ settingsError() }}</div> }
            @if (loadingSettings()) {
              <div class="loading"><span class="spinner"></span> Cargando canales...</div>
            } @else {
              <div class="form-grid">
                <div class="field span-2"><label for="senderName">Nombre del remitente *</label><input id="senderName" name="senderName" class="input" maxlength="100" [(ngModel)]="notificationForm.senderDisplayName"><span class="hint">Nombre visible en los correos enviados a la comunidad.</span></div>
                <div class="field"><label class="check"><input type="checkbox" [(ngModel)]="notificationForm.emailEnabled"> Notificaciones por correo</label></div>
                <div class="field"><label class="check"><input type="checkbox" [(ngModel)]="notificationForm.pushEnabled"> Notificaciones push</label></div>
                <div class="field"><label class="check"><input type="checkbox" [(ngModel)]="notificationForm.whatsappEnabled"> Notificaciones por WhatsApp</label></div>
              </div>
              <div class="form-actions">
                <button type="button" class="btn btn-primary" [disabled]="savingSettings()" (click)="saveNotifications()">Guardar canales</button>
              </div>
            }
          </div>
        </div>
      }

      <div class="card">
        <div class="card-header"><div><h2>Accesos de configuración</h2><p>Otras secciones relacionadas con la operación de la copropiedad.</p></div></div>
        <div class="card-body">
          <div class="grid grid-3">
            @if (auth.hasPermission('FINANCE_VIEW')) {
              <a class="btn btn-secondary" style="justify-content:flex-start;min-height:52px" routerLink="/app/cartera">Reglas de cartera y cobro</a>
            }
            @if (auth.hasPermission('PAYROLL_VIEW')) {
              <a class="btn btn-secondary" style="justify-content:flex-start;min-height:52px" routerLink="/app/nomina">Nómina y empleados</a>
            }
            @if (auth.hasPermission('ROLES_MANAGE')) {
              <a class="btn btn-secondary" style="justify-content:flex-start;min-height:52px" routerLink="/app/roles">Roles y permisos</a>
            }
            @if (auth.hasPermission('NOTIFICATIONS_MANAGE')) {
              <a class="btn btn-secondary" style="justify-content:flex-start;min-height:52px" routerLink="/app/notificaciones">Historial de notificaciones</a>
            }
            @if (auth.hasPermission('AUDIT_VIEW')) {
              <a class="btn btn-secondary" style="justify-content:flex-start;min-height:52px" routerLink="/app/auditoria">Auditoría de la copropiedad</a>
            }
            @if (auth.hasPermission('USERS_VIEW')) {
              <a class="btn btn-secondary" style="justify-content:flex-start;min-height:52px" routerLink="/app/usuarios">Usuarios y accesos</a>
            }
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .feature-row {
      display: flex; align-items: center; justify-content: space-between; gap: 10px;
      border: 1px solid var(--line); border-radius: 9px; padding: 10px 12px; background: #fbfdfe;
    }
    .feature-row.enabled { background: #f0fdfa; border-color: #99f6e4; }
    .feature-row strong { display: block; font-size: 12.5px; color: var(--ink-soft); }
    .feature-row small { display: block; font-size: 11px; color: var(--muted); margin-top: 2px; }
  `]
})
export class SettingsPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tenant = signal<TenantView | null>(null);
  readonly loading = signal(true);
  readonly loadingSettings = signal(false);
  readonly savingSettings = signal(false);
  readonly savingTenant = signal(false);
  readonly savingFeature = signal('');
  readonly tenantError = signal('');
  readonly settingsError = signal('');
  readonly features = signal<Array<{ key: string; enabled: boolean }>>([]);
  tenantForm: UpdateTenantRequest = { name: '' };
  notificationForm: UpdateSettingsRequest = {
    emailEnabled: false,
    pushEnabled: false,
    whatsappEnabled: false,
    senderDisplayName: ''
  };

  private readonly selfServiceFeatures = new Set(['WHATSAPP', 'CAMERAS', 'LPR', 'ASSEMBLIES']);

  constructor() {
    void this.load();
  }

  canManageTenant(): boolean {
    return this.auth.hasPermission('TENANT_SETTINGS_MANAGE');
  }

  isSelfService(key: string): boolean {
    return this.selfServiceFeatures.has(key);
  }

  async load(): Promise<void> {
    this.loading.set(true);
    try {
      const tenant = await firstValueFrom(this.api.get<TenantView>('/tenants/current'));
      this.applyTenant(tenant);
      if (this.auth.hasPermission('NOTIFICATIONS_MANAGE')) await this.loadNotifications();
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  async saveTenant(): Promise<void> {
    this.tenantError.set('');
    if (!this.tenantForm.name.trim()) {
      this.tenantError.set('El nombre de la copropiedad es obligatorio.');
      return;
    }
    this.savingTenant.set(true);
    try {
      const updated = await firstValueFrom(this.api.put<TenantView>('/tenants/current', {
        ...this.tenantForm,
        name: this.tenantForm.name.trim()
      }));
      this.applyTenant(updated);
      this.toast.success('Datos de la copropiedad guardados.');
    } catch (error) {
      this.tenantError.set(describeError(error));
    } finally {
      this.savingTenant.set(false);
    }
  }

  async toggleFeature(key: string, enabled: boolean): Promise<void> {
    this.savingFeature.set(key);
    try {
      const features = await firstValueFrom(this.api.put<Record<string, boolean>>(
        `/tenants/current/features/${key.toLowerCase()}`, { enabled }));
      this.features.set(Object.entries(features ?? {}).map(([feature, active]) => ({ key: feature, enabled: active })));
      this.toast.success(`${label(key)} ${enabled ? 'activada' : 'desactivada'}.`);
    } catch (error) {
      this.toast.error(describeError(error));
      await this.load();
    } finally {
      this.savingFeature.set('');
    }
  }

  async loadNotifications(): Promise<void> {
    this.loadingSettings.set(true);
    this.settingsError.set('');
    try {
      const settings = await firstValueFrom(this.api.get<TenantNotificationSettings>('/notifications/settings'));
      this.notificationForm = {
        emailEnabled: settings.emailEnabled ?? false,
        pushEnabled: settings.pushEnabled ?? false,
        whatsappEnabled: settings.whatsappEnabled ?? false,
        senderDisplayName: settings.senderDisplayName ?? ''
      };
    } catch (error) {
      this.settingsError.set(describeError(error));
    } finally {
      this.loadingSettings.set(false);
    }
  }

  async saveNotifications(): Promise<void> {
    this.settingsError.set('');
    if (!this.notificationForm.senderDisplayName.trim()) {
      this.settingsError.set('Indica el nombre del remitente.');
      return;
    }
    this.savingSettings.set(true);
    try {
      await firstValueFrom(this.api.put<TenantNotificationSettings>('/notifications/settings', {
        ...this.notificationForm,
        senderDisplayName: this.notificationForm.senderDisplayName.trim()
      }));
      this.toast.success('Canales de notificación guardados.');
    } catch (error) {
      this.settingsError.set(describeError(error));
    } finally {
      this.savingSettings.set(false);
    }
  }

  private applyTenant(tenant: TenantView): void {
    this.tenant.set(tenant);
    this.tenantForm = {
      name: tenant.name ?? '',
      nit: tenant.nit,
      address: tenant.address,
      city: tenant.city,
      department: tenant.department,
      phone: tenant.phone,
      email: tenant.email
    };
    this.features.set(Object.entries(tenant.features ?? {}).map(([key, enabled]) => ({ key, enabled })));
  }
}
