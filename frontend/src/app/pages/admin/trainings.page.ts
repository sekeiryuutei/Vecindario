import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ConfirmService } from '../../core/confirm.service';
import { label, statusTone } from '../../core/labels';
import { EnrollmentStatus, PageResultTrainingSession, TrainingEnrollment, TrainingModality, TrainingRequest, TrainingSession } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError, toDateTimeInput, toIsoInstant } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-trainings-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, ModalComponent, EmptyStateComponent, PaginationComponent],
  template: `
    <app-page-header title="Capacitaciones" subtitle="Programa talleres comunitarios, controla cupos y registra la asistencia.">
      @if (auth.hasPermission('TRAININGS_MANAGE')) {
        <button type="button" class="btn btn-primary" (click)="openCreate()">Nueva capacitación</button>
      }
    </app-page-header>

    <div class="filter-bar">
      <div class="field">
        <label for="trainingStatus">Estado</label>
        <select id="trainingStatus" name="trainingStatus" class="select" [(ngModel)]="statusFilter" (change)="load(0)">
          <option value="">Todas</option>
          <option value="SCHEDULED">Programadas</option>
          <option value="COMPLETED">Completadas</option>
          <option value="CANCELLED">Canceladas</option>
        </select>
      </div>
      <button type="button" class="btn btn-secondary" (click)="load(0)">Filtrar</button>
      <button type="button" class="btn btn-ghost" (click)="clearFilters()">Limpiar</button>
    </div>

    <div class="card">
      <div class="card-header"><div><h2>Capacitaciones</h2><p>{{ totalElements() }} capacitación(es).</p></div><button type="button" class="btn btn-secondary btn-sm" (click)="load(page())">Actualizar</button></div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando capacitaciones.</div>
      } @else if (!items().length) {
        <app-empty-state symbol="CA" title="Sin capacitaciones" message="No hay capacitaciones que coincidan con el filtro." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Capacitación</th><th>Fecha</th><th>Lugar</th><th>Cupos</th><th>Estado</th><th style="width:300px"></th></tr></thead>
            <tbody>
              @for (item of items(); track item.id) {
                <tr>
                  <td class="main-cell">
                    <div>{{ item.title }}</div>
                    <div class="muted">{{ item.trainerName || 'Sin facilitador' }} · {{ label(item.modality) }}</div>
                  </td>
                  <td>
                    <div>{{ item.startsAt | date:'dd/MM/yyyy' }}</div>
                    <div class="muted">{{ item.startsAt | date:'HH:mm' }} – {{ item.endsAt | date:'HH:mm' }}</div>
                  </td>
                  <td>{{ item.location || item.meetingLink || '—' }}</td>
                  <td>{{ item.enrolledCount ?? 0 }} / {{ item.capacity }}</td>
                  <td><span class="badge" [class]="'badge ' + statusTone(item.status)">{{ label(item.status) }}</span></td>
                  <td>
                    <div class="table-actions">
                      <button type="button" class="btn btn-secondary btn-sm" (click)="openEnrollments(item)">Inscripciones</button>
                      @if (item.status === 'SCHEDULED') {
                        <button type="button" class="btn btn-secondary btn-sm" (click)="openEdit(item)">Editar</button>
                        <button type="button" class="btn btn-danger btn-sm" (click)="cancel(item)">Cancelar</button>
                      }
                      @if (item.status === 'CANCELLED') {
                        <button type="button" class="btn btn-danger btn-sm" (click)="remove(item)">Eliminar</button>
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

    <app-modal [open]="modalOpen()" [title]="editing() ? 'Editar capacitación' : 'Nueva capacitación'" subtitle="Define fechas, cupo y facilitador." (closed)="modalOpen.set(false)">
      @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
      <form (ngSubmit)="save()" novalidate>
        <div class="form-grid">
          <div class="field span-2"><label for="trainingTitle">Título *</label><input id="trainingTitle" name="trainingTitle" class="input" required maxlength="160" [(ngModel)]="form.title"></div>
          <div class="field span-2"><label for="trainingDescription">Descripción</label><textarea id="trainingDescription" name="trainingDescription" class="textarea" rows="3" maxlength="1000" [(ngModel)]="form.description"></textarea></div>
          <div class="field"><label for="trainingTrainer">Facilitador</label><input id="trainingTrainer" name="trainingTrainer" class="input" maxlength="160" [(ngModel)]="form.trainerName"></div>
          <div class="field">
            <label for="trainingModality">Modalidad</label>
            <select id="trainingModality" name="trainingModality" class="select" [(ngModel)]="form.modality">
              @for (option of modalities; track option) { <option [value]="option">{{ label(option) }}</option> }
            </select>
          </div>
          <div class="field"><label for="trainingStarts">Inicio *</label><input id="trainingStarts" name="trainingStarts" type="datetime-local" class="input" required [(ngModel)]="form.startsAt"></div>
          <div class="field"><label for="trainingEnds">Fin *</label><input id="trainingEnds" name="trainingEnds" type="datetime-local" class="input" required [(ngModel)]="form.endsAt"></div>
          <div class="field"><label for="trainingLocation">Lugar</label><input id="trainingLocation" name="trainingLocation" class="input" maxlength="200" [(ngModel)]="form.location"></div>
          <div class="field"><label for="trainingLink">Enlace virtual</label><input id="trainingLink" name="trainingLink" class="input" maxlength="500" [(ngModel)]="form.meetingLink"></div>
          <div class="field"><label for="trainingCapacity">Cupo máximo *</label><input id="trainingCapacity" name="trainingCapacity" type="number" min="1" max="500" class="input" required [(ngModel)]="form.capacity"></div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="modalOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="saving()">{{ saving() ? 'Guardando…' : 'Guardar' }}</button>
        </div>
      </form>
    </app-modal>

    <app-modal [open]="enrollmentsOpen()" title="Inscripciones" [subtitle]="enrollmentSession()?.title || ''" (closed)="enrollmentsOpen.set(false)">
      @if (enrollmentsLoading()) {
        <div class="loading"><span class="spinner"></span> Cargando inscripciones.</div>
      } @else if (!enrollments().length) {
        <app-empty-state symbol="IN" title="Sin inscripciones" message="Aún no hay personas inscritas en esta capacitación." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Persona</th><th>Inmueble</th><th>Estado</th><th style="width:180px"></th></tr></thead>
            <tbody>
              @for (enrollment of enrollments(); track enrollment.id) {
                <tr>
                  <td>{{ enrollment.personName }}</td>
                  <td>{{ enrollment.unitIdentifier || '—' }}</td>
                  <td><span class="badge" [class]="'badge ' + statusTone(enrollment.status)">{{ label(enrollment.status) }}</span></td>
                  <td>
                    @if (enrollment.status !== 'CANCELLED') {
                      <div class="table-actions">
                        <button type="button" class="btn btn-secondary btn-sm" (click)="mark(enrollment, 'ATTENDED')">Asistió</button>
                        <button type="button" class="btn btn-secondary btn-sm" (click)="mark(enrollment, 'ABSENT')">No asistió</button>
                      </div>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </app-modal>
  `
})
export class TrainingsPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly statusTone = statusTone;
  readonly modalities: TrainingModality[] = ['IN_PERSON', 'VIRTUAL', 'HYBRID'];
  readonly items = signal<TrainingSession[]>([]);
  readonly loading = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly modalOpen = signal(false);
  readonly saving = signal(false);
  readonly editing = signal<TrainingSession | null>(null);
  readonly error = signal('');
  readonly enrollmentsOpen = signal(false);
  readonly enrollmentsLoading = signal(false);
  readonly enrollmentSession = signal<TrainingSession | null>(null);
  readonly enrollments = signal<TrainingEnrollment[]>([]);
  statusFilter = '';
  form: TrainingRequest & { startsAt: string; endsAt: string } = {
    title: '', capacity: 30, modality: 'IN_PERSON', startsAt: '', endsAt: ''
  };

  constructor() { void this.load(); }

  async load(page = 0): Promise<void> {
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultTrainingSession>('/trainings', {
        status: this.statusFilter, page, size: 20
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
    this.statusFilter = '';
    void this.load(0);
  }

  openCreate(): void {
    this.editing.set(null);
    this.error.set('');
    const start = new Date();
    start.setDate(start.getDate() + 7);
    start.setHours(9, 0, 0, 0);
    const end = new Date(start);
    end.setHours(11, 0, 0, 0);
    this.form = {
      title: '', description: '', trainerName: '', modality: 'IN_PERSON', location: '', meetingLink: '',
      startsAt: toDateTimeInput(start.toISOString()), endsAt: toDateTimeInput(end.toISOString()), capacity: 30
    };
    this.modalOpen.set(true);
  }

  openEdit(item: TrainingSession): void {
    this.editing.set(item);
    this.error.set('');
    this.form = {
      title: item.title ?? '', description: item.description ?? '', trainerName: item.trainerName ?? '',
      modality: item.modality ?? 'IN_PERSON', location: item.location ?? '', meetingLink: item.meetingLink ?? '',
      startsAt: toDateTimeInput(item.startsAt), endsAt: toDateTimeInput(item.endsAt), capacity: item.capacity ?? 30
    };
    this.modalOpen.set(true);
  }

  async save(): Promise<void> {
    this.error.set('');
    const startsAt = toIsoInstant(this.form.startsAt);
    const endsAt = toIsoInstant(this.form.endsAt);
    if (!this.form.title.trim() || !startsAt || !endsAt) {
      this.error.set('Indica el título, el inicio y el fin de la capacitación.');
      return;
    }
    this.saving.set(true);
    try {
      const body: TrainingRequest = {
        title: this.form.title.trim(), description: this.form.description?.trim(), trainerName: this.form.trainerName?.trim(),
        modality: this.form.modality, location: this.form.location?.trim(), meetingLink: this.form.meetingLink?.trim(),
        startsAt, endsAt, capacity: this.form.capacity
      };
      const editing = this.editing();
      if (editing?.id) {
        await firstValueFrom(this.api.put<TrainingSession>(`/trainings/${editing.id}`, body));
        this.toast.success('Capacitación actualizada.');
      } else {
        await firstValueFrom(this.api.post<TrainingSession>('/trainings', body));
        this.toast.success('Capacitación creada.');
      }
      this.modalOpen.set(false);
      await this.load(this.page());
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async cancel(item: TrainingSession): Promise<void> {
    if (!item.id) return;
    const ok = await this.confirm.confirm({
      title: 'Cancelar capacitación',
      message: `¿Cancelar “${item.title}”? Las inscripciones quedarán sin efecto.`,
      confirmLabel: 'Cancelar capacitación',
      danger: true
    });
    if (!ok) return;
    try {
      await firstValueFrom(this.api.post<void>(`/trainings/${item.id}/cancel`));
      this.toast.success('Capacitación cancelada.');
      await this.load(this.page());
    } catch (error) {
      this.toast.error(describeError(error));
    }
  }

  async remove(item: TrainingSession): Promise<void> {
    if (!item.id) return;
    const ok = await this.confirm.confirm({
      title: 'Eliminar capacitación',
      message: `¿Eliminar definitivamente “${item.title}”?`,
      confirmLabel: 'Eliminar',
      danger: true
    });
    if (!ok) return;
    try {
      await firstValueFrom(this.api.delete<void>(`/trainings/${item.id}`));
      this.toast.success('Capacitación eliminada.');
      await this.load(this.page());
    } catch (error) {
      this.toast.error(describeError(error));
    }
  }

  async openEnrollments(item: TrainingSession): Promise<void> {
    this.enrollmentSession.set(item);
    this.enrollmentsOpen.set(true);
    if (!item.id) return;
    this.enrollmentsLoading.set(true);
    try {
      this.enrollments.set(await firstValueFrom(this.api.get<TrainingEnrollment[]>(`/trainings/${item.id}/enrollments`)));
    } catch (error) {
      this.toast.error(describeError(error));
      this.enrollments.set([]);
    } finally {
      this.enrollmentsLoading.set(false);
    }
  }

  async mark(enrollment: TrainingEnrollment, status: EnrollmentStatus): Promise<void> {
    if (!enrollment.id) return;
    try {
      await firstValueFrom(this.api.put<TrainingEnrollment>(`/trainings/enrollments/${enrollment.id}`, { status }));
      this.toast.success('Asistencia registrada.');
      const session = this.enrollmentSession();
      if (session) await this.openEnrollments(session);
      await this.load(this.page());
    } catch (error) {
      this.toast.error(describeError(error));
    }
  }
}
