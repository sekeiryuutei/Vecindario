import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { label } from '../../core/labels';
import { TenantNotificationSettings, TenantView, UpdateSettingsRequest } from '../../core/models';
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
      <div class="loading"><span class="spinner"></span> Cargando configuración…</div>
    } @else {
      <div class="grid grid-2">
        <div class="card">
          <div class="card-header"><div><h2>Datos de la copropiedad</h2><p>Información estructural registrada en la plataforma.</p></div><span class="badge" [class]="tenant()?.status === 'ACTIVE' ? 'badge success' : 'badge neutral'">{{ label(tenant()?.status) }}</span></div>
          @if (tenant(); as current) {
            <div class="card-body">
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
              <div class="notice" style="margin-top:16px">Los datos estructurales y el estado de la copropiedad son administrados desde la plataforma. Los cambios de roles y permisos se realizan en <strong>Roles y permisos</strong>.</div>
            </div>
          }
        </div>

        <div class="card">
          <div class="card-header"><div><h2>Funcionalidades activas</h2><p>Módulos habilitados para esta copropiedad.</p></div></div>
          <div class="card-body">
            @if (features().length) {
              <div class="grid grid-2" style="gap:9px">
                @for (feature of features(); track feature.key) {
                  <div class="feature-row" [class.enabled]="feature.enabled">
                    <span>
                      <strong>{{ label(feature.key) }}</strong>
                      <small>{{ feature.enabled ? 'Habilitada' : 'Deshabilitada' }}</small>
                    </span>
                    <span class="badge" [class]="feature.enabled ? 'badge success' : 'badge neutral'">{{ feature.enabled ? 'Activa' : 'Inactiva' }}</span>
                  </div>
                }
              </div>
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
              <div class="loading"><span class="spinner"></span> Cargando canales…</div>
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
  readonly settingsError = signal('');
  readonly features = signal<Array<{ key: string; enabled: boolean }>>([]);
  notificationForm: UpdateSettingsRequest = {
    emailEnabled: false,
    pushEnabled: false,
    whatsappEnabled: false,
    senderDisplayName: ''
  };

  constructor() {
    void this.load();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    try {
      const tenant = await firstValueFrom(this.api.get<TenantView>('/tenants/current'));
      this.tenant.set(tenant);
      this.features.set(Object.entries(tenant.features ?? {}).map(([key, enabled]) => ({ key, enabled })));
      if (this.auth.hasPermission('NOTIFICATIONS_MANAGE')) await this.loadNotifications();
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.loading.set(false);
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
}
