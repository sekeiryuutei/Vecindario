import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { label, statusTone } from '../../core/labels';
import {
  MemberView,
  PageResultMemberView,
  PageResultPqr,
  Pqr,
  PqrDetail,
  PqrSlaMetrics
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-pqrs-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, ModalComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="PQRS" subtitle="Peticiones, quejas, reclamos y sugerencias con seguimiento de SLA y respuestas trazables." />

    <div class="grid grid-4" style="margin-bottom:18px">
      <div class="stat accent"><div class="stat-label">Abiertas</div><div class="stat-value">{{ metrics()?.totalOpen ?? 0 }}</div><div class="stat-hint">Radicadas y en gestión</div></div>
      <div class="stat"><div class="stat-label">Dentro del SLA</div><div class="stat-value">{{ metrics()?.onTimeCount ?? 0 }}</div><div class="stat-hint">Atención oportuna</div></div>
      <div class="stat danger"><div class="stat-label">Fuera del SLA</div><div class="stat-value">{{ metrics()?.overdueCount ?? 0 }}</div><div class="stat-hint">Requieren atención</div></div>
      <div class="stat"><div class="stat-label">Tiempo medio</div><div class="stat-value">{{ metrics()?.avgResolutionHours ?? 0 }} h</div><div class="stat-hint">Promedio de resolución</div></div>
    </div>

    <div class="filter-bar">
      <div class="field">
        <label for="category">Categoría</label>
        <select id="category" name="category" class="select" [(ngModel)]="category" (change)="load(0)">
          <option value="">Todas</option>
          @for (option of categories; track option) { <option [value]="option">{{ label(option) }}</option> }
        </select>
      </div>
      <div class="field">
        <label for="status">Estado</label>
        <select id="status" name="status" class="select" [(ngModel)]="status" (change)="load(0)">
          <option value="">Todos</option>
          @for (option of statuses; track option) { <option [value]="option">{{ label(option) }}</option> }
        </select>
      </div>
      <div class="field">
        <label for="priority">Prioridad</label>
        <select id="priority" name="priority" class="select" [(ngModel)]="priority" (change)="load(0)">
          <option value="">Todas</option>
          @for (option of priorities; track option) { <option [value]="option">{{ label(option) }}</option> }
        </select>
      </div>
      <label class="check" style="margin-bottom:9px"><input type="checkbox" [(ngModel)]="overdue" (change)="load(0)"> Solo fuera de SLA</label>
      <button type="button" class="btn btn-secondary" (click)="load(0)">Filtrar</button>
      <button type="button" class="btn btn-ghost" (click)="clearFilters()">Limpiar</button>
    </div>

    <div class="card">
      <div class="card-header"><div><h2>Radicados</h2><p>{{ totalElements() }} PQRS encontradas.</p></div><button type="button" class="btn btn-secondary btn-sm" (click)="load(page())">Actualizar</button></div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando PQRS…</div>
      } @else if (!items().length) {
        <app-empty-state symbol="✉" title="Sin PQRS" message="No hay radicados que coincidan con los filtros." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Radicado</th><th>Asunto</th><th>Categoría</th><th>Prioridad</th><th>SLA</th><th>Estado</th><th style="width:90px"></th></tr></thead>
            <tbody>
              @for (item of items(); track item.id) {
                <tr>
                  <td><div class="main-cell">{{ item.ticketNumber || '—' }}</div><div class="muted small">{{ item.createdAt | date:'dd/MM/yyyy' }}</div></td>
                  <td>{{ item.title }}</td>
                  <td>{{ label(item.category) }}</td>
                  <td><span class="badge" [class]="'badge ' + tone(item.priority)">{{ label(item.priority) }}</span></td>
                  <td>
                    @if (item.overdue) { <span class="badge danger">Vencido</span> }
                    @else { <span class="muted small">{{ item.slaDueAt | date:'dd/MM HH:mm' }}</span> }
                  </td>
                  <td><span class="badge" [class]="'badge ' + tone(item.status)">{{ label(item.status) }}</span></td>
                  <td><button type="button" class="btn btn-secondary btn-sm" (click)="openDetail(item)">Gestionar</button></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-pagination [page]="page()" [totalPages]="totalPages()" [totalElements]="totalElements()" (pageChange)="load($event)" />
      }
    </div>

    <app-modal [open]="detailOpen()" [title]="detail()?.pqr?.ticketNumber || 'Detalle de PQRS'" [subtitle]="detail()?.pqr?.title || ''" [wide]="true" (closed)="detailOpen.set(false)">
      @if (loadingDetail()) {
        <div class="loading"><span class="spinner"></span> Cargando radicado…</div>
      } @else if (detail()?.pqr; as pqr) {
        <div class="grid grid-2">
          <div>
            <dl class="detail-list">
              <dt>Estado</dt><dd><span class="badge" [class]="'badge ' + tone(pqr.status)">{{ label(pqr.status) }}</span></dd>
              <dt>Categoría</dt><dd>{{ label(pqr.category) }}</dd>
              <dt>Prioridad</dt><dd>{{ label(pqr.priority) }}</dd>
              <dt>Radicada</dt><dd>{{ pqr.createdAt | date:'dd/MM/yyyy HH:mm' }}</dd>
              <dt>SLA</dt><dd>{{ pqr.slaDueAt | date:'dd/MM/yyyy HH:mm' }}{{ pqr.overdue ? ' · vencido' : '' }}</dd>
              <dt>Anónima</dt><dd>{{ pqr.isAnonymous ? 'Sí' : 'No' }}</dd>
            </dl>
            <div class="divider"></div>
            <h3 class="section-title">Descripción</h3>
            <p style="white-space:pre-wrap;font-size:13px;line-height:1.6">{{ pqr.description }}</p>
            @if (pqr.resolutionNotes) {
              <div class="notice" style="margin-top:14px"><strong>Resolución:</strong> {{ pqr.resolutionNotes }}</div>
            }
          </div>
          <div>
            <h3 class="section-title">Gestión</h3>
            @if (auth.hasPermission('PQR_ASSIGN')) {
              <div class="field">
                <label for="assignee">Responsable</label>
                <select id="assignee" name="assignee" class="select" [(ngModel)]="assigneeId">
                  <option value="">Sin asignar</option>
                  @for (member of members(); track member.userId) { <option [value]="member.userId">{{ member.fullName }} · {{ label(member.roleCode) }}</option> }
                </select>
                <button type="button" class="btn btn-secondary btn-sm" style="margin-top:8px" [disabled]="actionSaving()" (click)="assign()">Asignar responsable</button>
              </div>
            }
            @if (auth.hasPermission('PQR_RESPOND') || auth.hasPermission('PQR_CLOSE')) {
              <div class="divider"></div>
              <div class="field">
                <label for="newStatus">Nuevo estado</label>
                <select id="newStatus" name="newStatus" class="select" [(ngModel)]="newStatus">
                  @for (option of statuses; track option) { <option [value]="option">{{ label(option) }}</option> }
                </select>
              </div>
              <div class="field" style="margin-top:10px">
                <label for="resolution">Nota de gestión / resolución</label>
                <textarea id="resolution" name="resolution" class="textarea" [(ngModel)]="resolutionNotes"></textarea>
              </div>
              <button type="button" class="btn btn-primary btn-sm" style="margin-top:8px" [disabled]="actionSaving()" (click)="changeStatus()">Actualizar estado</button>
            }
          </div>
        </div>

        <div class="divider"></div>
        <h3 class="section-title">Conversación</h3>
        <div class="timeline">
          @for (message of detail()?.messages; track message.id) {
            <div class="timeline-item">
              <span class="timeline-dot"></span>
              <div class="timeline-content">
                <div class="row between">
                  <strong style="font-size:13px">{{ message.senderName || 'Usuario' }}</strong>
                  <span class="muted small">{{ message.createdAt | date:'dd/MM/yyyy HH:mm' }}{{ message.isInternal ? ' · nota interna' : '' }}</span>
                </div>
                <p style="white-space:pre-wrap;margin-top:5px;font-size:13px">{{ message.message }}</p>
              </div>
            </div>
          } @empty { <p class="muted small">Aún no hay mensajes en este radicado.</p> }
        </div>
        <div class="divider"></div>
        <div class="field">
          <label for="reply">Nueva respuesta o nota</label>
          <textarea id="reply" name="reply" class="textarea" [(ngModel)]="reply" placeholder="Escribe la respuesta para el solicitante."></textarea>
          <label class="check" style="margin-top:7px"><input type="checkbox" [(ngModel)]="internal"> Nota interna (no visible para el residente)</label>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-primary" [disabled]="actionSaving() || !reply.trim()" (click)="sendMessage()">Enviar mensaje</button>
        </div>
      }
    </app-modal>
  `
})
export class PqrsPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly categories = ['PETICION', 'QUEJA', 'RECLAMO', 'SUGERENCIA', 'CONVIVENCIA_RUIDO', 'CONVIVENCIA_MASCOTAS', 'CONVIVENCIA_GENERAL', 'OTRO'];
  readonly statuses = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED'];
  readonly priorities = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];
  readonly items = signal<Pqr[]>([]);
  readonly metrics = signal<PqrSlaMetrics | null>(null);
  readonly members = signal<MemberView[]>([]);
  readonly loading = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly detailOpen = signal(false);
  readonly loadingDetail = signal(false);
  readonly detail = signal<PqrDetail | null>(null);
  readonly actionSaving = signal(false);
  category = '';
  status = '';
  priority = '';
  overdue = false;
  assigneeId = '';
  newStatus = 'IN_PROGRESS';
  resolutionNotes = '';
  reply = '';
  internal = false;

  constructor() {
    void this.load(0);
    void this.loadMetrics();
    void this.loadMembers();
  }

  async load(page = 0): Promise<void> {
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultPqr>('/pqrs', {
        category: this.category,
        status: this.status,
        priority: this.priority,
        overdue: this.overdue ? true : '',
        page,
        size: 20
      }));
      this.items.set(result.content ?? []);
      this.page.set(result.page ?? 0);
      this.totalPages.set(result.totalPages ?? 0);
      this.totalElements.set(result.totalElements ?? 0);
    } finally {
      this.loading.set(false);
    }
  }

  async loadMetrics(): Promise<void> {
    try {
      this.metrics.set(await firstValueFrom(this.api.get<PqrSlaMetrics>('/pqrs/metrics/sla')));
    } catch {
      // El resumen queda en cero si no hay datos o permisos.
    }
  }

  async loadMembers(): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.get<PageResultMemberView>('/tenant-members', { size: 200 }));
      this.members.set(result.content ?? []);
    } catch {
      // La asignación se reintenta al abrir un radicado.
    }
  }

  clearFilters(): void {
    this.category = '';
    this.status = '';
    this.priority = '';
    this.overdue = false;
    void this.load(0);
  }

  async openDetail(item: Pqr): Promise<void> {
    if (!item.id) return;
    this.detailOpen.set(true);
    this.loadingDetail.set(true);
    this.reply = '';
    this.internal = false;
    try {
      const detail = await firstValueFrom(this.api.get<PqrDetail>(`/pqrs/${item.id}`));
      this.detail.set(detail);
      this.assigneeId = detail.pqr?.assignedTo ?? '';
      this.newStatus = detail.pqr?.status === 'CLOSED' ? 'CLOSED' : 'IN_PROGRESS';
      this.resolutionNotes = detail.pqr?.resolutionNotes ?? '';
    } finally {
      this.loadingDetail.set(false);
    }
  }

  async assign(): Promise<void> {
    const pqr = this.detail()?.pqr;
    if (!pqr?.id) return;
    this.actionSaving.set(true);
    try {
      await firstValueFrom(this.api.put<Pqr>(`/pqrs/${pqr.id}/assignee`, { userId: this.assigneeId }));
      this.toast.success('Responsable actualizado.');
      await this.reloadDetail();
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.actionSaving.set(false);
    }
  }

  async changeStatus(): Promise<void> {
    const pqr = this.detail()?.pqr;
    if (!pqr?.id) return;
    if ((this.newStatus === 'CLOSED' || this.newStatus === 'RESOLVED') && !this.resolutionNotes.trim()) {
      this.toast.warning('Indica la nota de resolución para cerrar o resolver el radicado.');
      return;
    }
    this.actionSaving.set(true);
    try {
      await firstValueFrom(this.api.patch<Pqr>(`/pqrs/${pqr.id}/status`, {
        status: this.newStatus,
        resolutionNotes: this.resolutionNotes.trim() || undefined
      }));
      this.toast.success('Estado actualizado.');
      await this.reloadDetail();
      await this.load(this.page());
      await this.loadMetrics();
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.actionSaving.set(false);
    }
  }

  async sendMessage(): Promise<void> {
    const pqr = this.detail()?.pqr;
    if (!pqr?.id || !this.reply.trim()) return;
    this.actionSaving.set(true);
    try {
      await firstValueFrom(this.api.post(`/pqrs/${pqr.id}/messages`, {
        message: this.reply.trim(),
        isInternal: this.internal
      }));
      this.reply = '';
      this.internal = false;
      this.toast.success('Mensaje registrado.');
      await this.reloadDetail();
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.actionSaving.set(false);
    }
  }

  private async reloadDetail(): Promise<void> {
    const pqr = this.detail()?.pqr;
    if (!pqr?.id) return;
    this.detail.set(await firstValueFrom(this.api.get<PqrDetail>(`/pqrs/${pqr.id}`)));
  }
}
