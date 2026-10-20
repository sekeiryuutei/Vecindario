import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ConfirmService } from '../../core/confirm.service';
import { label, statusTone } from '../../core/labels';
import { AccessHistoryEntry, AddMemberRequest, MemberView, PageResultMemberView } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-members-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, ModalComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Usuarios" subtitle="Gestiona quién puede acceder a la copropiedad, su rol y el estado de su membresía.">
      @if (canManage()) { <button type="button" class="btn btn-primary" (click)="openAdd()">Invitar usuario</button> }
    </app-page-header>

    <div class="card">
      <div class="card-header">
        <div><h2>Miembros de la copropiedad</h2><p>{{ totalElements() }} usuario(s) vinculados.</p></div>
        <button type="button" class="btn btn-secondary btn-sm" (click)="load(page())">Actualizar</button>
      </div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando usuarios…</div>
      } @else if (!items().length) {
        <app-empty-state symbol="◎" title="Sin usuarios" message="Invita al primer usuario para que acceda al portal de la copropiedad." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Usuario</th><th>Rol</th><th>Cuenta</th><th>Membresía</th><th>Vinculado</th><th>Última actividad</th><th style="width:210px"></th></tr></thead>
            <tbody>
              @for (member of items(); track member.userId) {
                <tr>
                  <td>
                    <div class="main-cell">{{ member.fullName || 'Sin nombre' }}</div>
                    <div class="muted small">{{ member.email }}</div>
                  </td>
                  <td><span class="chip">{{ label(member.roleCode) }}</span></td>
                  <td><span class="badge" [class]="'badge ' + tone(member.userStatus)">{{ label(member.userStatus) }}</span></td>
                  <td><span class="badge" [class]="'badge ' + tone(member.status)">{{ label(member.status) }}</span></td>
                  <td>{{ member.grantedAt | date:'dd/MM/yyyy' }}</td>
                  <td>{{ member.lastActivityAt | date:'dd/MM/yyyy HH:mm' }}</td>
                  <td>
                    <div class="table-actions">
                      <button type="button" class="btn-icon" title="Historial" (click)="openHistory(member)">⌕</button>
                      @if (canManage() && !isSelf(member)) {
                        @if (member.status === 'ACTIVE') {
                          <button type="button" class="btn-icon" title="Cambiar rol" (click)="openRole(member)">⇄</button>
                          <button type="button" class="btn-icon" title="Suspender" (click)="suspend(member)">Ⅱ</button>
                        }
                        @if (member.status === 'SUSPENDED') {
                          <button type="button" class="btn-icon" title="Reactivar" (click)="activate(member)">▶</button>
                        }
                        @if (member.status !== 'REVOKED') {
                          <button type="button" class="btn-icon danger" title="Revocar acceso" (click)="revoke(member)">⊗</button>
                        }
                      }
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-pagination [page]="page()" [totalPages]="totalPages()" [totalElements]="totalElements()" (pageChange)="load($event)" />
      }
    </div>

    <app-modal [open]="addOpen()" title="Invitar usuario" subtitle="El usuario recibirá acceso a esta copropiedad con el rol asignado." (closed)="addOpen.set(false)">
      @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
      <div class="form-grid">
        <div class="field span-2"><label for="fullName">Nombre completo *</label><input id="fullName" name="fullName" class="input" maxlength="200" [(ngModel)]="addForm.fullName"></div>
        <div class="field span-2"><label for="email">Correo electrónico *</label><input id="email" name="email" type="email" class="input" maxlength="254" [(ngModel)]="addForm.email"></div>
        <div class="field span-2">
          <label for="newRole">Rol *</label>
          <select id="newRole" name="newRole" class="select" [(ngModel)]="addForm.role">
            @for (role of roles; track role) { <option [value]="role">{{ label(role) }}</option> }
          </select>
          <span class="hint">El rol define los permisos iniciales del usuario en el portal.</span>
        </div>
      </div>
      <div class="form-actions">
        <button type="button" class="btn btn-secondary" (click)="addOpen.set(false)">Cancelar</button>
        <button type="button" class="btn btn-primary" [disabled]="saving()" (click)="add()">Invitar usuario</button>
      </div>
    </app-modal>

    <app-modal [open]="roleOpen()" title="Cambiar rol" [subtitle]="selected()?.fullName || selected()?.email || ''" [narrow]="true" (closed)="roleOpen.set(false)">
      <div class="field">
        <label for="roleSelect">Nuevo rol</label>
        <select id="roleSelect" name="roleSelect" class="select" [(ngModel)]="newRole">
          @for (role of roles; track role) { <option [value]="role">{{ label(role) }}</option> }
        </select>
      </div>
      <div class="form-actions">
        <button type="button" class="btn btn-secondary" (click)="roleOpen.set(false)">Cancelar</button>
        <button type="button" class="btn btn-primary" [disabled]="saving()" (click)="changeRole()">Guardar rol</button>
      </div>
    </app-modal>

    <app-modal [open]="historyOpen()" title="Historial de acceso" [subtitle]="selected()?.fullName || selected()?.email || ''" (closed)="historyOpen.set(false)">
      @if (loadingHistory()) {
        <div class="loading"><span class="spinner"></span> Cargando historial…</div>
      } @else if (!history().length) {
        <app-empty-state symbol="⌕" title="Sin movimientos" message="No hay eventos registrados para este usuario." />
      } @else {
        <div class="timeline">
          @for (entry of history(); track $index) {
            <div class="timeline-item">
              <span class="timeline-dot"></span>
              <div class="timeline-content">
                <div class="row between">
                  <strong style="font-size:13px">{{ label(entry.event) }}</strong>
                  <span class="muted small">{{ entry.occurredAt | date:'dd/MM/yyyy HH:mm' }}</span>
                </div>
                <div class="muted small">{{ label(entry.roleCode) }} · IP {{ entry.ip || '—' }}</div>
              </div>
            </div>
          }
        </div>
      }
    </app-modal>
  `
})
export class MembersPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly roles = [
    'ADMINISTRADOR',
    'SECRETARIA_ADMINISTRACION',
    'CONTADOR',
    'CONSEJO',
    'PROPIETARIO',
    'ARRENDATARIO',
    'RESIDENTE',
    'PORTERO',
    'SUPERVISOR_PORTERIA',
    'MANTENIMIENTO',
    'PROVEEDOR'
  ];
  readonly items = signal<MemberView[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly loadingHistory = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly addOpen = signal(false);
  readonly roleOpen = signal(false);
  readonly historyOpen = signal(false);
  readonly error = signal('');
  readonly selected = signal<MemberView | null>(null);
  readonly history = signal<AccessHistoryEntry[]>([]);
  addForm: AddMemberRequest = { email: '', fullName: '', role: 'RESIDENTE' };
  newRole = 'RESIDENTE';

  constructor() {
    void this.load(0);
  }

  canManage(): boolean {
    return this.auth.hasPermission('USERS_MANAGE');
  }

  isSelf(member: MemberView): boolean {
    return Boolean(member.userId && member.userId === this.auth.user()?.id);
  }

  async load(page = 0): Promise<void> {
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultMemberView>('/tenant-members', { page, size: 20 }));
      this.items.set(result.content ?? []);
      this.page.set(result.page ?? 0);
      this.totalPages.set(result.totalPages ?? 0);
      this.totalElements.set(result.totalElements ?? 0);
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  openAdd(): void {
    this.error.set('');
    this.addForm = { email: '', fullName: '', role: 'RESIDENTE' };
    this.addOpen.set(true);
  }

  async add(): Promise<void> {
    this.error.set('');
    if (!this.addForm.email.trim() || !this.addForm.fullName.trim() || !this.addForm.role) {
      this.error.set('Completa el nombre, el correo y el rol del usuario.');
      return;
    }
    this.saving.set(true);
    try {
      await firstValueFrom(this.api.post<MemberView>('/tenant-members', {
        email: this.addForm.email.trim(),
        fullName: this.addForm.fullName.trim(),
        role: this.addForm.role
      }));
      this.toast.success('Invitación registrada.');
      this.addOpen.set(false);
      await this.load(0);
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  openRole(member: MemberView): void {
    this.selected.set(member);
    this.newRole = member.roleCode ?? 'RESIDENTE';
    this.roleOpen.set(true);
  }

  async changeRole(): Promise<void> {
    const member = this.selected();
    if (!member?.userId) return;
    this.saving.set(true);
    try {
      await firstValueFrom(this.api.patch<MemberView>(`/tenant-members/${member.userId}/role`, { role: this.newRole }));
      this.toast.success('Rol actualizado.');
      this.roleOpen.set(false);
      await this.load(this.page());
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async suspend(member: MemberView): Promise<void> {
    if (!member.userId) return;
    const accepted = await this.confirm.confirm({
      title: 'Suspender acceso',
      message: `¿Suspender el acceso de ${member.fullName || member.email}? Podrás reactivarlo más adelante.`,
      confirmLabel: 'Suspender',
      danger: true
    });
    if (!accepted) return;
    try {
      await firstValueFrom(this.api.post<MemberView>(`/tenant-members/${member.userId}/suspend`));
      this.toast.success('Acceso suspendido.');
      await this.load(this.page());
    } catch (error) {
      this.toast.error(describeError(error));
    }
  }

  async activate(member: MemberView): Promise<void> {
    if (!member.userId) return;
    try {
      await firstValueFrom(this.api.post<MemberView>(`/tenant-members/${member.userId}/activate`));
      this.toast.success('Acceso reactivado.');
      await this.load(this.page());
    } catch (error) {
      this.toast.error(describeError(error));
    }
  }

  async revoke(member: MemberView): Promise<void> {
    if (!member.userId) return;
    const accepted = await this.confirm.confirm({
      title: 'Revocar acceso',
      message: `¿Revocar definitivamente el acceso de ${member.fullName || member.email}? El historial se conserva, pero el usuario no podrá volver a ingresar a esta copropiedad sin una nueva invitación.`,
      confirmLabel: 'Revocar',
      danger: true
    });
    if (!accepted) return;
    try {
      await firstValueFrom(this.api.delete<void>(`/tenant-members/${member.userId}`));
      this.toast.success('Acceso revocado.');
      await this.load(this.page());
    } catch (error) {
      this.toast.error(describeError(error));
    }
  }

  async openHistory(member: MemberView): Promise<void> {
    if (!member.userId) return;
    this.selected.set(member);
    this.history.set([]);
    this.historyOpen.set(true);
    this.loadingHistory.set(true);
    try {
      this.history.set(await firstValueFrom(this.api.get<AccessHistoryEntry[]>(`/tenant-members/${member.userId}/history`)));
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.loadingHistory.set(false);
    }
  }
}
