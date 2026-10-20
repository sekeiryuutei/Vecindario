import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { label, statusTone } from '../../core/labels';
import {
  CreateMyPqrRequest,
  MyUnit,
  PageResultPqr,
  Pqr,
  PqrDetail,
  ResidentMessageRequest
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

type PqrCategory = NonNullable<CreateMyPqrRequest['category']>;
type PqrPriority = NonNullable<CreateMyPqrRequest['priority']>;

@Component({
  selector: 'app-my-pqrs-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, ModalComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Mis PQRS" subtitle="Radica peticiones, quejas, reclamos o sugerencias y sigue su gestión con la administración.">
      @if (auth.hasPermission('PQR_CREATE')) {
        <button type="button" class="btn btn-primary" (click)="openCreate()">Radicar PQRS</button>
      }
    </app-page-header>

    <div class="grid grid-4">
      <div class="stat accent"><div class="stat-label">Radicadas</div><div class="stat-value">{{ totalElements() }}</div><div class="stat-hint">En total</div></div>
      <div class="stat warn"><div class="stat-label">Abiertas</div><div class="stat-value">{{ openCount() }}</div><div class="stat-hint">En esta página</div></div>
      <div class="stat"><div class="stat-label">Resueltas</div><div class="stat-value">{{ resolvedCount() }}</div><div class="stat-hint">En esta página</div></div>
      <div class="stat"><div class="stat-label">Última radicación</div><div class="stat-value" style="font-size:20px">{{ lastCreated() ? (lastCreated() | date:'dd/MM/yyyy') : 'Sin registros' }}</div><div class="stat-hint">Fecha de la más reciente</div></div>
    </div>

    <div class="card">
      <div class="card-header">
        <div><h2>Radicados</h2><p>Consulta el estado y conversa con la administración sobre cada caso.</p></div>
        <button type="button" class="btn btn-secondary btn-sm" (click)="load(page())">Actualizar</button>
      </div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando tus PQRS…</div>
      } @else if (!items().length) {
        <app-empty-state symbol="✉" title="Sin PQRS radicadas" message="Cuando radiques una petición, queja, reclamo o sugerencia aparecerá en este listado." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Radicado</th><th>Asunto</th><th>Categoría</th><th>Prioridad</th><th>Estado</th><th>SLA</th><th style="width:90px"></th></tr></thead>
            <tbody>
              @for (item of items(); track item.id) {
                <tr>
                  <td><div class="main-cell">{{ item.ticketNumber || '—' }}</div><div class="muted small">{{ item.createdAt | date:'dd/MM/yyyy' }}</div></td>
                  <td><div class="main-cell">{{ item.title }}</div><div class="muted small">{{ item.isAnonymous ? 'Radicada de forma anónima' : 'Radicada a tu nombre' }}</div></td>
                  <td>{{ label(item.category) }}</td>
                  <td><span class="badge" [class]="'badge ' + tone(item.priority)">{{ label(item.priority) }}</span></td>
                  <td><span class="badge" [class]="'badge ' + tone(item.status)">{{ label(item.status) }}</span></td>
                  <td>
                    @if (item.overdue) { <span class="badge danger">Vencido</span> }
                    @else { <span class="muted small">{{ item.slaDueAt | date:'dd/MM HH:mm' }}</span> }
                  </td>
                  <td><button type="button" class="btn btn-secondary btn-sm" (click)="openDetail(item)">Ver</button></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-pagination [page]="page()" [totalPages]="totalPages()" [totalElements]="totalElements()" (pageChange)="load($event)" />
      }
    </div>

    <app-modal [open]="createOpen()" title="Radicar PQRS" subtitle="Describe tu solicitud con el mayor detalle posible para facilitar la gestión." [wide]="true" (closed)="createOpen.set(false)">
      @if (formError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ formError() }}</div> }
      <form (ngSubmit)="create()" novalidate>
        <div class="form-grid">
          <div class="field">
            <label for="pqrUnit">Inmueble relacionado</label>
            <select id="pqrUnit" name="pqrUnit" class="select" [(ngModel)]="form.unitId">
              <option value="">Sin inmueble específico</option>
              @for (unit of units(); track unit.unitId) { <option [value]="unit.unitId">{{ unit.identifier }} · {{ unit.tower || 'Sin torre' }}</option> }
            </select>
          </div>
          <div class="field">
            <label for="pqrCategory">Categoría *</label>
            <select id="pqrCategory" name="pqrCategory" class="select" required [(ngModel)]="form.category">
              @for (option of categories; track option) { <option [value]="option">{{ label(option) }}</option> }
            </select>
          </div>
          <div class="field">
            <label for="pqrPriority">Prioridad</label>
            <select id="pqrPriority" name="pqrPriority" class="select" [(ngModel)]="form.priority">
              @for (option of priorities; track option) { <option [value]="option">{{ label(option) }}</option> }
            </select>
          </div>
          <div class="field">
            <label class="check" style="margin-top:26px"><input type="checkbox" [(ngModel)]="form.isAnonymous"> Radicar de forma anónima</label>
          </div>
          <div class="field span-2">
            <label for="pqrTitle">Asunto *</label>
            <input id="pqrTitle" name="pqrTitle" class="input" required maxlength="200" [(ngModel)]="form.title" placeholder="Resumen breve del caso">
          </div>
          <div class="field span-2">
            <label for="pqrDescription">Descripción *</label>
            <textarea id="pqrDescription" name="pqrDescription" class="textarea" required maxlength="4000" style="min-height:160px" [(ngModel)]="form.description" placeholder="Cuéntanos qué ocurrió, cuándo y dónde."></textarea>
            <span class="hint">{{ form.description.length }} de 4000 caracteres.</span>
          </div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="createOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="saving()">{{ saving() ? 'Radicando…' : 'Radicar PQRS' }}</button>
        </div>
      </form>
    </app-modal>

    <app-modal [open]="detailOpen()" [title]="detail()?.pqr?.ticketNumber || 'Detalle de PQRS'" [subtitle]="detail()?.pqr?.title || ''" [wide]="true" (closed)="detailOpen.set(false)">
      @if (loadingDetail()) {
        <div class="loading"><span class="spinner"></span> Cargando el radicado…</div>
      } @else if (detail()?.pqr; as pqr) {
        <div class="grid grid-2">
          <div>
            <dl class="detail-list">
              <dt>Estado</dt><dd><span class="badge" [class]="'badge ' + tone(pqr.status)">{{ label(pqr.status) }}</span></dd>
              <dt>Categoría</dt><dd>{{ label(pqr.category) }}</dd>
              <dt>Prioridad</dt><dd>{{ label(pqr.priority) }}</dd>
              <dt>Radicada</dt><dd>{{ pqr.createdAt | date:'dd/MM/yyyy HH:mm' }}</dd>
              <dt>SLA</dt><dd>{{ pqr.slaDueAt | date:'dd/MM/yyyy HH:mm' }}{{ pqr.overdue ? ' · vencido' : '' }}</dd>
              <dt>Inmueble</dt><dd>{{ unitName(pqr.unitId) }}</dd>
            </dl>
            <div class="divider"></div>
            <h3 class="section-title">Descripción</h3>
            <p style="white-space:pre-wrap;line-height:1.65;font-size:13.5px">{{ pqr.description }}</p>
            @if (pqr.resolutionNotes) {
              <div class="notice" style="margin-top:14px"><strong>Resolución de la administración:</strong> {{ pqr.resolutionNotes }}</div>
            }
          </div>
          <div>
            <h3 class="section-title">Conversación</h3>
            <div class="timeline" style="max-height:380px;overflow:auto">
              @for (message of detail()?.messages; track message.id) {
                <div class="timeline-item">
                  <span class="timeline-dot"></span>
                  <div class="timeline-content">
                    <div class="row between">
                      <strong style="font-size:13px">{{ message.senderName || 'Administración' }}</strong>
                      <span class="muted small">{{ message.createdAt | date:'dd/MM/yyyy HH:mm' }}</span>
                    </div>
                    <p style="white-space:pre-wrap;margin-top:5px;font-size:13px">{{ message.message }}</p>
                  </div>
                </div>
              } @empty { <p class="muted small">Aún no hay respuestas en este radicado.</p> }
            </div>
            @if (pqr.status !== 'CLOSED' && pqr.status !== 'REJECTED') {
              <div class="divider"></div>
              <div class="field">
                <label for="reply">Aportar información</label>
                <textarea id="reply" name="reply" class="textarea" maxlength="2000" [(ngModel)]="reply" placeholder="Agrega detalles o responde a la administración."></textarea>
              </div>
              <div class="form-actions">
                <button type="button" class="btn btn-primary" [disabled]="saving() || !reply.trim()" (click)="sendMessage()">{{ saving() ? 'Enviando…' : 'Enviar mensaje' }}</button>
              </div>
            }
          </div>
        </div>
      }
    </app-modal>
  `
})
export class MyPqrsPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly categories: PqrCategory[] = ['PETICION', 'QUEJA', 'RECLAMO', 'SUGERENCIA', 'CONVIVENCIA_RUIDO', 'CONVIVENCIA_MASCOTAS', 'CONVIVENCIA_GENERAL', 'OTRO'];
  readonly priorities: PqrPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];
  readonly units = signal<MyUnit[]>([]);
  readonly items = signal<Pqr[]>([]);
  readonly detail = signal<PqrDetail | null>(null);
  readonly loading = signal(false);
  readonly loadingDetail = signal(false);
  readonly saving = signal(false);
  readonly createOpen = signal(false);
  readonly detailOpen = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly formError = signal('');
  readonly openCount = computed(() => this.items().filter((item) => item.status === 'OPEN' || item.status === 'IN_PROGRESS').length);
  readonly resolvedCount = computed(() => this.items().filter((item) => item.status === 'RESOLVED' || item.status === 'CLOSED').length);
  readonly lastCreated = computed(() => {
    const dates = this.items().map((item) => item.createdAt).filter((value): value is string => Boolean(value)).sort();
    return dates.length ? dates[dates.length - 1] : '';
  });
  reply = '';
  form: CreateMyPqrRequest = this.emptyForm();

  constructor() {
    void this.loadUnits();
    void this.load(0);
  }

  unitName(id: string | undefined): string {
    if (!id) return 'Sin inmueble específico';
    return this.units().find((unit) => unit.unitId === id)?.identifier ?? '—';
  }

  async loadUnits(): Promise<void> {
    try {
      this.units.set(await firstValueFrom(this.api.get<MyUnit[]>('/my/units')));
    } catch {
      this.units.set([]);
    }
  }

  async load(page = 0): Promise<void> {
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultPqr>('/my/pqrs', { page, size: 20 }));
      this.items.set(result.content ?? []);
      this.page.set(result.page ?? 0);
      this.totalPages.set(result.totalPages ?? 0);
      this.totalElements.set(result.totalElements ?? 0);
    } catch (error) {
      this.items.set([]);
      this.toast.error(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  openCreate(): void {
    this.form = this.emptyForm();
    if (this.units().length === 1) this.form.unitId = this.units()[0].unitId ?? '';
    this.formError.set('');
    this.createOpen.set(true);
  }

  async create(): Promise<void> {
    this.formError.set('');
    if (!this.form.title.trim() || !this.form.description.trim()) {
      this.formError.set('Indica el asunto y la descripción del caso.');
      return;
    }
    if (this.form.description.trim().length < 20) {
      this.formError.set('La descripción debe tener al menos 20 caracteres.');
      return;
    }
    this.saving.set(true);
    try {
      const body: CreateMyPqrRequest = {
        unitId: this.form.unitId || undefined,
        category: this.form.category,
        priority: this.form.priority,
        title: this.form.title.trim(),
        description: this.form.description.trim(),
        isAnonymous: this.form.isAnonymous ?? false
      };
      const created = await firstValueFrom(this.api.post<Pqr>('/my/pqrs', body));
      this.toast.success(`PQRS radicada${created.ticketNumber ? ` con radicado ${created.ticketNumber}` : ''}.`);
      this.createOpen.set(false);
      await this.load(0);
    } catch (error) {
      this.formError.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async openDetail(item: Pqr): Promise<void> {
    if (!item.id) return;
    this.detail.set(null);
    this.reply = '';
    this.detailOpen.set(true);
    this.loadingDetail.set(true);
    try {
      this.detail.set(await firstValueFrom(this.api.get<PqrDetail>(`/my/pqrs/${item.id}`)));
    } catch (error) {
      this.toast.error(describeError(error));
      this.detailOpen.set(false);
    } finally {
      this.loadingDetail.set(false);
    }
  }

  async sendMessage(): Promise<void> {
    const pqr = this.detail()?.pqr;
    if (!pqr?.id || !this.reply.trim()) return;
    this.saving.set(true);
    try {
      const body: ResidentMessageRequest = { message: this.reply.trim() };
      await firstValueFrom(this.api.post(`/my/pqrs/${pqr.id}/messages`, body));
      this.reply = '';
      this.toast.success('Mensaje enviado.');
      this.detail.set(await firstValueFrom(this.api.get<PqrDetail>(`/my/pqrs/${pqr.id}`)));
      await this.load(this.page());
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  private emptyForm(): CreateMyPqrRequest {
    return { unitId: '', category: 'PETICION', priority: 'MEDIUM', title: '', description: '', isAnonymous: false };
  }
}
