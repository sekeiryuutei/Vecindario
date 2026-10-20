import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
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
    <app-page-header title="Roles y permisos" subtitle="Ajusta los permisos efectivos de cada rol para esta copropiedad sin alterar las demás." />

    <div class="grid" style="grid-template-columns:300px minmax(0,1fr)">
      <div class="card">
        <div class="card-header"><div><h2>Roles</h2><p>Selecciona un rol para configurarlo.</p></div></div>
        @if (loading()) {
          <div class="loading"><span class="spinner"></span> Cargando roles…</div>
        } @else {
          <div class="card-body" style="padding:10px">
            @for (role of roles(); track role.role) {
              <button
                type="button"
                class="nav-role"
                [class.active]="selected()?.role === role.role"
                (click)="select(role)">
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
            <span class="badge" [class]="role.editable ? 'badge info' : 'badge neutral'">{{ role.permissions?.length ?? 0 }} permisos</span>
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
              <app-empty-state symbol="⌕" title="Sin coincidencias" message="No hay permisos que coincidan con la búsqueda." />
            } @else {
              <div class="grid grid-2" style="gap:8px">
                @for (permission of filteredPermissions(); track permission) {
                  <label class="check permission-row" [class.locked]="!role.editable">
                    <input
                      type="checkbox"
                      [checked]="has(role, permission)"
                      [disabled]="!role.editable || saving()"
                      (change)="toggle(role, permission, $any($event.target).checked)">
                    <span>
                      <strong>{{ permissionLabel(permission) }}</strong>
                      <small>{{ permission }}</small>
                    </span>
                  </label>
                }
              </div>
            }
          </div>
        } @else {
          <app-empty-state symbol="⚿" title="Selecciona un rol" message="Elige un rol de la lista para revisar y ajustar sus permisos." />
        }
      </div>
    </div>
  `,
  styles: [`
    .nav-role {
      width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 10px;
      padding: 10px 12px; border: 1px solid transparent; border-radius: 9px; background: transparent;
      color: var(--ink-soft); cursor: pointer; text-align: left;
    }
    .nav-role:hover { background: #f8fafc; }
    .nav-role.active { background: #f0fdfa; border-color: #99f6e4; color: var(--brand-dark); }
    .nav-role strong { display: block; font-size: 13px; }
    .nav-role small { display: block; font-size: 11px; color: var(--muted); margin-top: 2px; }
    .permission-row { align-items: flex-start; gap: 9px; padding: 9px 10px; border: 1px solid var(--line); border-radius: 9px; }
    .permission-row.locked { opacity: .65; }
    .permission-row strong { display: block; font-size: 12.5px; color: var(--ink-soft); font-weight: 600; }
    .permission-row small { display: block; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 10.5px; color: var(--muted); margin-top: 2px; }
    @media (max-width: 900px) {
      .grid { grid-template-columns: 1fr !important; }
    }
  `]
})
export class RolesPage {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly roles = signal<RoleView[]>([]);
  readonly permissions = signal<string[]>([]);
  readonly selected = signal<RoleView | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  search = '';

  constructor() {
    void this.load();
  }

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
      this.selected.set(updated);
      this.toast.success(granted ? 'Permiso concedido.' : 'Permiso retirado.');
    } catch (error) {
      this.toast.error(describeError(error));
      await this.load();
    } finally {
      this.saving.set(false);
    }
  }

  permissionLabel(permission: string): string {
    const text = permission.replaceAll('_', ' ').toLowerCase();
    return text.charAt(0).toUpperCase() + text.slice(1);
  }
}
