import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { ConfirmService } from '../core/confirm.service';
import { roleLabel } from '../core/labels';
import { TenantContextService } from '../core/tenant-context.service';
import { ToastService } from '../core/toast.service';
import { describeError, initials } from '../core/utils';
import { PageHeaderComponent } from '../shared/ui.components';

@Component({
  selector: 'app-profile-page',
  standalone: true,
  imports: [FormsModule, RouterLink, PageHeaderComponent],
  template: `
    <app-page-header title="Mi cuenta" subtitle="Datos de acceso, copropiedad activa y seguridad de la sesión." />

    <div class="grid grid-2">
      <div class="card">
        <div class="card-header"><h2>Perfil</h2></div>
        <div class="card-body">
          <div class="row" style="gap:14px;margin-bottom:18px">
            <span class="avatar" style="width:52px;height:52px;font-size:18px;background:#ccfbf1;color:#0f766e">{{ userInitials() }}</span>
            <div>
              <div style="font-weight:700;font-size:16px">{{ auth.user()?.fullName }}</div>
              <div class="muted small">{{ auth.user()?.email }}</div>
            </div>
          </div>
          <dl class="detail-list">
            <dt>Rol en la copropiedad</dt><dd>{{ roleName() }}</dd>
            <dt>Copropiedad activa</dt><dd>{{ tenant()?.name || '—' }}</dd>
            <dt>NIT</dt><dd>{{ tenant()?.nit || '—' }}</dd>
            <dt>Ciudad</dt><dd>{{ tenant()?.city || '—' }}{{ tenant()?.department ? ', ' + tenant()?.department : '' }}</dd>
            <dt>Moneda</dt><dd>{{ tenant()?.currency || 'COP' }}</dd>
            <dt>Zona horaria</dt><dd>{{ tenant()?.timezone || '—' }}</dd>
            <dt>Permisos activos</dt><dd>{{ auth.permissions().size }}</dd>
          </dl>
          <div class="row" style="margin-top:18px">
            @if (auth.tenants().length > 1) {
              <a class="btn btn-secondary btn-sm" routerLink="/seleccionar-copropiedad">Cambiar copropiedad</a>
            }
            <button type="button" class="btn btn-danger btn-sm" (click)="logout()">Cerrar sesión</button>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-header"><h2>Cambiar contraseña</h2></div>
        <div class="card-body">
          @if (message()) { <div class="alert" [class.alert-success]="success()" [class.alert-error]="!success()" style="margin-bottom:14px">{{ message() }}</div> }
          <form (ngSubmit)="changePassword()" novalidate>
            <div class="stack">
              <div class="field">
                <label for="current">Contraseña actual</label>
                <input id="current" name="current" type="password" class="input" autocomplete="current-password" required [(ngModel)]="form.currentPassword">
              </div>
              <div class="field">
                <label for="new">Nueva contraseña</label>
                <input id="new" name="new" type="password" class="input" autocomplete="new-password" required [(ngModel)]="form.newPassword">
                <span class="hint">Mínimo 8 caracteres.</span>
              </div>
              <div class="field">
                <label for="confirm">Confirmar nueva contraseña</label>
                <input id="confirm" name="confirm" type="password" class="input" autocomplete="new-password" required [(ngModel)]="form.confirmation">
              </div>
              <div class="form-actions" style="margin-top:4px">
                <button type="submit" class="btn btn-primary" [disabled]="saving()">
                  @if (saving()) { <span class="spinner" style="border-color:rgba(255,255,255,.4);border-top-color:#fff"></span> }
                  Actualizar contraseña
                </button>
              </div>
            </div>
          </form>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-header">
        <div>
          <h2>Permisos de tu rol</h2>
          <p>Los permisos son asignados por la administración y se validan en cada solicitud al servidor.</p>
        </div>
      </div>
      <div class="card-body">
        <div class="tag-list">
          @for (permission of permissionList(); track permission) {
            <span class="chip mono">{{ permission }}</span>
          } @empty {
            <span class="muted small">No hay permisos asociados al rol activo.</span>
          }
        </div>
      </div>
    </div>
  `
})
export class ProfilePage {
  readonly auth = inject(AuthService);
  private readonly tenantContext = inject(TenantContextService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly saving = signal(false);
  readonly message = signal('');
  readonly success = signal(false);
  readonly userInitials = computed(() => initials(this.auth.user()?.fullName));
  readonly roleName = computed(() => roleLabel(this.auth.activeRole()));
  readonly tenant = this.tenantContext.tenant;
  readonly permissionList = computed(() => Array.from(this.auth.permissions()).sort());
  form = { currentPassword: '', newPassword: '', confirmation: '' };

  async changePassword(): Promise<void> {
    this.message.set('');
    if (this.form.newPassword.length < 8) {
      this.success.set(false);
      this.message.set('La nueva contraseña debe tener al menos 8 caracteres.');
      return;
    }
    if (this.form.newPassword !== this.form.confirmation) {
      this.success.set(false);
      this.message.set('La confirmación no coincide con la nueva contraseña.');
      return;
    }
    this.saving.set(true);
    try {
      await this.auth.changePassword({
        currentPassword: this.form.currentPassword,
        newPassword: this.form.newPassword
      });
      this.success.set(true);
      this.message.set('Contraseña actualizada. Las demás sesiones deberán autenticarse nuevamente.');
      this.form = { currentPassword: '', newPassword: '', confirmation: '' };
      this.toast.success('Contraseña actualizada.');
    } catch (error) {
      this.success.set(false);
      this.message.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async logout(): Promise<void> {
    const accepted = await this.confirm.confirm({
      title: 'Cerrar sesión',
      message: '¿Deseas salir del portal en este dispositivo?',
      confirmLabel: 'Cerrar sesión',
      danger: true
    });
    if (accepted) await this.auth.logout();
  }
}
