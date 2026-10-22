import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ConfirmService } from '../../core/confirm.service';
import { label } from '../../core/labels';
import { PageResultStaffMember, StaffMember, StaffRequest, StaffStatus } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError, today } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-staff-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, ModalComponent, EmptyStateComponent, PaginationComponent],
  template: `
    <app-page-header title="Personal operativo" subtitle="Directorio del equipo de portería, aseo, mantenimiento y servicios de la copropiedad.">
      @if (auth.hasPermission('STAFF_MANAGE')) {
        <button type="button" class="btn btn-primary" (click)="openCreate()">Registrar persona</button>
      }
    </app-page-header>

    <div class="filter-bar">
      <div class="field">
        <label for="staffSearch">Buscar</label>
        <input id="staffSearch" name="staffSearch" class="input" [(ngModel)]="q" (keyup.enter)="load(0)" placeholder="Nombre, documento, cargo o área">
      </div>
      <div class="field">
        <label for="staffStatus">Estado</label>
        <select id="staffStatus" name="staffStatus" class="select" [(ngModel)]="statusFilter" (change)="load(0)">
          <option value="">Todos</option>
          <option value="ACTIVE">Activos</option>
          <option value="INACTIVE">Inactivos</option>
        </select>
      </div>
      <button type="button" class="btn btn-secondary" (click)="load(0)">Filtrar</button>
      <button type="button" class="btn btn-ghost" (click)="clearFilters()">Limpiar</button>
    </div>

    <div class="card">
      <div class="card-header"><div><h2>Equipo</h2><p>{{ totalElements() }} persona(s).</p></div><button type="button" class="btn btn-secondary btn-sm" (click)="load(page())">Actualizar</button></div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando personal.</div>
      } @else if (!items().length) {
        <app-empty-state symbol="PE" title="Sin personal" message="No hay personal que coincida con los filtros." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Nombre</th><th>Cargo / área</th><th>Contacto</th><th>Turno</th><th>Ingreso</th><th>Estado</th><th style="width:160px"></th></tr></thead>
            <tbody>
              @for (item of items(); track item.id) {
                <tr>
                  <td class="main-cell">
                    <div>{{ item.fullName }}</div>
                    <div class="muted">{{ item.documentType }} {{ item.documentNumber }}</div>
                  </td>
                  <td>
                    <div>{{ item.position }}</div>
                    <div class="muted">{{ item.area || 'Sin área' }}</div>
                  </td>
                  <td>
                    <div>{{ item.phone || 'Sin teléfono' }}</div>
                    <div class="muted">{{ item.email || 'Sin correo' }}</div>
                  </td>
                  <td>{{ item.shift || '—' }}</td>
                  <td>{{ item.startDate | date:'dd/MM/yyyy' }}</td>
                  <td><span class="badge" [class]="'badge ' + (item.status === 'ACTIVE' ? 'success' : 'neutral')">{{ label(item.status) }}</span></td>
                  <td>
                    @if (auth.hasPermission('STAFF_MANAGE')) {
                      <div class="table-actions">
                        <button type="button" class="btn btn-secondary btn-sm" (click)="openEdit(item)">Editar</button>
                        <button type="button" class="btn btn-danger btn-sm" (click)="remove(item)">Eliminar</button>
                      </div>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-pagination [page]="page()" [totalPages]="totalPages()" [totalElements]="totalElements()" (pageChange)="load($event)" />
      }
    </div>

    <app-modal [open]="modalOpen()" [title]="editing() ? 'Editar persona' : 'Registrar persona'" subtitle="Datos operativos del equipo; la nómina se administra en su propio módulo." (closed)="modalOpen.set(false)">
      @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
      <form (ngSubmit)="save()" novalidate>
        <div class="form-grid">
          <div class="field span-2"><label for="staffName">Nombre completo *</label><input id="staffName" name="staffName" class="input" required maxlength="200" [(ngModel)]="form.fullName"></div>
          <div class="field">
            <label for="staffDocType">Tipo de documento</label>
            <select id="staffDocType" name="staffDocType" class="select" [(ngModel)]="form.documentType">
              <option value="CC">Cédula de ciudadanía</option>
              <option value="CE">Cédula de extranjería</option>
              <option value="PAS">Pasaporte</option>
            </select>
          </div>
          <div class="field"><label for="staffDoc">Número de documento *</label><input id="staffDoc" name="staffDoc" class="input mono" required maxlength="30" [(ngModel)]="form.documentNumber"></div>
          <div class="field"><label for="staffPosition">Cargo *</label><input id="staffPosition" name="staffPosition" class="input" required maxlength="100" [(ngModel)]="form.position"></div>
          <div class="field"><label for="staffArea">Área</label><input id="staffArea" name="staffArea" class="input" maxlength="100" [(ngModel)]="form.area"></div>
          <div class="field"><label for="staffPhone">Teléfono</label><input id="staffPhone" name="staffPhone" class="input" maxlength="40" [(ngModel)]="form.phone"></div>
          <div class="field"><label for="staffEmail">Correo</label><input id="staffEmail" name="staffEmail" type="email" class="input" maxlength="254" [(ngModel)]="form.email"></div>
          <div class="field span-2"><label for="staffShift">Turno</label><input id="staffShift" name="staffShift" class="input" maxlength="80" [(ngModel)]="form.shift"></div>
          <div class="field"><label for="staffStart">Fecha de ingreso *</label><input id="staffStart" name="staffStart" type="date" class="input" required [(ngModel)]="form.startDate"></div>
          <div class="field"><label for="staffEnd">Fecha de retiro</label><input id="staffEnd" name="staffEnd" type="date" class="input" [(ngModel)]="form.endDate"></div>
          <div class="field">
            <label for="staffFormStatus">Estado</label>
            <select id="staffFormStatus" name="staffFormStatus" class="select" [(ngModel)]="form.status">
              <option value="ACTIVE">Activo</option>
              <option value="INACTIVE">Inactivo</option>
            </select>
          </div>
          <div class="field span-2"><label for="staffNotes">Notas</label><textarea id="staffNotes" name="staffNotes" class="textarea" rows="3" maxlength="500" [(ngModel)]="form.notes"></textarea></div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="modalOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="saving()">{{ saving() ? 'Guardando…' : 'Guardar' }}</button>
        </div>
      </form>
    </app-modal>
  `
})
export class StaffPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly items = signal<StaffMember[]>([]);
  readonly loading = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly modalOpen = signal(false);
  readonly saving = signal(false);
  readonly editing = signal<StaffMember | null>(null);
  readonly error = signal('');
  q = '';
  statusFilter = '';
  form: StaffRequest = { fullName: '', documentType: 'CC', documentNumber: '', position: '', startDate: today(), status: 'ACTIVE' };

  constructor() { void this.load(); }

  async load(page = 0): Promise<void> {
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultStaffMember>('/staff', {
        q: this.q.trim(), status: this.statusFilter, page, size: 20
      }));
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

  clearFilters(): void {
    this.q = '';
    this.statusFilter = '';
    void this.load(0);
  }

  openCreate(): void {
    this.editing.set(null);
    this.error.set('');
    this.form = { fullName: '', documentType: 'CC', documentNumber: '', position: '', startDate: today(), status: 'ACTIVE' };
    this.modalOpen.set(true);
  }

  openEdit(item: StaffMember): void {
    this.editing.set(item);
    this.error.set('');
    this.form = {
      fullName: item.fullName ?? '', documentType: item.documentType ?? 'CC', documentNumber: item.documentNumber ?? '',
      position: item.position ?? '', area: item.area ?? '', phone: item.phone ?? '', email: item.email ?? '',
      shift: item.shift ?? '', startDate: item.startDate ?? today(), endDate: item.endDate ?? undefined,
      status: (item.status ?? 'ACTIVE') as StaffStatus, notes: item.notes ?? ''
    };
    this.modalOpen.set(true);
  }

  async save(): Promise<void> {
    this.error.set('');
    if (!this.form.fullName.trim() || !this.form.documentNumber.trim() || !this.form.position.trim() || !this.form.startDate) {
      this.error.set('Completa nombre, documento, cargo y fecha de ingreso.');
      return;
    }
    this.saving.set(true);
    try {
      const editing = this.editing();
      if (editing?.id) {
        await firstValueFrom(this.api.put<StaffMember>(`/staff/${editing.id}`, this.form));
        this.toast.success('Personal actualizado.');
      } else {
        await firstValueFrom(this.api.post<StaffMember>('/staff', this.form));
        this.toast.success('Personal registrado.');
      }
      this.modalOpen.set(false);
      await this.load(this.page());
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async remove(item: StaffMember): Promise<void> {
    if (!item.id) return;
    const ok = await this.confirm.confirm({
      title: 'Eliminar personal',
      message: `¿Eliminar a ${item.fullName} del directorio?`,
      confirmLabel: 'Eliminar',
      danger: true
    });
    if (!ok) return;
    try {
      await firstValueFrom(this.api.delete<void>(`/staff/${item.id}`));
      this.toast.success('Personal eliminado.');
      await this.load(this.page());
    } catch (error) {
      this.toast.error(describeError(error));
    }
  }
}
