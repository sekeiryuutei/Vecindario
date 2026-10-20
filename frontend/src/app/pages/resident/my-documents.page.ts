import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService, saveBlob } from '../../core/api.service';
import { FileSizePipe } from '../../core/format.pipes';
import { label } from '../../core/labels';
import { Document as ResidentDocument } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, PageHeaderComponent } from '../../shared/ui.components';

type DocumentCategory = NonNullable<ResidentDocument['category']>;

@Component({
  selector: 'app-my-documents-page',
  standalone: true,
  imports: [FormsModule, DatePipe, FileSizePipe, PageHeaderComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Documentos" subtitle="Actas, reglamentos, estados financieros y documentos disponibles para tu rol." />

    <div class="grid grid-4">
      <div class="stat accent"><div class="stat-label">Documentos</div><div class="stat-value">{{ items().length }}</div><div class="stat-hint">Disponibles para tu rol</div></div>
      <div class="stat"><div class="stat-label">Categorías</div><div class="stat-value">{{ categoryCount() }}</div><div class="stat-hint">Con documentos publicados</div></div>
      <div class="stat"><div class="stat-label">Última publicación</div><div class="stat-value" style="font-size:20px">{{ lastPublished() ? (lastPublished() | date:'dd/MM/yyyy') : 'Sin registros' }}</div><div class="stat-hint">Documento más reciente</div></div>
      <div class="stat"><div class="stat-label">Tamaño total</div><div class="stat-value">{{ totalSize() | fileSize }}</div><div class="stat-hint">Suma de los documentos</div></div>
    </div>

    <div class="filter-bar">
      <div class="field">
        <label for="category">Categoría</label>
        <select id="category" name="category" class="select" [(ngModel)]="category" (change)="load()">
          <option value="">Todas</option>
          @for (option of categories; track option) { <option [value]="option">{{ label(option) }}</option> }
        </select>
      </div>
      <button type="button" class="btn btn-secondary" (click)="load()">Filtrar</button>
      <button type="button" class="btn btn-ghost" (click)="category = ''; load()">Limpiar</button>
    </div>

    <div class="card">
      <div class="card-header"><div><h2>Biblioteca de documentos</h2><p>Descarga los archivos que la administración ha publicado para la comunidad.</p></div><button type="button" class="btn btn-secondary btn-sm" (click)="load()">Actualizar</button></div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando documentos…</div>
      } @else if (!items().length) {
        <app-empty-state symbol="▤" title="Sin documentos" message="No hay documentos publicados para tu rol con los filtros seleccionados." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Documento</th><th>Categoría</th><th>Tamaño</th><th>Publicado</th><th style="width:120px"></th></tr></thead>
            <tbody>
              @for (item of items(); track item.id) {
                <tr>
                  <td><div class="main-cell">{{ item.title }}</div><div class="muted small">{{ item.fileName }} · {{ item.description || 'Sin descripción' }}</div></td>
                  <td>{{ label(item.category) }}</td>
                  <td class="num">{{ item.fileSize | fileSize }}</td>
                  <td class="nowrap">{{ item.createdAt | date:'dd/MM/yyyy' }}</td>
                  <td class="num">
                    <button type="button" class="btn btn-secondary btn-sm" [disabled]="downloading() === item.id" (click)="download(item)">
                      {{ downloading() === item.id ? 'Descargando…' : 'Descargar' }}
                    </button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `
})
export class MyDocumentsPage {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly categories: DocumentCategory[] = ['ACTA_ASAMBLEA', 'ESTADO_FINANCIERO', 'REGLAMENTO_PROPIEDAD_HORIZONTAL', 'PRESUPUESTO', 'CONTRATO', 'CIRCULAR', 'OTRO'];
  readonly items = signal<ResidentDocument[]>([]);
  readonly loading = signal(false);
  readonly downloading = signal<string | null>(null);
  readonly categoryCount = computed(() => new Set(this.items().map((item) => item.category).filter(Boolean)).size);
  readonly totalSize = computed(() => this.items().reduce((total, item) => total + Number(item.fileSize ?? 0), 0));
  readonly lastPublished = computed(() => {
    const dates = this.items().map((item) => item.createdAt).filter((value): value is string => Boolean(value)).sort();
    return dates.length ? dates[dates.length - 1] : '';
  });
  category = '';

  constructor() {
    void this.load();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.items.set(await firstValueFrom(this.api.get<ResidentDocument[]>('/my/documents', { category: this.category })));
    } catch (error) {
      this.items.set([]);
      this.toast.error(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  async download(item: ResidentDocument): Promise<void> {
    if (!item.id) return;
    this.downloading.set(item.id);
    try {
      const fallback = item.fileName || 'documento';
      const response = await firstValueFrom(this.api.download(`/my/documents/${item.id}/download`, undefined, fallback));
      saveBlob(response, fallback);
      this.toast.success('Documento descargado.');
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.downloading.set(null);
    }
  }
}
