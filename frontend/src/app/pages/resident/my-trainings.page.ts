import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ConfirmService } from '../../core/confirm.service';
import { label, statusTone } from '../../core/labels';
import { MyTraining } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, PageHeaderComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-my-trainings-page',
  standalone: true,
  imports: [DatePipe, PageHeaderComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Capacitaciones" subtitle="Talleres y formaciones organizadas por la copropiedad. Inscríbete y consulta tu asistencia." />

    @if (loading()) {
      <div class="card"><div class="loading"><span class="spinner"></span> Cargando capacitaciones.</div></div>
    } @else if (!items().length) {
      <div class="card"><app-empty-state symbol="CA" title="Sin capacitaciones" message="No hay capacitaciones publicadas por el momento." /></div>
    } @else {
      <div class="grid grid-2">
        @for (item of items(); track item.session?.id) {
          @if (item.session; as session) {
            <div class="card">
              <div class="card-body">
                <div style="display:flex;justify-content:space-between;gap:12px;align-items:flex-start">
                  <div>
                    <h2 style="margin:0 0 4px">{{ session.title }}</h2>
                    <div class="muted">{{ session.trainerName || 'Facilitador por confirmar' }} · {{ label(session.modality) }}</div>
                  </div>
                  <span class="badge" [class]="'badge ' + statusTone(session.status)">{{ label(session.status) }}</span>
                </div>
                @if (session.description) { <p style="margin-top:12px;white-space:pre-wrap">{{ session.description }}</p> }
                <div style="margin-top:14px;display:grid;gap:5px">
                  <div><strong>Fecha:</strong> {{ session.startsAt | date:'dd/MM/yyyy HH:mm' }} – {{ session.endsAt | date:'HH:mm' }}</div>
                  <div><strong>Lugar:</strong> {{ session.location || session.meetingLink || 'Por confirmar' }}</div>
                  <div><strong>Cupos:</strong> {{ session.enrolledCount ?? 0 }} de {{ session.capacity }}</div>
                </div>
                <div style="margin-top:14px;display:flex;align-items:center;gap:10px;flex-wrap:wrap">
                  @if (item.enrollmentStatus && item.enrollmentStatus !== 'CANCELLED') {
                    <span class="badge" [class]="'badge ' + statusTone(item.enrollmentStatus)">{{ label(item.enrollmentStatus) }}</span>
                  }
                  @if (canEnroll(item)) {
                    <button type="button" class="btn btn-primary btn-sm" [disabled]="busy() === session.id" (click)="enroll(session.id)">Inscribirme</button>
                  }
                  @if (item.enrollmentStatus === 'ENROLLED' && isFuture(session.startsAt)) {
                    <button type="button" class="btn btn-danger btn-sm" [disabled]="busy() === session.id" (click)="cancel(session.id)">Cancelar inscripción</button>
                  }
                </div>
              </div>
            </div>
          }
        }
      </div>
    }
  `
})
export class MyTrainingsPage {
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly statusTone = statusTone;
  readonly items = signal<MyTraining[]>([]);
  readonly loading = signal(false);
  readonly busy = signal<string | null>(null);

  constructor() { void this.load(); }

  async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.items.set(await firstValueFrom(this.api.get<MyTraining[]>('/my/trainings')));
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  canEnroll(item: MyTraining): boolean {
    const session = item.session;
    if (!session?.id || session.status !== 'SCHEDULED' || !this.isFuture(session.startsAt)) return false;
    if (item.enrollmentStatus === 'ENROLLED' || item.enrollmentStatus === 'ATTENDED') return false;
    return (session.enrolledCount ?? 0) < (session.capacity ?? 0);
  }

  async enroll(id?: string): Promise<void> {
    if (!id) return;
    this.busy.set(id);
    try {
      await firstValueFrom(this.api.post(`/my/trainings/${id}/enroll`));
      this.toast.success('Inscripción confirmada.');
      await this.load();
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.busy.set(null);
    }
  }

  async cancel(id?: string): Promise<void> {
    if (!id) return;
    const ok = await this.confirm.confirm({
      title: 'Cancelar inscripción',
      message: '¿Deseas cancelar tu inscripción a esta capacitación?',
      confirmLabel: 'Cancelar inscripción',
      danger: true
    });
    if (!ok) return;
    this.busy.set(id);
    try {
      await firstValueFrom(this.api.delete<void>(`/my/trainings/${id}/enroll`));
      this.toast.success('Inscripción cancelada.');
      await this.load();
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.busy.set(null);
    }
  }

  isFuture(value?: string): boolean {
    return Boolean(value && new Date(value).getTime() > Date.now());
  }
}
