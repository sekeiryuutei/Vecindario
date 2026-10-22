import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ConfirmService } from '../../core/confirm.service';
import { label } from '../../core/labels';
import { PermissionChangeRequest, RoleView } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, PageHeaderComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-roles-page',
  standalone: true,
  imports: [FormsModule, PageHeaderComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Roles y permisos" subtitle="Matriz de permisos por rol para esta copropiedad. Los cambios no alteran las demás copropiedades.">
      <button type="button" class="btn" [class.btn-primary]="view() === 'DETAIL'" [class.btn-secondary]="view() !== 'DETAIL'" (click)="view.set('DETAIL')">Por rol</button>
      <button type="button" class="btn" [class.btn-primary]="view() === 'MATRIX'" [class.btn-secondary]="view() !== 'MATRIX'" (click)="view.set('MATRIX')">Matriz</button>
      <button type="button" class="btn btn-secondary" (click)="exportCsv()">Exportar CSV</button>
    </app-page-header>

    @if (view() === 'DETAIL') {
      <div class="grid roles-layout">
        <div class="card">
          <div class="card-header"><div><h2>Roles</h2><p>Selecciona un rol para configurarlo.</p></div></div>
          @if (loading()) {
            <div class="loading"><span class="spinner"></span> Cargando roles…</div>
          } @else {
            <div class="card-body" style="padding:10px">
              @for (role of roles(); track role.role) {
                <button type="button" class="nav-role" [class.active]="selected()?.role === role.role" (click)="select(role)">
                  <span>
                    <strong>{{ label(role.role) }}</strong>
                    <small>{{ role.permissions?.length ?? 0 }} permisos{{ role.editable ? '' : ' · bloqueado' }}</small>
                  </span>
                  <span aria-hidden="true">›</span>
                </button>
              }
            </div>
          }
        </div>

        <div class="card">
          @if (selected(); as role) {
            <div class="card-header">
              <div><h2>{{ label(role.role) }}</h2><p>{{ role.editable ? 'Marca los permisos que debe tener este rol.' : 'El rol administrador conserva todos los permisos y no puede modificarse.' }}</p></div>
              <div class="table-actions">
                <span class="badge info">{{ role.permissions?.length ?? 0 }} permisos</span>
                @if (role.editable) { <button type="button" class="btn btn-secondary btn-sm" (click)="resetRole(role)">Restablecer</button> }
              </div>
            </div>
            <div class="card-body">
              @if (!role.editable) {
                <div class="alert alert-info" style="margin-bottom:14px">Este rol es inmutable: siempre conserva el conjunto completo de permisos de la copropiedad.</div>
              }
              <div class="field" style="margin-bottom:14px">
                <label for="search">Buscar permiso</label>
                <input id="search" name="search" class="input" [(ngModel)]="search" placeholder="Ej. FINANCE, VISITORS, PQR">
              </div>
              @if (!filteredPermissions().length) {
                <app-empty-state symbol="?" title="Sin coincidencias" message="No hay permisos que coincidan con la búsqueda." />
              } @else {
                <div class="grid grid-2" style="gap:8px">
                  @for (permission of filteredPermissions(); track permission) {
                    <label class="check permission-row" [class.locked]="!role.editable">
                      <input type="checkbox" [checked]="has(role, permission)" [disabled]="!role.editable || saving()" (change)="toggle(role, permission, $any($event.target).checked)">
                      <span><strong>{{ permissionLabel(permission) }}</strong><small>{{ permission }}</small></span>
                    </label>
                  }
                </div>
              }
            </div>
          } @else {
            <app-empty-state symbol="R" title="Selecciona un rol" message="Elige un rol de la lista para revisar y ajustar sus permisos." />
          }
        </div>
      </div>
    } @else {
      <div class="card">
        <div class="card-header">
          <div><h2>Matriz de roles y permisos</h2><p>Marca o desmarca una celda para ajustar el permiso en esta copropiedad.</p></div>
          <div class="field" style="margin:0;min-width:230px">
            <input class="input" name="matrixSearch" [(ngModel)]="search" placeholder="Buscar permiso…" aria-label="Buscar permiso">
          </div>
        </div>
        @if (loading()) {
          <div class="loading"><span class="spinner"></span> Cargando matriz.</div>
        } @else if (!filteredPermissions().length) {
          <app-empty-state symbol="?" title="Sin coincidencias" message="No hay permisos que coincidan con la búsqueda." />
        } @else {
          <div class="table-wrap matrix-wrap">
            <table class="table matrix">
              <thead>
                <tr>
                  <th class="permission-column">Permiso</th>
                  @for (role of roles(); track role.role) {
                    <th class="role-column"><span>{{ label(role.role) }}</span><small>{{ role.permissions?.length ?? 0 }}</small></th>
                  }
                </tr>
              </thead>
              <tbody>
                @for (permission of filteredPermissions(); track permission) {
                  <tr>
                    <td class="permission-column"><strong>{{ permissionLabel(permission) }}</strong><small>{{ permission }}</small></td>
                    @for (role of roles(); track role.role) {
                      <td class="matrix-cell">
                        <button type="button" class="matrix-toggle" [class.granted]="has(role, permission)" [class.locked]="!role.editable"
                                [disabled]="!role.editable || saving()" [attr.aria-label]="(has(role, permission) ? 'Quitar' : 'Conceder') + ' ' + permission + ' a ' + label(role.role)"
                                (click)="toggle(role, permission, !has(role, permission))">
                          {{ has(role, permission) ? '✓' : '—' }}
                        </button>
                      </td>
                    }
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    }
  `,
  styles: [`
    .roles-layout { grid-template-columns: 300px minmax(0, 1fr); }
    .nav-role { width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 10px 12px; border: 1px solid transparent; border-radius: 9px; background: transparent; color: var(--ink-soft); cursor: pointer; text-align: left; }
    .nav-role:hover { background: #f8fafc; }
    .nav-role.active { background: #f0fdfa; border-color: #99f6e4; color: var(--brand-dark); }
    .nav-role strong { display: block; font-size: 13px; }
    .nav-role small { display: block; font-size: 11px; color: var(--muted); margin-top: 2px; }
    .permission-row { align-items: flex-start; gap: 9px; padding: 9px 10px; border: 1px solid var(--line); border-radius: 9px; }
    .permission-row.locked { opacity: .65; }
    .permission-row strong { display: block; font-size: 12.5px; color: var(--ink-soft); font-weight: 600; }
    .permission-row small { display: block; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 10.5px; color: var(--muted); margin-top: 2px; }
    .matrix-wrap { max-height: 70vh; overflow: auto; }
    .matrix { min-width: 980px; }
    .matrix th { position: sticky; top: 0; z-index: 2; background: var(--surface, #fff); }
    .matrix .permission-column { position: sticky; left: 0; z-index: 1; background: var(--surface, #fff); min-width: 250px; text-align: left; }
    .matrix tbody .permission-column strong { display: block; font-size: 12px; }
    .matrix tbody .permission-column small { display: block; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 10px; color: var(--muted); margin-top: 2px; }
    .matrix .role-column { min-width: 92px; text-align: center; font-size: 11px; vertical-align: bottom; }
    .matrix .role-column span { display: block; }
    .matrix .role-column small { color: var(--muted); font-weight: 400; }
    .matrix-cell { text-align: center; padding: 4px !important; }
    .matrix-toggle { width: 34px; height: 30px; border-radius: 7px; border: 1px solid var(--line); background: #fff; color: var(--muted); cursor: pointer; font-weight: 700; }
    .matrix-toggle.granted { background: #ecfdf5; border-color: #a7f3d0; color: #047857; }
    .matrix-toggle.locked { cursor: not-allowed; opacity: .6; }
    .matrix-toggle:disabled { cursor: wait; }
    @media (max-width: 900px) { .roles-layout { grid-template-columns: 1fr !important; } }
  `]
})
export class RolesPage {
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly roles = signal<RoleView[]>([]);
  readonly permissions = signal<string[]>([]);
  readonly selected = signal<RoleView | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly view = signal<'DETAIL' | 'MATRIX'>('DETAIL');
  search = '';

  constructor() { void this.load(); }

  filteredPermissions(): string[] {
    const term = this.search.trim().toUpperCase();
    const catalog = this.permissions();
    if (!term) return catalog;
    return catalog.filter((permission) => permission.includes(term));
  }

  async load(): Promise<void> {
    this.loading.set(true);
    try {
      const [roles, permissions] = await Promise.all([
        firstValueFrom(this.api.get<RoleView[]>('/roles')),
        firstValueFrom(this.api.get<string[]>('/roles/permissions'))
      ]);
      this.roles.set(roles);
      this.permissions.set([...permissions].sort());
      const current = this.selected()?.role;
      const selected = roles.find((role) => role.role === current) ?? roles.find((role) => role.editable) ?? roles[0] ?? null;
      this.selected.set(selected);
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  select(role: RoleView): void {
    this.selected.set(role);
  }

  has(role: RoleView, permission: string): boolean {
    return Boolean(role.permissions?.includes(permission));
  }

  async toggle(role: RoleView, permission: string, granted: boolean): Promise<void> {
    if (!role.role || !role.editable) return;
    this.saving.set(true);
    try {
      const body: PermissionChangeRequest = { granted };
      const updated = await firstValueFrom(this.api.put<RoleView>(`/roles/${role.role}/permissions/${permission}`, body));
      this.roles.update((roles) => roles.map((item) => item.role === updated.role ? updated : item));
      if (this.selected()?.role === updated.role) this.selected.set(updated);
      this.toast.success(granted ? 'Permiso concedido.' : 'Permiso retirado.');
    } catch (error) {
      this.toast.error(describeError(error));
      await this.load();
    } finally {
      this.saving.set(false);
    }
  }

  async resetRole(role: RoleView): Promise<void> {
    if (!role.role || !role.editable) return;
    const ok = await this.confirm.confirm({
      title: 'Restablecer permisos',
      message: `¿Restablecer los permisos de ${label(role.role)} a los valores base de la plataforma?`,
      confirmLabel: 'Restablecer',
      danger: true
    });
    if (!ok) return;
    this.saving.set(true);
    try {
      const updated = await firstValueFrom(this.api.delete<RoleView>(`/roles/${role.role}/permissions`));
      this.roles.update((roles) => roles.map((item) => item.role === updated.role ? updated : item));
      this.selected.set(updated);
      this.toast.success('Permisos restablecidos.');
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  exportCsv(): void {
    const roles = this.roles();
    const permissions = this.filteredPermissions();
    const escape = (value: string) => `"${value.replaceAll('"', '""')}"`;
    const totalPermissions = permissions.length;
    const totalRoles = roles.length === 0 ? 1 : roles.length;
    const lines = [
      ['Permiso', ...roles.map((role) => label(role.role))].map(escape).join(','),
      ...permissions.map((permission) => [permission, ...roles.map((role) => this.has(role, permission) ? 'Sí' : 'No')].map(escape).join(','))
    ];
    const blob = new Blob(['\uFEFF' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'matriz-roles-permisos.csv';
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1500);
    this.toast.success(`Matriz exportada: ${totalPermissions} permisos × ${totalRoles} roles.`);
  }

  permissionLabel(permission: string): string {
    const text = permission.replaceAll('_', ' ').toLowerCase();
    return text.charAt(0).toUpperCase() + text.slice(1);
  }
}
