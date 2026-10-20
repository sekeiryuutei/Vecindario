import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { label, statusTone } from '../../core/labels';
import {
  Incident,
  IncidentRequest,
  PageResultIncident
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError, toIsoInstant } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

type IncidentCategory = IncidentRequest['category'];

@Component({
  selector: 'app-porter-incidents-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, ModalComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Novedades de portería" subtitle="Registra hechos relevantes del turno y da seguimiento a las novedades reportadas.">
      @if (auth.hasPermission('INCIDENTS_CREATE')) {
        <button type="button" class="btn btn-primary" (click)="openCreate()">Reportar novedad</button>
      }
    </app-page-header>

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
      <button type="button" class="btn btn-secondary" (click)="load(0)">Filtrar</button>
      <button type="button" class="btn btn-ghost" (click)="clearFilters()">Limpiar</button>
    </div>

    <div class="card">
      <div class="card-header"><div><h2>Novedades</h2><p>{{ totalElements() }} novedad(es) registradas.</p></div><button type="button" class="btn btn-secondary btn-sm" (click)="load(page())">Actualizar</button></div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando novedades…</div>
      } @else if (!items().length) {
        <app-empty-state symbol="!" title="Sin novedades" message="No hay novedades que coincidan con los filtros seleccionados." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Ocurrencia</th><th>Categoría</th><th>Descripción</th><th>Ubicación</th><th>Estado</th><th style="width:120px"></th></tr></thead>
            <tbody>
              @for (item of items(); track item.id) {
                <tr>
                  <td class="nowrap">{{ (item.occurredAt || item.createdAt) | date:'dd/MM/yyyy HH:mm' }}</td>
                  <td><span class="badge warning">{{ label(item.category) }}</span></td>
                  <td>
                    <div class="main-cell" style="max-width:460px">{{ item.description }}</div>
                    @if (item.resolution) { <div class="muted small">Resolución: {{ item.resolution }}</div> }
                  </td>
                  <td>{{ item.location || '—' }}</td>
                  <td><span class="badge" [class]="'badge ' + tone(item.status)">{{ label(item.status) }}</span></td>
                  <td>
                    @if (auth.hasPermission('INCIDENTS_MANAGE')) {
                      <button type="button" class="btn btn-secondary btn-sm" (click)="openStatus(item)">Gestionar</button>
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

    <app-modal [open]="createOpen()" title="Reportar novedad" subtitle="Describe lo ocurrido para que la administración pueda hacer seguimiento." (closed)="createOpen.set(false)">
      @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
      <form (ngSubmit)="create()" novalidate>
        <div class="form-grid">
          <div class="field">
            <label for="newCategory">Categoría *</label>
            <select id="newCategory" name="newCategory" class="select" [(ngModel)]="form.category">
              @for (option of categories; track option) { <option [value]="option">{{ label(option) }}</option> }
            </select>
          </div>
          <div class="field"><label for="occurredAt">Fecha y hora</label><input id="occurredAt" name="occurredAt" type="datetime-local" class="input" [(ngModel)]="occurredAt"></div>
          <div class="field span-2"><label for="location">Ubicación</label><input id="location" name="location" class="input" maxlength="150" [(ngModel)]="form.location" placeholder="Torre, área común, parqueadero, etc."></div>
          <div class="field span-2"><label for="description">Descripción *</label><textarea id="description" name="description" class="textarea" required maxlength="2000" [(ngModel)]="form.description" style="min-height:130px"></textarea></div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="createOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="saving()">{{ saving() ? 'Guardando…' : 'Registrar novedad' }}</button>
        </div>
      </form>
    </app-modal>

    <app-modal [open]="statusOpen()" title="Gestionar novedad" [subtitle]="selected()?.description || ''" (closed)="statusOpen.set(false)">
      @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
      <div class="field">
        <label for="newStatus">Estado *</label>
        <select id="newStatus" name="newStatus" class="select" [(ngModel)]="newStatus">
          @for (option of statuses; track option) { <option [value]="option">{{ label(option) }}</option> }
        </select>
      </div>
      <div class="field" style="margin-top:12px">
        <label for="resolution">Resolución / seguimiento</label>
        <textarea id="resolution" name="resolution" class="textarea" maxlength="500" [(ngModel)]="resolution" placeholder="Describe las acciones realizadas."></textarea>
        <span class="hint">Obligatoria para cerrar la novedad.</span>
      </div>
      <div class="form-actions">
        <button type="button" class="btn btn-secondary" (click)="statusOpen.set(false)">Cancelar</button>
        <button type="button" class="btn btn-primary" [disabled]="saving()" (click)="updateStatus()">{{ saving() ? 'Guardando…' : 'Actualizar estado' }}</button>
      </div>
    </app-modal>
  `
})
export class PorterIncidentsPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly categories: IncidentCategory[] = ['DANO', 'SEGURIDAD', 'RUIDO', 'VEHICULO', 'VISITANTE', 'INFRAESTRUCTURA', 'EMERGENCIA', 'OTRO'];
  readonly statuses = ['OPEN', 'IN_REVIEW', 'CLOSED'];
  readonly items = signal<Incident[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly createOpen = signal(false);
  readonly statusOpen = signal(false);
  readonly selected = signal<Incident | null>(null);
  readonly error = signal('');
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  category = '';
  status = '';
  occurredAt = '';
  newStatus = 'IN_REVIEW';
  resolution = '';
  form: IncidentRequest = this.emptyForm();

  constructor() {
    void this.load(0);
  }

  async load(page = 0): Promise<void> {
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultIncident>('/incidents', {
        category: this.category,
        status: this.status,
        page,
        size: 20
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
    this.category = '';
    this.status = '';
    void this.load(0);
  }

  openCreate(): void {
    this.form = this.emptyForm();
    this.occurredAt = this.localNow();
    this.error.set('');
    this.createOpen.set(true);
  }

  async create(): Promise<void> {
    this.error.set('');
    if (!this.form.description.trim()) {
      this.error.set('Describe la novedad para poder registrarla.');
      return;
    }
    this.saving.set(true);
    try {
      await firstValueFrom(this.api.post<Incident>('/incidents', {
        category: this.form.category,
        description: this.form.description.trim(),
        location: this.form.location?.trim() || undefined,
        occurredAt: toIsoInstant(this.occurredAt)
      }));
      this.toast.success('Novedad registrada.');
      this.createOpen.set(false);
      await this.load(0);
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  openStatus(item: Incident): void {
    this.selected.set(item);
    this.newStatus = item.status ?? 'IN_REVIEW';
    this.resolution = item.resolution ?? '';
    this.error.set('');
    this.statusOpen.set(true);
  }

  async updateStatus(): Promise<void> {
    const item = this.selected();
    if (!item?.id) return;
    if (this.newStatus === 'CLOSED' && !this.resolution.trim()) {
      this.error.set('Indica la resolución para cerrar la novedad.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.api.patch<Incident>(`/incidents/${item.id}/status`, {
        status: this.newStatus,
        resolution: this.resolution.trim() || undefined
      }));
      this.toast.success('Novedad actualizada.');
      this.statusOpen.set(false);
      await this.load(this.page());
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  private emptyForm(): IncidentRequest {
    return { category: 'SEGURIDAD', description: '', location: '', occurredAt: undefined };
  }

  private localNow(): string {
    const date = new Date();
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }
}
