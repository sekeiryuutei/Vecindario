import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ConfirmService } from '../../core/confirm.service';
import { label, statusTone } from '../../core/labels';
import { PageResultPropertyUnit, PropertyRequest, PropertyUnit } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-properties-page',
  standalone: true,
  imports: [FormsModule, PageHeaderComponent, ModalComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Inmuebles" subtitle="Apartamentos, casas, locales, parqueaderos y depósitos de la copropiedad.">
      @if (auth.hasPermission('PROPERTIES_CREATE')) {
        <button type="button" class="btn btn-primary" (click)="openCreate()">Nuevo inmueble</button>
      }
    </app-page-header>

    <div class="filter-bar">
      <div class="field grow">
        <label for="q">Buscar</label>
        <input id="q" name="q" class="input" [(ngModel)]="query" (keyup.enter)="load(0)" placeholder="Identificador, torre o número">
      </div>
      <div class="field">
        <label for="type">Tipo</label>
        <select id="type" name="type" class="select" [(ngModel)]="type" (change)="load(0)">
          <option value="">Todos</option>
          @for (option of types; track option) { <option [value]="option">{{ label(option) }}</option> }
        </select>
      </div>
      <button type="button" class="btn btn-secondary" (click)="load(0)">Filtrar</button>
      <button type="button" class="btn btn-ghost" (click)="clearFilters()">Limpiar</button>
    </div>

    <div class="card">
      <div class="card-header">
        <div><h2>Listado de inmuebles</h2><p>{{ totalElements() }} registro(s) en la copropiedad.</p></div>
        <button type="button" class="btn btn-secondary btn-sm" (click)="load(page())">Actualizar</button>
      </div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando inmuebles…</div>
      } @else if (!items().length) {
        <app-empty-state symbol="⌂" title="Sin inmuebles" message="No hay inmuebles que coincidan con los filtros. Crea el primero para comenzar." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th>Identificador</th><th>Tipo</th><th>Torre</th><th>Piso</th>
                <th class="num">Coeficiente</th><th class="num">Área m²</th><th>Estado</th><th style="width:110px"></th>
              </tr>
            </thead>
            <tbody>
              @for (item of items(); track item.id) {
                <tr>
                  <td class="main-cell">{{ item.identifier }}</td>
                  <td>{{ label(item.type) }}</td>
                  <td>{{ item.tower || '—' }}</td>
                  <td>{{ item.floorNumber ?? '—' }}</td>
                  <td class="num">{{ item.coefficient ?? '—' }}</td>
                  <td class="num">{{ item.areaM2 ?? '—' }}</td>
                  <td><span class="badge" [class]="'badge ' + tone(item.status)">{{ label(item.status) }}</span></td>
                  <td>
                    <div class="table-actions">
                      @if (auth.hasPermission('PROPERTIES_UPDATE')) {
                        <button type="button" class="btn-icon" title="Editar" (click)="openEdit(item)">✎</button>
                      }
                      @if (auth.hasPermission('PROPERTIES_DELETE')) {
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

    <app-modal [open]="modalOpen()" [title]="editing() ? 'Editar inmueble' : 'Nuevo inmueble'" subtitle="Los datos del inmueble se usan en cartera, reservas, visitantes y certificados." (closed)="closeModal()">
      @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
      <form (ngSubmit)="save()" novalidate>
        <div class="form-grid">
          <div class="field">
            <label for="identifier">Identificador *</label>
            <input id="identifier" name="identifier" class="input" required [(ngModel)]="form.identifier" placeholder="Ej. A-101">
          </div>
          <div class="field">
            <label for="propertyType">Tipo *</label>
            <select id="propertyType" name="propertyType" class="select" required [(ngModel)]="form.type">
              @for (option of types; track option) { <option [value]="option">{{ label(option) }}</option> }
            </select>
          </div>
          <div class="field">
            <label for="unitNumber">Número</label>
            <input id="unitNumber" name="unitNumber" class="input" [(ngModel)]="form.unitNumber">
          </div>
          <div class="field">
            <label for="tower">Torre / bloque</label>
            <input id="tower" name="tower" class="input" [(ngModel)]="form.tower">
          </div>
          <div class="field">
            <label for="floor">Piso</label>
            <input id="floor" name="floor" type="number" class="input" [(ngModel)]="form.floorNumber">
          </div>
          <div class="field">
            <label for="coefficient">Coeficiente</label>
            <input id="coefficient" name="coefficient" type="number" step="0.000001" class="input" [(ngModel)]="form.coefficient">
            <span class="hint">Se usa para cuotas ordinarias y votaciones por coeficiente.</span>
          </div>
          <div class="field">
            <label for="area">Área (m²)</label>
            <input id="area" name="area" type="number" step="0.01" class="input" [(ngModel)]="form.areaM2">
          </div>
          <div class="field">
            <label for="status">Estado</label>
            <select id="status" name="status" class="select" [(ngModel)]="form.status">
              <option value="ACTIVE">Activo</option>
              <option value="INACTIVE">Inactivo</option>
            </select>
          </div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="closeModal()">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="saving()">
            @if (saving()) { <span class="spinner" style="border-color:rgba(255,255,255,.4);border-top-color:#fff"></span> }
            {{ editing() ? 'Guardar cambios' : 'Crear inmueble' }}
          </button>
        </div>
      </form>
    </app-modal>
  `
})
export class PropertiesPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly types = ['APARTAMENTO', 'CASA', 'LOCAL', 'PARQUEADERO', 'DEPOSITO', 'OTRO'] as const;
  readonly items = signal<PropertyUnit[]>([]);
  readonly loading = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly modalOpen = signal(false);
  readonly editing = signal<PropertyUnit | null>(null);
  readonly saving = signal(false);
  readonly error = signal('');
  query = '';
  type = '';
  form: PropertyRequest = this.emptyForm();

  constructor() {
    void this.load();
  }

  async load(page = 0): Promise<void> {
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultPropertyUnit>('/properties', {
        q: this.query.trim(),
        type: this.type,
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

  clearFilters(): void {
    this.query = '';
    this.type = '';
    void this.load(0);
  }

  openCreate(): void {
    this.editing.set(null);
    this.form = this.emptyForm();
    this.error.set('');
    this.modalOpen.set(true);
  }

  openEdit(item: PropertyUnit): void {
    this.editing.set(item);
    this.form = {
      type: item.type ?? 'APARTAMENTO',
      identifier: item.identifier ?? '',
      unitNumber: item.unitNumber,
      tower: item.tower,
      floorNumber: item.floorNumber,
      coefficient: item.coefficient,
      areaM2: item.areaM2,
      status: item.status ?? 'ACTIVE'
    };
    this.error.set('');
    this.modalOpen.set(true);
  }

  closeModal(): void {
    this.modalOpen.set(false);
  }

  async save(): Promise<void> {
    this.error.set('');
    if (!this.form.identifier.trim()) {
      this.error.set('El identificador del inmueble es obligatorio.');
      return;
    }
    this.saving.set(true);
    try {
      const editing = this.editing();
      if (editing?.id) {
        await firstValueFrom(this.api.put<PropertyUnit>(`/properties/${editing.id}`, this.form));
        this.toast.success('Inmueble actualizado.');
      } else {
        await firstValueFrom(this.api.post<PropertyUnit>('/properties', this.form));
        this.toast.success('Inmueble creado.');
      }
      this.modalOpen.set(false);
      await this.load(this.page());
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async remove(item: PropertyUnit): Promise<void> {
    const accepted = await this.confirm.confirm({
      title: 'Eliminar inmueble',
      message: `¿Deseas eliminar ${item.identifier}? La operación se conserva en auditoría y no se puede deshacer desde el portal.`,
      confirmLabel: 'Eliminar',
      danger: true
    });
    if (!accepted || !item.id) return;
    try {
      await firstValueFrom(this.api.delete<void>(`/properties/${item.id}`));
      this.toast.success('Inmueble eliminado.');
      await this.load(this.page());
    } catch {
      // El interceptor global muestra el detalle del error.
    }
  }

  private emptyForm(): PropertyRequest {
    return { type: 'APARTAMENTO', identifier: '', status: 'ACTIVE' };
  }
}
