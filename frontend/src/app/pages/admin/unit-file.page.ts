import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiService, saveBlob } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ConfirmService } from '../../core/confirm.service';
import { label } from '../../core/labels';
import { PageResultPropertyUnit, PropertyUnit, UnitFileNote, UnitFileNoteRequest, UnitFileView } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-unit-file-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, ModalComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Expediente del inmueble" subtitle="Historia, personas, vehículos, mascotas, documentos y notas de cada inmueble.">
      @if (auth.hasPermission('UNIT_FILE_MANAGE')) {
        <button type="button" class="btn btn-secondary" (click)="goToDocuments()">Subir documento del inmueble</button>
      }
    </app-page-header>

    <div class="filter-bar">
      <div class="field" style="min-width:280px">
        <label for="unitFileUnit">Inmueble</label>
        <select id="unitFileUnit" name="unitFileUnit" class="select" [(ngModel)]="selectedUnitId" (change)="loadFile()">
          <option value="">Selecciona un inmueble</option>
          @for (unit of units(); track unit.id) { <option [value]="unit.id">{{ unit.identifier }} — {{ unit.tower || 'Sin torre' }}</option> }
        </select>
      </div>
      <button type="button" class="btn btn-secondary" (click)="loadFile()">Consultar</button>
    </div>

    @if (loading()) {
      <div class="card"><div class="loading"><span class="spinner"></span> Cargando expediente.</div></div>
    } @else if (!view()) {
      <div class="card"><app-empty-state symbol="EX" title="Selecciona un inmueble" message="Elige un inmueble para consultar su expediente." /></div>
    } @else if (view(); as data) {
      <div class="grid grid-3" style="margin-bottom:16px">
        <div class="card"><div class="card-body"><div class="muted">Identificador</div><h2 style="margin:4px 0">{{ data.unit?.identifier }}</h2><div class="muted">{{ label(data.unit?.type) }} · {{ data.unit?.tower || 'Sin torre' }}</div></div></div>
        <div class="card"><div class="card-body"><div class="muted">Coeficiente</div><h2 style="margin:4px 0">{{ data.unit?.coefficient ?? '—' }}</h2><div class="muted">Área {{ data.unit?.areaM2 ?? '—' }} m²</div></div></div>
        <div class="card"><div class="card-body"><div class="muted">Estado</div><h2 style="margin:4px 0">{{ label(data.unit?.status) }}</h2><div class="muted">{{ data.relations?.length ?? 0 }} persona(s) vinculada(s)</div></div></div>
      </div>

      <div class="card" style="margin-bottom:16px">
        <div class="card-header"><div><h2>Personas vinculadas</h2><p>Propietarios, arrendatarios y residentes.</p></div></div>
        @if (!data.relations?.length) {
          <app-empty-state symbol="PE" title="Sin personas" message="No hay relaciones activas para este inmueble." />
        } @else {
          <div class="table-wrap"><table class="table"><thead><tr><th>Persona</th><th>Relación</th><th>Desde</th><th>Hasta</th></tr></thead><tbody>
            @for (relation of data.relations; track relation.id) {
              <tr><td>{{ relation.personName }}</td><td>{{ label(relation.type) }}</td><td>{{ relation.startDate | date:'dd/MM/yyyy' }}</td><td>{{ relation.endDate ? (relation.endDate | date:'dd/MM/yyyy') : 'Vigente' }}</td></tr>
            }
          </tbody></table></div>
        }
      </div>

      <div class="grid grid-2" style="margin-bottom:16px">
        <div class="card">
          <div class="card-header"><div><h2>Vehículos</h2><p>{{ data.vehicles?.length ?? 0 }} registrado(s).</p></div></div>
          @if (!data.vehicles?.length) { <app-empty-state symbol="VE" title="Sin vehículos" message="El inmueble no tiene vehículos registrados." /> }
          @else { <div class="table-wrap"><table class="table"><thead><tr><th>Placa</th><th>Tipo</th><th>Marca / modelo</th></tr></thead><tbody>
            @for (vehicle of data.vehicles; track vehicle.id) { <tr><td class="mono">{{ vehicle.plate || '—' }}</td><td>{{ label(vehicle.typeCode) }}</td><td>{{ vehicle.brand }} {{ vehicle.model }}</td></tr> }
          </tbody></table></div> }
        </div>
        <div class="card">
          <div class="card-header"><div><h2>Mascotas</h2><p>{{ data.pets?.length ?? 0 }} registrada(s).</p></div></div>
          @if (!data.pets?.length) { <app-empty-state symbol="MA" title="Sin mascotas" message="El inmueble no tiene mascotas registradas." /> }
          @else { <div class="table-wrap"><table class="table"><thead><tr><th>Nombre</th><th>Especie</th><th>Vacunación</th></tr></thead><tbody>
            @for (pet of data.pets; track pet.id) { <tr><td>{{ pet.name }}</td><td>{{ label(pet.species) }}</td><td>{{ pet.vaccinated ? 'Al día' : 'Pendiente' }}</td></tr> }
          </tbody></table></div> }
        </div>
      </div>

      <div class="card" style="margin-bottom:16px">
        <div class="card-header"><div><h2>Documentos del inmueble</h2><p>{{ data.documents?.length ?? 0 }} documento(s).</p></div></div>
        @if (!data.documents?.length) {
          <app-empty-state symbol="DO" title="Sin documentos" message="Asocia documentos al inmueble desde la biblioteca de documentos." />
        } @else {
          <div class="table-wrap"><table class="table"><thead><tr><th>Título</th><th>Categoría</th><th>Publicado</th><th style="width:100px"></th></tr></thead><tbody>
            @for (doc of data.documents; track doc.id) {
              <tr><td class="main-cell">{{ doc.title }}</td><td>{{ label(doc.category) }}</td><td>{{ doc.createdAt | date:'dd/MM/yyyy' }}</td><td><button type="button" class="btn btn-secondary btn-sm" (click)="download(doc.id, doc.fileName)">Descargar</button></td></tr>
            }
          </tbody></table></div>
        }
      </div>

      <div class="card">
        <div class="card-header">
          <div><h2>Notas del expediente</h2><p>Las notas internas solo son visibles para la administración.</p></div>
          @if (auth.hasPermission('UNIT_FILE_MANAGE')) { <button type="button" class="btn btn-primary btn-sm" (click)="openNoteCreate()">Agregar nota</button> }
        </div>
        @if (!data.notes?.length) {
          <app-empty-state symbol="NO" title="Sin notas" message="Registra observaciones, actas de entrega o seguimientos del inmueble." />
        } @else {
          <div class="card-body" style="display:grid;gap:12px">
            @for (note of data.notes; track note.id) {
              <div style="border:1px solid var(--line);border-radius:10px;padding:14px">
                <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:12px">
                  <div><strong>{{ note.title }}</strong> @if (note.internal) { <span class="badge neutral" style="margin-left:8px">Interna</span> }</div>
                  <div class="table-actions">
                    @if (auth.hasPermission('UNIT_FILE_MANAGE')) {
                      <button type="button" class="btn btn-secondary btn-sm" (click)="openNoteEdit(note)">Editar</button>
                      <button type="button" class="btn btn-danger btn-sm" (click)="removeNote(note)">Eliminar</button>
                    }
                  </div>
                </div>
                <p style="margin:8px 0 0;white-space:pre-wrap">{{ note.body }}</p>
                <div class="muted" style="margin-top:8px">{{ note.createdAt | date:'dd/MM/yyyy HH:mm' }}</div>
              </div>
            }
          </div>
        }
      </div>
    }

    <app-modal [open]="noteModalOpen()" [title]="editingNote() ? 'Editar nota' : 'Agregar nota'" subtitle="Documenta la historia y los acuerdos del inmueble." (closed)="noteModalOpen.set(false)">
      @if (noteError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ noteError() }}</div> }
      <form (ngSubmit)="saveNote()" novalidate>
        <div class="form-grid">
          <div class="field span-2"><label for="noteTitle">Título *</label><input id="noteTitle" name="noteTitle" class="input" required maxlength="160" [(ngModel)]="noteForm.title"></div>
          <div class="field span-2"><label for="noteBody">Nota *</label><textarea id="noteBody" name="noteBody" class="textarea" rows="5" required maxlength="2000" [(ngModel)]="noteForm.body"></textarea></div>
          <div class="field span-2"><label class="check"><input type="checkbox" name="noteInternal" [(ngModel)]="noteForm.internal"> Nota interna (no visible para residentes)</label></div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="noteModalOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="noteSaving()">{{ noteSaving() ? 'Guardando…' : 'Guardar nota' }}</button>
        </div>
      </form>
    </app-modal>
  `
})
export class UnitFilePage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  readonly label = label;
  readonly units = signal<PropertyUnit[]>([]);
  readonly view = signal<UnitFileView | null>(null);
  readonly loading = signal(false);
  readonly noteModalOpen = signal(false);
  readonly noteSaving = signal(false);
  readonly editingNote = signal<UnitFileNote | null>(null);
  readonly noteError = signal('');
  selectedUnitId = '';
  noteForm: UnitFileNoteRequest = { title: '', body: '', internal: true };

  constructor() {
    void this.loadUnits();
  }

  async loadUnits(): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.get<PageResultPropertyUnit>('/properties', { size: 200 }));
      this.units.set(result.content ?? []);
      this.selectedUnitId = this.units()[0]?.id ?? '';
      if (this.selectedUnitId) await this.loadFile();
    } catch (error) {
      this.toast.error(describeError(error));
    }
  }

  async loadFile(): Promise<void> {
    if (!this.selectedUnitId) {
      this.view.set(null);
      return;
    }
    this.loading.set(true);
    try {
      this.view.set(await firstValueFrom(this.api.get<UnitFileView>(`/properties/${this.selectedUnitId}/file`)));
    } catch (error) {
      this.view.set(null);
      this.toast.error(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  goToDocuments(): void {
    void this.router.navigate(['/app/documentos'], { queryParams: { unitId: this.selectedUnitId || undefined } });
  }

  async download(id?: string, fileName?: string): Promise<void> {
    if (!id) return;
    try {
      const response = await firstValueFrom(this.api.download(`/documents/${id}/download`, undefined, fileName || 'documento'));
      saveBlob(response, fileName || 'documento');
    } catch (error) {
      this.toast.error(describeError(error));
    }
  }

  openNoteCreate(): void {
    this.editingNote.set(null);
    this.noteError.set('');
    this.noteForm = { title: '', body: '', internal: true };
    this.noteModalOpen.set(true);
  }

  openNoteEdit(note: UnitFileNote): void {
    this.editingNote.set(note);
    this.noteError.set('');
    this.noteForm = { title: note.title ?? '', body: note.body ?? '', internal: note.internal ?? true };
    this.noteModalOpen.set(true);
  }

  async saveNote(): Promise<void> {
    this.noteError.set('');
    if (this.noteForm.title.trim().length < 3 || this.noteForm.body.trim().length < 5) {
      this.noteError.set('El título debe tener al menos 3 caracteres y la nota al menos 5.');
      return;
    }
    this.noteSaving.set(true);
    try {
      const editing = this.editingNote();
      if (editing?.id) {
        await firstValueFrom(this.api.put<UnitFileNote>(`/properties/${this.selectedUnitId}/notes/${editing.id}`, this.noteForm));
        this.toast.success('Nota actualizada.');
      } else {
        await firstValueFrom(this.api.post<UnitFileNote>(`/properties/${this.selectedUnitId}/notes`, this.noteForm));
        this.toast.success('Nota agregada.');
      }
      this.noteModalOpen.set(false);
      await this.loadFile();
    } catch (error) {
      this.noteError.set(describeError(error));
    } finally {
      this.noteSaving.set(false);
    }
  }

  async removeNote(note: UnitFileNote): Promise<void> {
    if (!note.id) return;
    const ok = await this.confirm.confirm({
      title: 'Eliminar nota',
      message: `¿Eliminar la nota “${note.title}”?`,
      confirmLabel: 'Eliminar',
      danger: true
    });
    if (!ok) return;
    try {
      await firstValueFrom(this.api.delete<void>(`/properties/${this.selectedUnitId}/notes/${note.id}`));
      this.toast.success('Nota eliminada.');
      await this.loadFile();
    } catch (error) {
      this.toast.error(describeError(error));
    }
  }
}
