import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService, saveBlob } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ConfirmService } from '../../core/confirm.service';
import { FileSizePipe } from '../../core/format.pipes';
import { label } from '../../core/labels';
import { Document, PageResultPropertyUnit, PropertyUnit } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent } from '../../shared/ui.components';

type DocumentCategory = NonNullable<Document['category']>;

@Component({
  selector: 'app-documents-page',
  standalone: true,
  imports: [FormsModule, DatePipe, FileSizePipe, PageHeaderComponent, ModalComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Documentos" subtitle="Actas, reglamentos, estados financieros y documentos de consulta para la comunidad.">
      <button type="button" class="btn btn-primary" (click)="openUpload()">Subir documento</button>
    </app-page-header>

    <div class="filter-bar">
      <div class="field">
        <label for="category">Categoría</label>
        <select id="category" name="category" class="select" [(ngModel)]="category" (change)="load()">
          <option value="">Todas</option>
          @for (option of categories; track option) { <option [value]="option">{{ label(option) }}</option> }
        </select>
      </div>
      <div class="field">
        <label for="documentUnit">Inmueble</label>
        <select id="documentUnit" name="documentUnit" class="select" [(ngModel)]="unitFilter" (change)="load()">
          <option value="">Todos</option>
          @for (unit of units(); track unit.id) { <option [value]="unit.id">{{ unit.identifier }} — {{ unit.tower || 'Sin torre' }}</option> }
        </select>
      </div>
      <button type="button" class="btn btn-secondary" (click)="load()">Filtrar</button>
      <button type="button" class="btn btn-ghost" (click)="category = ''; unitFilter = ''; load()">Limpiar</button>
    </div>

    <div class="card">
      <div class="card-header"><div><h2>Biblioteca de documentos</h2><p>{{ items().length }} documento(s) activos.</p></div><button type="button" class="btn btn-secondary btn-sm" (click)="load()">Actualizar</button></div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando documentos…</div>
      } @else if (!items().length) {
        <app-empty-state symbol="▤" title="Sin documentos" message="Sube actas, reglamentos o estados financieros para consulta de la comunidad." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Documento</th><th>Categoría</th><th>Rol mínimo</th><th>Tamaño</th><th>Publicado</th><th style="width:120px"></th></tr></thead>
            <tbody>
              @for (item of items(); track item.id) {
                <tr>
                  <td><div class="main-cell">{{ item.title }}</div><div class="muted small">{{ item.fileName }} · {{ item.description || 'Sin descripción' }}</div></td>
                  <td>{{ label(item.category) }}</td>
                  <td>{{ label(item.minimumRole) }}</td>
                  <td class="num">{{ item.fileSize | fileSize }}</td>
                  <td>{{ item.createdAt | date:'dd/MM/yyyy HH:mm' }}</td>
                  <td>
                    <div class="table-actions">
                      <button type="button" class="btn-icon" title="Descargar" (click)="download(item)">⤓</button>
                      @if (auth.hasPermission('DOCUMENTS_MANAGE')) {
                        <button type="button" class="btn-icon danger" title="Eliminar" (click)="remove(item)">🗑</button>
                      }
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>

    <app-modal [open]="uploadOpen()" title="Subir documento" subtitle="Los archivos se validan por contenido y quedan disponibles según el rol mínimo." (closed)="uploadOpen.set(false)">
      @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
      <form (ngSubmit)="upload()" novalidate>
        <div class="form-grid">
          <div class="field span-2"><label for="title">Título *</label><input id="title" name="title" class="input" required [(ngModel)]="form.title" maxlength="200"></div>
          <div class="field span-2"><label for="description">Descripción</label><textarea id="description" name="description" class="textarea" [(ngModel)]="form.description" maxlength="500"></textarea></div>
          <div class="field">
            <label for="uploadCategory">Categoría *</label>
            <select id="uploadCategory" name="uploadCategory" class="select" required [(ngModel)]="form.category">
              @for (option of categories; track option) { <option [value]="option">{{ label(option) }}</option> }
            </select>
          </div>
          <div class="field">
            <label for="minimumRole">Visible para</label>
            <select id="minimumRole" name="minimumRole" class="select" [(ngModel)]="form.minimumRole">
              <option value="RESIDENTE">Todos los residentes</option>
              <option value="PROPIETARIO">Propietarios</option>
              <option value="CONSEJO">Consejo de administración</option>
              <option value="ADMINISTRADOR">Solo administración</option>
            </select>
          </div>
          <div class="field span-2">
            <label for="uploadUnit">Inmueble asociado (opcional)</label>
            <select id="uploadUnit" name="uploadUnit" class="select" [(ngModel)]="form.unitId">
              <option value="">Documento general de la copropiedad</option>
              @for (unit of units(); track unit.id) { <option [value]="unit.id">{{ unit.identifier }} — {{ unit.tower || 'Sin torre' }}</option> }
            </select>
          </div>
          <div class="field span-2">
            <label for="file">Archivo *</label>
            <input id="file" name="file" type="file" class="input" accept=".pdf,.png,.jpg,.jpeg,.webp,.doc,.docx,.xls,.xlsx,.pptx,.csv,.txt" (change)="onFile($event)">
            <span class="hint">Máximo 5 MB. Formatos: PDF, imágenes, Office, CSV o texto.</span>
          </div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="uploadOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="uploading()">{{ uploading() ? 'Subiendo…' : 'Subir documento' }}</button>
        </div>
      </form>
    </app-modal>
  `
})
export class DocumentsPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  readonly label = label;
  readonly units = signal<PropertyUnit[]>([]);
  readonly categories: DocumentCategory[] = ['ACTA_ASAMBLEA', 'ESTADO_FINANCIERO', 'REGLAMENTO_PROPIEDAD_HORIZONTAL', 'PRESUPUESTO', 'CONTRATO', 'CIRCULAR', 'OTRO'];
  readonly items = signal<Document[]>([]);
  readonly loading = signal(false);
  readonly uploadOpen = signal(false);
  readonly uploading = signal(false);
  readonly error = signal('');
  category = '';
  unitFilter = '';
  file: File | null = null;
  form = {
    title: '',
    description: '',
    category: 'CIRCULAR' as DocumentCategory,
    minimumRole: 'RESIDENTE',
    unitId: ''
  };

  constructor() {
    this.unitFilter = this.route.snapshot.queryParamMap.get('unitId') ?? '';
    void this.loadUnits();
    void this.load();
  }

  async loadUnits(): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.get<PageResultPropertyUnit>('/properties', { size: 200 }));
      this.units.set(result.content ?? []);
    } catch {
      this.units.set([]);
    }
  }

  async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.items.set(await firstValueFrom(this.api.get<Document[]>('/documents', { category: this.category, unitId: this.unitFilter })));
    } finally {
      this.loading.set(false);
    }
  }

  openUpload(): void {
    this.form = { title: '', description: '', category: 'CIRCULAR', minimumRole: 'RESIDENTE', unitId: this.unitFilter };
    this.file = null;
    this.error.set('');
    this.uploadOpen.set(true);
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file = input.files?.[0] ?? null;
  }

  async upload(): Promise<void> {
    this.error.set('');
    if (!this.form.title.trim() || !this.file) {
      this.error.set('Indica el título y selecciona un archivo.');
      return;
    }
    this.uploading.set(true);
    try {
      await firstValueFrom(this.api.upload<Document>('/documents', this.file, {
        title: this.form.title.trim(),
        description: this.form.description.trim(),
        category: this.form.category,
        minimumRole: this.form.minimumRole,
        unitId: this.form.unitId
      }));
      this.toast.success('Documento publicado.');
      this.uploadOpen.set(false);
      await this.load();
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.uploading.set(false);
    }
  }

  async download(item: Document): Promise<void> {
    if (!item.id) return;
    const response = await firstValueFrom(this.api.download(`/documents/${item.id}/download`, undefined, item.fileName || 'documento'));
    saveBlob(response, item.fileName || 'documento');
  }

  async remove(item: Document): Promise<void> {
    if (!item.id) return;
    const accepted = await this.confirm.confirm({
      title: 'Eliminar documento',
      message: `¿Deseas eliminar "${item.title}"? El archivo dejará de estar disponible para la comunidad.`,
      confirmLabel: 'Eliminar',
      danger: true
    });
    if (!accepted) return;
    await firstValueFrom(this.api.delete<void>(`/documents/${item.id}`));
    this.toast.success('Documento eliminado.');
    await this.load();
  }
}
