import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ConfirmService } from '../../core/confirm.service';
import { label, statusTone } from '../../core/labels';
import { Appointment, AppointmentModality, AppointmentStatus, AppointmentUpdateRequest, PageResultAppointment } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError, toDateTimeInput, toIsoInstant } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-appointments-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, ModalComponent, EmptyStateComponent, PaginationComponent],
  template: `
    <app-page-header title="Citas con la administración" subtitle="Agenda de solicitudes, confirmaciones y seguimiento de citas de los residentes.">
      <button type="button" class="btn btn-secondary" (click)="load(page())">Actualizar</button>
    </app-page-header>

    <div class="filter-bar">
      <div class="field">
        <label for="appointmentSearch">Buscar</label>
        <input id="appointmentSearch" name="appointmentSearch" class="input" [(ngModel)]="q" (keyup.enter)="load(0)" placeholder="Persona, inmueble o asunto">
      </div>
      <div class="field">
        <label for="appointmentStatus">Estado</label>
        <select id="appointmentStatus" name="appointmentStatus" class="select" [(ngModel)]="statusFilter" (change)="load(0)">
          <option value="">Todos</option>
          @for (option of statuses; track option) { <option [value]="option">{{ label(option) }}</option> }
        </select>
      </div>
      <button type="button" class="btn btn-secondary" (click)="load(0)">Filtrar</button>
      <button type="button" class="btn btn-ghost" (click)="clearFilters()">Limpiar</button>
    </div>

    <div class="card">
      <div class="card-header"><div><h2>Agenda</h2><p>{{ totalElements() }} cita(s).</p></div></div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando citas.</div>
      } @else if (!items().length) {
        <app-empty-state symbol="CT" title="Sin citas" message="No hay citas que coincidan con los filtros." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Fecha y hora</th><th>Persona / inmueble</th><th>Asunto</th><th>Modalidad</th><th>Estado</th><th style="width:280px"></th></tr></thead>
            <tbody>
              @for (item of items(); track item.id) {
                <tr>
                  <td>
                    <div>{{ item.scheduledAt | date:'dd/MM/yyyy' }}</div>
                    <div class="muted">{{ item.scheduledAt | date:'HH:mm' }} · {{ item.durationMinutes }} min</div>
                  </td>
                  <td class="main-cell">
                    <div>{{ item.personName }}</div>
                    <div class="muted">{{ item.unitIdentifier || 'Sin inmueble' }}</div>
                  </td>
                  <td>
                    <div>{{ item.subject }}</div>
                    <div class="muted">{{ item.reason }}</div>
                  </td>
                  <td>{{ label(item.modality) }}</td>
                  <td><span class="badge" [class]="'badge ' + statusTone(item.status)">{{ label(item.status) }}</span></td>
                  <td>
                    <div class="table-actions">
                      @if (item.status === 'REQUESTED') { <button type="button" class="btn btn-primary btn-sm" (click)="setStatus(item, 'CONFIRMED')">Confirmar</button> }
                      @if (item.status === 'CONFIRMED') {
                        <button type="button" class="btn btn-secondary btn-sm" (click)="setStatus(item, 'COMPLETED')">Completar</button>
                        <button type="button" class="btn btn-secondary btn-sm" (click)="setStatus(item, 'NO_SHOW')">No asistió</button>
                      }
                      <button type="button" class="btn btn-secondary btn-sm" (click)="openEdit(item)">Gestionar</button>
                      @if (item.status === 'REQUESTED' || item.status === 'CONFIRMED') {
                        <button type="button" class="btn btn-danger btn-sm" (click)="setStatus(item, 'CANCELLED')">Cancelar</button>
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

    <app-modal [open]="modalOpen()" title="Gestionar cita" subtitle="Reprograma, confirma o registra observaciones de la cita." (closed)="modalOpen.set(false)">
      @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
      @if (editing(); as item) {
        <div class="alert alert-info" style="margin-bottom:14px">
          <strong>{{ item.personName }}</strong> · {{ item.unitIdentifier }} · {{ item.subject }}
        </div>
        <form (ngSubmit)="save()" novalidate>
          <div class="form-grid">
            <div class="field"><label for="appointmentDate">Fecha y hora *</label><input id="appointmentDate" name="appointmentDate" type="datetime-local" class="input" required [(ngModel)]="form.scheduledAt"></div>
            <div class="field"><label for="appointmentDuration">Duración (min)</label><input id="appointmentDuration" name="appointmentDuration" type="number" min="15" max="120" step="15" class="input" [(ngModel)]="form.durationMinutes"></div>
            <div class="field">
              <label for="appointmentModality">Modalidad</label>
              <select id="appointmentModality" name="appointmentModality" class="select" [(ngModel)]="form.modality">
                @for (option of modalities; track option) { <option [value]="option">{{ label(option) }}</option> }
              </select>
            </div>
            <div class="field">
              <label for="appointmentFormStatus">Estado</label>
              <select id="appointmentFormStatus" name="appointmentFormStatus" class="select" [(ngModel)]="form.status">
                @for (option of statuses; track option) { <option [value]="option">{{ label(option) }}</option> }
              </select>
            </div>
            <div class="field"><label for="appointmentLocation">Lugar</label><input id="appointmentLocation" name="appointmentLocation" class="input" maxlength="200" [(ngModel)]="form.location"></div>
            <div class="field"><label for="appointmentLink">Enlace virtual</label><input id="appointmentLink" name="appointmentLink" class="input" maxlength="500" [(ngModel)]="form.meetingLink"></div>
            <div class="field span-2"><label for="appointmentNotes">Observaciones administrativas</label><textarea id="appointmentNotes" name="appointmentNotes" class="textarea" rows="3" maxlength="1000" [(ngModel)]="form.adminNotes"></textarea></div>
          </div>
          <div class="form-actions">
            <button type="button" class="btn btn-secondary" (click)="modalOpen.set(false)">Cancelar</button>
            <button type="submit" class="btn btn-primary" [disabled]="saving()">{{ saving() ? 'Guardando…' : 'Guardar cambios' }}</button>
          </div>
        </form>
      }
    </app-modal>
  `
})
export class AppointmentsPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly statusTone = statusTone;
  readonly statuses: AppointmentStatus[] = ['REQUESTED', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'NO_SHOW'];
  readonly modalities: AppointmentModality[] = ['IN_PERSON', 'VIRTUAL', 'PHONE'];
  readonly items = signal<Appointment[]>([]);
  readonly loading = signal(false);
  readonly modalOpen = signal(false);
  readonly saving = signal(false);
  readonly editing = signal<Appointment | null>(null);
  readonly error = signal('');
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  q = '';
  statusFilter = '';
  form: { scheduledAt: string; durationMinutes: number; modality: AppointmentModality; location: string; meetingLink: string; status: AppointmentStatus; adminNotes: string } = {
    scheduledAt: '', durationMinutes: 30, modality: 'IN_PERSON', location: '', meetingLink: '', status: 'REQUESTED', adminNotes: ''
  };

  constructor() { void this.load(); }

  async load(page = 0): Promise<void> {
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultAppointment>('/appointments', {
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

  openEdit(item: Appointment): void {
    this.editing.set(item);
    this.error.set('');
    this.form = {
      scheduledAt: toDateTimeInput(item.scheduledAt),
      durationMinutes: item.durationMinutes ?? 30,
      modality: item.modality ?? 'IN_PERSON',
      location: item.location ?? '',
      meetingLink: item.meetingLink ?? '',
      status: item.status ?? 'REQUESTED',
      adminNotes: item.adminNotes ?? ''
    };
    this.modalOpen.set(true);
  }

  async save(): Promise<void> {
    const item = this.editing();
    if (!item?.id) return;
    const scheduledAt = toIsoInstant(this.form.scheduledAt);
    if (!scheduledAt) {
      this.error.set('Selecciona la fecha y hora de la cita.');
      return;
    }
    await this.update(item, {
      scheduledAt,
      durationMinutes: this.form.durationMinutes,
      modality: this.form.modality,
      location: this.form.location,
      meetingLink: this.form.meetingLink,
      status: this.form.status,
      adminNotes: this.form.adminNotes
    });
  }

  async setStatus(item: Appointment, status: AppointmentStatus): Promise<void> {
    if (status === 'CANCELLED') {
      const ok = await this.confirm.confirm({
        title: 'Cancelar cita',
        message: `¿Cancelar la cita de ${item.personName} del ${item.scheduledAt ? new Date(item.scheduledAt).toLocaleString('es-CO') : ''}?`,
        confirmLabel: 'Cancelar cita',
        danger: true
      });
      if (!ok) return;
    }
    await this.update(item, {
      scheduledAt: item.scheduledAt,
      durationMinutes: item.durationMinutes,
      modality: item.modality,
      location: item.location,
      meetingLink: item.meetingLink,
      status,
      adminNotes: item.adminNotes
    });
  }

  private async update(item: Appointment, body: AppointmentUpdateRequest): Promise<void> {
    this.saving.set(true);
    try {
      await firstValueFrom(this.api.put<Appointment>(`/appointments/${item.id}`, body));
      this.toast.success('Cita actualizada.');
      this.modalOpen.set(false);
      await this.load(this.page());
    } catch (error) {
      const message = describeError(error);
      this.error.set(message);
      this.toast.error(message);
    } finally {
      this.saving.set(false);
    }
  }
}
