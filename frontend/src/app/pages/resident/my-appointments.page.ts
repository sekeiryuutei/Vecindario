import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ConfirmService } from '../../core/confirm.service';
import { label, statusTone } from '../../core/labels';
import { Appointment, AppointmentModality, AppointmentRequest, MyUnit } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError, toIsoInstant } from '../../core/utils';
import { EmptyStateComponent, PageHeaderComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-my-appointments-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Citas con la administración" subtitle="Solicita una reunión presencial, virtual o telefónica y consulta su estado." />

    <div class="grid" style="grid-template-columns:minmax(0,380px) minmax(0,1fr);align-items:start">
      <div class="card">
        <div class="card-header"><div><h2>Nueva solicitud</h2><p>Las citas se atienden de lunes a viernes, de 8:00 a 17:00.</p></div></div>
        <div class="card-body">
          @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
          <form (ngSubmit)="request()" novalidate>
            <div class="form-grid" style="grid-template-columns:1fr">
              <div class="field">
                <label for="myAppointmentUnit">Inmueble *</label>
                <select id="myAppointmentUnit" name="myAppointmentUnit" class="select" required [(ngModel)]="form.unitId">
                  <option value="">Selecciona un inmueble</option>
                  @for (unit of units(); track unit.unitId) { <option [value]="unit.unitId">{{ unit.identifier }} — {{ unit.tower || 'Sin torre' }}</option> }
                </select>
              </div>
              <div class="field"><label for="myAppointmentSubject">Asunto *</label><input id="myAppointmentSubject" name="myAppointmentSubject" class="input" required maxlength="160" [(ngModel)]="form.subject"></div>
              <div class="field"><label for="myAppointmentReason">Motivo *</label><textarea id="myAppointmentReason" name="myAppointmentReason" class="textarea" rows="4" required maxlength="1000" [(ngModel)]="form.reason"></textarea></div>
              <div class="field"><label for="myAppointmentDate">Fecha y hora *</label><input id="myAppointmentDate" name="myAppointmentDate" type="datetime-local" class="input" required [min]="minDateTime" [(ngModel)]="form.scheduledAt"></div>
              <div class="field">
                <label for="myAppointmentModality">Modalidad</label>
                <select id="myAppointmentModality" name="myAppointmentModality" class="select" [(ngModel)]="form.modality">
                  @for (option of modalities; track option) { <option [value]="option">{{ label(option) }}</option> }
                </select>
              </div>
              <div class="field"><label for="myAppointmentDuration">Duración (min)</label><input id="myAppointmentDuration" name="myAppointmentDuration" type="number" min="15" max="120" step="15" class="input" [(ngModel)]="form.durationMinutes"></div>
              <div class="field"><label for="myAppointmentLocation">Lugar (si es presencial)</label><input id="myAppointmentLocation" name="myAppointmentLocation" class="input" maxlength="200" [(ngModel)]="form.location"></div>
              <div class="field"><label for="myAppointmentLink">Enlace (si es virtual)</label><input id="myAppointmentLink" name="myAppointmentLink" class="input" maxlength="500" [(ngModel)]="form.meetingLink"></div>
            </div>
            <div class="form-actions"><button type="submit" class="btn btn-primary" [disabled]="saving()">{{ saving() ? 'Enviando…' : 'Solicitar cita' }}</button></div>
          </form>
        </div>
      </div>

      <div class="card">
        <div class="card-header"><div><h2>Mis citas</h2><p>{{ items().length }} solicitud(es).</p></div><button type="button" class="btn btn-secondary btn-sm" (click)="load()">Actualizar</button></div>
        @if (loading()) {
          <div class="loading"><span class="spinner"></span> Cargando citas.</div>
        } @else if (!items().length) {
          <app-empty-state symbol="CT" title="Sin citas" message="Aún no has solicitado una cita con la administración." />
        } @else {
          <div class="card-body" style="display:grid;gap:12px">
            @for (item of items(); track item.id) {
              <div style="border:1px solid var(--line);border-radius:10px;padding:14px">
                <div style="display:flex;justify-content:space-between;gap:12px;align-items:flex-start">
                  <div>
                    <strong>{{ item.subject }}</strong>
                    <div class="muted">{{ item.scheduledAt | date:'dd/MM/yyyy HH:mm' }} · {{ item.durationMinutes }} min · {{ label(item.modality) }}</div>
                  </div>
                  <span class="badge" [class]="'badge ' + statusTone(item.status)">{{ label(item.status) }}</span>
                </div>
                <p style="margin:10px 0 0;white-space:pre-wrap">{{ item.reason }}</p>
                <div class="muted" style="margin-top:8px">Inmueble: {{ item.unitIdentifier || '—' }}{{ item.location ? ' · Lugar: ' + item.location : '' }}{{ item.meetingLink ? ' · Enlace: ' + item.meetingLink : '' }}</div>
                @if (item.adminNotes) { <div class="alert alert-info" style="margin-top:10px">Administración: {{ item.adminNotes }}</div> }
                @if (item.cancellationReason) { <div class="muted" style="margin-top:8px">Motivo de cancelación: {{ item.cancellationReason }}</div> }
                @if ((item.status === 'REQUESTED' || item.status === 'CONFIRMED') && isFuture(item.scheduledAt)) {
                  <div class="form-actions" style="margin-top:10px"><button type="button" class="btn btn-danger btn-sm" (click)="cancel(item)">Cancelar cita</button></div>
                }
              </div>
            }
          </div>
        }
      </div>
    </div>
  `,
  styles: [`@media (max-width: 950px) { .grid { grid-template-columns: 1fr !important; } }`]
})
export class MyAppointmentsPage {
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly statusTone = statusTone;
  readonly modalities: AppointmentModality[] = ['IN_PERSON', 'VIRTUAL', 'PHONE'];
  readonly items = signal<Appointment[]>([]);
  readonly units = signal<MyUnit[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly minDateTime: string;
  form: AppointmentRequest = {
    unitId: '', subject: '', reason: '', scheduledAt: '', durationMinutes: 30, modality: 'IN_PERSON', location: 'Oficina de administración'
  };

  constructor() {
    const min = new Date(Date.now() + 2 * 60 * 60 * 1000);
    const pad = (value: number) => String(value).padStart(2, '0');
    this.minDateTime = `${min.getFullYear()}-${pad(min.getMonth() + 1)}-${pad(min.getDate())}T${pad(min.getHours())}:${pad(min.getMinutes())}`;
    this.form.scheduledAt = '';
    void this.loadUnits();
    void this.load();
  }

  async loadUnits(): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.get<MyUnit[]>('/my/units'));
      this.units.set(result);
      this.form.unitId = result[0]?.unitId ?? '';
    } catch (error) {
      this.toast.error(describeError(error));
    }
  }

  async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.items.set(await firstValueFrom(this.api.get<Appointment[]>('/my/appointments')));
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  async request(): Promise<void> {
    this.error.set('');
    const scheduledAt = toIsoInstant(this.form.scheduledAt);
    if (!this.form.unitId || !this.form.subject.trim() || !this.form.reason.trim() || !scheduledAt) {
      this.error.set('Selecciona el inmueble, escribe asunto y motivo, e indica fecha y hora.');
      return;
    }
    this.saving.set(true);
    try {
      await firstValueFrom(this.api.post<Appointment>('/my/appointments', { ...this.form, scheduledAt }));
      this.toast.success('Solicitud enviada. La administración confirmará la cita.');
      this.form = { ...this.form, subject: '', reason: '', scheduledAt: '' };
      await this.load();
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async cancel(item: Appointment): Promise<void> {
    if (!item.id) return;
    const ok = await this.confirm.confirm({
      title: 'Cancelar cita',
      message: '¿Deseas cancelar esta cita? La administración verá el cambio.',
      confirmLabel: 'Cancelar cita',
      danger: true
    });
    if (!ok) return;
    try {
      await firstValueFrom(this.api.post<void>(`/my/appointments/${item.id}/cancel`, { reason: 'Cancelada por el residente.' }));
      this.toast.success('Cita cancelada.');
      await this.load();
    } catch (error) {
      this.toast.error(describeError(error));
    }
  }

  isFuture(value?: string): boolean {
    return Boolean(value && new Date(value).getTime() > Date.now());
  }
}
