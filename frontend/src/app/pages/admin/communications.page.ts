import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ConfirmService } from '../../core/confirm.service';
import { label, statusTone } from '../../core/labels';
import {
  Communication,
  CreateCommunicationRequest,
  PageResultCommunication
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError, toIsoDateTime } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-communications-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, ModalComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Comunicados" subtitle="Publica avisos, circulares e información relevante para la comunidad.">
      @if (auth.hasPermission('COMMUNICATIONS_MANAGE')) {
        <button type="button" class="btn btn-primary" (click)="openCreate()">Nuevo comunicado</button>
      }
    </app-page-header>

    <div class="filter-bar">
      <div class="field">
        <label for="status">Estado</label>
        <select id="status" name="status" class="select" [(ngModel)]="status" (change)="load(0)">
          <option value="">Todos</option>
          @for (option of statuses; track option) { <option [value]="option">{{ label(option) }}</option> }
        </select>
      </div>
      <button type="button" class="btn btn-secondary" (click)="load(0)">Filtrar</button>
      <button type="button" class="btn btn-ghost" (click)="status = ''; load(0)">Limpiar</button>
    </div>

    <div class="card">
      <div class="card-header"><div><h2>Comunicados</h2><p>{{ totalElements() }} publicación(es).</p></div><button type="button" class="btn btn-secondary btn-sm" (click)="load(page())">Actualizar</button></div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando comunicados…</div>
      } @else if (!items().length) {
        <app-empty-state symbol="◉" title="Sin comunicados" message="Publica el primer comunicado para informar a la comunidad." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Título</th><th>Audiencia</th><th>Prioridad</th><th>Publicación</th><th>Vence</th><th>Estado</th><th style="width:170px"></th></tr></thead>
            <tbody>
              @for (item of items(); track item.id) {
                <tr>
                  <td><div class="main-cell">{{ item.title }}</div><div class="muted small" style="max-width:420px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ item.summary || item.content }}</div></td>
                  <td>{{ label(item.targetAudience) }}{{ item.targetTower ? ' · ' + item.targetTower : '' }}</td>
                  <td><span class="badge" [class]="'badge ' + tone(item.priority)">{{ label(item.priority) }}</span></td>
                  <td>{{ item.publishedAt ? (item.publishedAt | date:'dd/MM/yyyy HH:mm') : '—' }}</td>
                  <td>{{ item.expiresAt ? (item.expiresAt | date:'dd/MM/yyyy') : '—' }}</td>
                  <td><span class="badge" [class]="'badge ' + tone(item.status)">{{ label(item.status) }}</span></td>
                  <td>
                    <div class="table-actions">
                      <button type="button" class="btn-icon" title="Ver detalle" (click)="openView(item)">👁</button>
                      @if (item.status === 'DRAFT' && auth.hasPermission('COMMUNICATIONS_MANAGE')) {
                        <button type="button" class="btn btn-primary btn-sm" (click)="publish(item)">Publicar</button>
                      }
                      @if (item.status === 'PUBLISHED' && auth.hasPermission('COMMUNICATIONS_MANAGE')) {
                        <button type="button" class="btn btn-secondary btn-sm" (click)="archive(item)">Archivar</button>
                      }
                      @if (auth.hasPermission('COMMUNICATIONS_MANAGE')) {
                        <button type="button" class="btn-icon danger" title="Eliminar" (click)="remove(item)">🗑</button>
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

    <app-modal [open]="createOpen()" title="Nuevo comunicado" subtitle="Puedes guardarlo como borrador o publicarlo de inmediato." [wide]="true" (closed)="createOpen.set(false)">
      @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
      <form (ngSubmit)="save()" novalidate>
        <div class="form-grid">
          <div class="field span-2"><label for="title">Título *</label><input id="title" name="title" class="input" required maxlength="200" [(ngModel)]="form.title"></div>
          <div class="field span-2"><label for="summary">Resumen</label><input id="summary" name="summary" class="input" maxlength="300" [(ngModel)]="form.summary"></div>
          <div class="field span-2"><label for="content">Contenido *</label><textarea id="content" name="content" class="textarea" style="min-height:170px" required [(ngModel)]="form.content"></textarea></div>
          <div class="field">
            <label for="priority">Prioridad</label>
            <select id="priority" name="priority" class="select" [(ngModel)]="form.priority">
              <option value="NORMAL">Normal</option><option value="IMPORTANT">Importante</option><option value="URGENT">Urgente</option>
            </select>
          </div>
          <div class="field">
            <label for="audience">Audiencia</label>
            <select id="audience" name="audience" class="select" [(ngModel)]="form.targetAudience">
              <option value="ALL">Todos</option><option value="OWNERS">Propietarios</option><option value="TENANTS">Arrendatarios</option>
              <option value="RESIDENTS">Residentes</option><option value="COUNCIL">Consejo</option>
            </select>
          </div>
          <div class="field"><label for="tower">Torre específica</label><input id="tower" name="tower" class="input" [(ngModel)]="form.targetTower" placeholder="Opcional"></div>
          <div class="field"><label for="expires">Vigencia hasta</label><input id="expires" name="expires" type="date" class="input" [(ngModel)]="expiresDate"></div>
          <div class="field"><label class="check"><input type="checkbox" [(ngModel)]="form.isPinned"> Fijar en la parte superior</label></div>
          <div class="field"><label class="check"><input type="checkbox" [(ngModel)]="form.publishNow"> Publicar inmediatamente</label></div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="createOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="saving()">{{ form.publishNow ? 'Publicar comunicado' : 'Guardar borrador' }}</button>
        </div>
      </form>
    </app-modal>

    <app-modal [open]="viewOpen()" [title]="selected()?.title || 'Comunicado'" [subtitle]="selected()?.summary || ''" (closed)="viewOpen.set(false)">
      @if (selected(); as item) {
        <div class="row" style="gap:8px;margin-bottom:14px">
          <span class="badge" [class]="'badge ' + tone(item.priority)">{{ label(item.priority) }}</span>
          <span class="badge info">{{ label(item.targetAudience) }}</span>
          @if (item.isPinned) { <span class="badge warning">Fijado</span> }
          <span class="muted small">{{ item.publishedAt | date:'dd/MM/yyyy HH:mm' }}</span>
        </div>
        <p style="white-space:pre-wrap;line-height:1.65;font-size:13.5px">{{ item.content }}</p>
      }
    </app-modal>
  `
})
export class CommunicationsPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly statuses = ['DRAFT', 'PUBLISHED', 'ARCHIVED'];
  readonly items = signal<Communication[]>([]);
  readonly loading = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly createOpen = signal(false);
  readonly viewOpen = signal(false);
  readonly selected = signal<Communication | null>(null);
  readonly saving = signal(false);
  readonly error = signal('');
  status = '';
  expiresDate = '';
  form: CreateCommunicationRequest = this.emptyForm();

  constructor() {
    void this.load(0);
  }

  async load(page = 0): Promise<void> {
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultCommunication>('/communications', { status: this.status, page, size: 20 }));
      this.items.set(result.content ?? []);
      this.page.set(result.page ?? 0);
      this.totalPages.set(result.totalPages ?? 0);
      this.totalElements.set(result.totalElements ?? 0);
    } finally {
      this.loading.set(false);
    }
  }

  openCreate(): void {
    this.form = this.emptyForm();
    this.expiresDate = '';
    this.error.set('');
    this.createOpen.set(true);
  }

  async save(): Promise<void> {
    this.error.set('');
    if (!this.form.title.trim() || !this.form.content.trim()) {
      this.error.set('El título y el contenido son obligatorios.');
      return;
    }
    this.saving.set(true);
    try {
      const body: CreateCommunicationRequest = {
        ...this.form,
        title: this.form.title.trim(),
        summary: this.form.summary?.trim() || undefined,
        content: this.form.content.trim(),
        targetTower: this.form.targetTower?.trim() || undefined,
        expiresAt: toIsoDateTime(this.expiresDate ? `${this.expiresDate}T23:59` : '')
      };
      await firstValueFrom(this.api.post<Communication>('/communications', body));
      this.toast.success(body.publishNow ? 'Comunicado publicado.' : 'Borrador guardado.');
      this.createOpen.set(false);
      await this.load(0);
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async publish(item: Communication): Promise<void> {
    if (!item.id) return;
    await firstValueFrom(this.api.post<Communication>(`/communications/${item.id}/publish`));
    this.toast.success('Comunicado publicado.');
    await this.load(this.page());
  }

  async archive(item: Communication): Promise<void> {
    if (!item.id) return;
    const accepted = await this.confirm.confirm({
      title: 'Archivar comunicado',
      message: `¿Deseas archivar "${item.title}"? Dejará de ser visible para la comunidad.`,
      confirmLabel: 'Archivar'
    });
    if (!accepted) return;
    await firstValueFrom(this.api.post<Communication>(`/communications/${item.id}/archive`));
    this.toast.success('Comunicado archivado.');
    await this.load(this.page());
  }

  async remove(item: Communication): Promise<void> {
    if (!item.id) return;
    const accepted = await this.confirm.confirm({
      title: 'Eliminar comunicado',
      message: `¿Deseas eliminar "${item.title}"? Esta acción no se puede deshacer.`,
      confirmLabel: 'Eliminar',
      danger: true
    });
    if (!accepted) return;
    await firstValueFrom(this.api.delete<void>(`/communications/${item.id}`));
    this.toast.success('Comunicado eliminado.');
    await this.load(this.page());
  }

  openView(item: Communication): void {
    this.selected.set(item);
    this.viewOpen.set(true);
  }

  private emptyForm(): CreateCommunicationRequest {
    return {
      title: '',
      summary: '',
      content: '',
      priority: 'NORMAL',
      targetAudience: 'ALL',
      targetTower: '',
      isPinned: false,
      publishNow: false
    };
  }
}
