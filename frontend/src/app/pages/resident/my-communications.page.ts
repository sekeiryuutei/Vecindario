import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { label, statusTone } from '../../core/labels';
import {
  MyUnit,
  PageResultResidentCommunicationItem,
  ResidentCommunicationItem
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-my-communications-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, ModalComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Comunicados" subtitle="Avisos, circulares y novedades publicadas por la administración para la comunidad." />

    <div class="grid grid-4">
      <div class="stat accent"><div class="stat-label">Comunicados</div><div class="stat-value">{{ totalElements() }}</div><div class="stat-hint">Disponibles para tu rol</div></div>
      <div class="stat warn"><div class="stat-label">Sin leer</div><div class="stat-value">{{ unreadCount() }}</div><div class="stat-hint">En esta página</div></div>
      <div class="stat"><div class="stat-label">Fijados</div><div class="stat-value">{{ pinnedCount() }}</div><div class="stat-hint">Destacados por la administración</div></div>
      <div class="stat"><div class="stat-label">Última publicación</div><div class="stat-value" style="font-size:20px">{{ lastPublished() ? (lastPublished() | date:'dd/MM/yyyy') : 'Sin registros' }}</div><div class="stat-hint">Fecha más reciente</div></div>
    </div>

    <div class="filter-bar">
      <div class="field">
        <label for="towerFilter">Torre</label>
        <select id="towerFilter" name="towerFilter" class="select" [(ngModel)]="tower" (change)="load(0)">
          <option value="">Todas las torres</option>
          @for (option of towers(); track option) { <option [value]="option">{{ option }}</option> }
        </select>
      </div>
      <button type="button" class="btn btn-secondary" (click)="load(page())">Actualizar</button>
    </div>

    @if (loading()) {
      <div class="loading"><span class="spinner"></span> Cargando comunicados…</div>
    } @else if (!items().length) {
      <div class="card"><app-empty-state symbol="◉" title="Sin comunicados" message="No hay comunicados publicados para tu rol en este momento." /></div>
    } @else {
      <div class="stack" style="gap:12px">
        @for (item of items(); track item.communication?.id) {
          <article class="card" [style.border-left]="item.communication?.priority === 'URGENT' ? '4px solid var(--danger)' : (item.communication?.priority === 'IMPORTANT' ? '4px solid var(--warning)' : null)">
            <div class="card-header" style="margin-bottom:0">
              <div>
                <div class="row" style="gap:8px;flex-wrap:wrap">
                  <h2 style="font-size:15px">{{ item.communication?.title }}</h2>
                  @if (!item.isRead) { <span class="badge info">Nuevo</span> }
                  @if (item.communication?.isPinned) { <span class="badge warning">Fijado</span> }
                  <span class="badge" [class]="'badge ' + tone(item.communication?.priority)">{{ label(item.communication?.priority) }}</span>
                </div>
                <p style="margin-top:4px">
                  {{ item.communication?.publishedAt | date:'dd/MM/yyyy HH:mm' }} · {{ label(item.communication?.targetAudience) }}
                  @if (item.communication?.targetTower) { · Torre {{ item.communication?.targetTower }} }
                </p>
              </div>
              <button type="button" class="btn btn-secondary btn-sm" (click)="open(item)">Leer comunicado</button>
            </div>
            <div class="card-body" style="padding-top:10px">
              <p class="muted" style="font-size:13px;line-height:1.55;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden">{{ item.communication?.summary || item.communication?.content }}</p>
            </div>
          </article>
        }
      </div>
      <app-pagination [page]="page()" [totalPages]="totalPages()" [totalElements]="totalElements()" (pageChange)="load($event)" />
    }

    <app-modal [open]="viewOpen()" [title]="selected()?.communication?.title || 'Comunicado'" [subtitle]="selected()?.communication?.summary || ''" [wide]="true" (closed)="viewOpen.set(false)">
      @if (selected()?.communication; as communication) {
        <div class="row" style="gap:8px;margin-bottom:14px;flex-wrap:wrap">
          <span class="badge" [class]="'badge ' + tone(communication.priority)">{{ label(communication.priority) }}</span>
          <span class="badge info">{{ label(communication.targetAudience) }}</span>
          @if (communication.isPinned) { <span class="badge warning">Fijado</span> }
          <span class="muted small">{{ communication.publishedAt | date:'dd/MM/yyyy HH:mm' }}</span>
        </div>
        <p style="white-space:pre-wrap;line-height:1.7;font-size:13.5px">{{ communication.content }}</p>
        @if (communication.expiresAt) { <div class="notice" style="margin-top:16px">Vigente hasta {{ communication.expiresAt | date:'dd/MM/yyyy HH:mm' }}.</div> }
      }
    </app-modal>
  `
})
export class MyCommunicationsPage {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly items = signal<ResidentCommunicationItem[]>([]);
  readonly units = signal<MyUnit[]>([]);
  readonly loading = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly viewOpen = signal(false);
  readonly selected = signal<ResidentCommunicationItem | null>(null);
  readonly unreadCount = computed(() => this.items().filter((item) => !item.isRead).length);
  readonly pinnedCount = computed(() => this.items().filter((item) => item.communication?.isPinned).length);
  readonly lastPublished = computed(() => {
    const dates = this.items()
      .map((item) => item.communication?.publishedAt)
      .filter((value): value is string => Boolean(value))
      .sort();
    return dates.length ? dates[dates.length - 1] : '';
  });
  readonly towers = computed(() => [...new Set(this.units().map((unit) => unit.tower).filter((value): value is string => Boolean(value)))].sort());
  tower = '';

  constructor() {
    void this.loadUnits();
    void this.load(0);
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
      const result = await firstValueFrom(this.api.get<PageResultResidentCommunicationItem>('/my/communications', {
        tower: this.tower,
        page,
        size: 10
      }));
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

  async open(item: ResidentCommunicationItem): Promise<void> {
    this.selected.set(item);
    this.viewOpen.set(true);
    const id = item.communication?.id;
    if (!id || item.isRead) return;
    try {
      await firstValueFrom(this.api.post<void>(`/my/communications/${id}/read`));
      this.items.update((items) => items.map((current) =>
        current.communication?.id === id ? { ...current, isRead: true } : current
      ));
      this.selected.set({ ...item, isRead: true });
    } catch {
      // La lectura es complementaria: el comunicado ya está visible.
    }
  }
}
