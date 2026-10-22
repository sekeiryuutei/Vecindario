import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService, saveBlob } from '../../core/api.service';
import { label } from '../../core/labels';
import { MyUnit, UnitFileView } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, PageHeaderComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-my-unit-file-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Expediente de mi inmueble" subtitle="Consulta la información, documentos y notas públicas de tus inmuebles." />

    <div class="filter-bar">
      <div class="field" style="min-width:280px">
        <label for="myUnitFileUnit">Inmueble</label>
        <select id="myUnitFileUnit" name="myUnitFileUnit" class="select" [(ngModel)]="selectedUnitId" (change)="loadFile()">
          <option value="">Selecciona un inmueble</option>
          @for (unit of units(); track unit.unitId) { <option [value]="unit.unitId">{{ unit.identifier }} — {{ unit.tower || 'Sin torre' }}</option> }
        </select>
      </div>
      <button type="button" class="btn btn-secondary" (click)="loadFile()">Consultar</button>
    </div>

    @if (loading()) {
      <div class="card"><div class="loading"><span class="spinner"></span> Cargando expediente.</div></div>
    } @else if (!view()) {
      <div class="card"><app-empty-state symbol="EX" title="Selecciona un inmueble" message="Elige uno de tus inmuebles para consultar su expediente." /></div>
    } @else if (view(); as data) {
      <div class="grid grid-3" style="margin-bottom:16px">
        <div class="card"><div class="card-body"><div class="muted">Identificador</div><h2 style="margin:4px 0">{{ data.unit?.identifier }}</h2><div class="muted">{{ label(data.unit?.type) }} · {{ data.unit?.tower || 'Sin torre' }}</div></div></div>
        <div class="card"><div class="card-body"><div class="muted">Coeficiente</div><h2 style="margin:4px 0">{{ data.unit?.coefficient ?? '—' }}</h2><div class="muted">Área {{ data.unit?.areaM2 ?? '—' }} m²</div></div></div>
        <div class="card"><div class="card-body"><div class="muted">Personas vinculadas</div><h2 style="margin:4px 0">{{ data.relations?.length ?? 0 }}</h2><div class="muted">Propietarios, arrendatarios y residentes</div></div></div>
      </div>

      <div class="card" style="margin-bottom:16px">
        <div class="card-header"><div><h2>Personas vinculadas</h2><p>Relaciones activas del inmueble.</p></div></div>
        @if (!data.relations?.length) { <app-empty-state symbol="PE" title="Sin personas" message="No hay relaciones activas." /> }
        @else { <div class="table-wrap"><table class="table"><thead><tr><th>Persona</th><th>Relación</th><th>Desde</th><th>Hasta</th></tr></thead><tbody>
          @for (relation of data.relations; track relation.id) { <tr><td>{{ relation.personName }}</td><td>{{ label(relation.type) }}</td><td>{{ relation.startDate | date:'dd/MM/yyyy' }}</td><td>{{ relation.endDate ? (relation.endDate | date:'dd/MM/yyyy') : 'Vigente' }}</td></tr> }
        </tbody></table></div> }
      </div>

      <div class="grid grid-2" style="margin-bottom:16px">
        <div class="card">
          <div class="card-header"><div><h2>Vehículos</h2><p>{{ data.vehicles?.length ?? 0 }} registrado(s).</p></div></div>
          @if (!data.vehicles?.length) { <app-empty-state symbol="VE" title="Sin vehículos" message="No hay vehículos registrados." /> }
          @else { <div class="table-wrap"><table class="table"><thead><tr><th>Placa</th><th>Tipo</th><th>Marca / modelo</th></tr></thead><tbody>
            @for (vehicle of data.vehicles; track vehicle.id) { <tr><td class="mono">{{ vehicle.plate || '—' }}</td><td>{{ label(vehicle.typeCode) }}</td><td>{{ vehicle.brand }} {{ vehicle.model }}</td></tr> }
          </tbody></table></div> }
        </div>
        <div class="card">
          <div class="card-header"><div><h2>Mascotas</h2><p>{{ data.pets?.length ?? 0 }} registrada(s).</p></div></div>
          @if (!data.pets?.length) { <app-empty-state symbol="MA" title="Sin mascotas" message="No hay mascotas registradas." /> }
          @else { <div class="table-wrap"><table class="table"><thead><tr><th>Nombre</th><th>Especie</th><th>Vacunación</th></tr></thead><tbody>
            @for (pet of data.pets; track pet.id) { <tr><td>{{ pet.name }}</td><td>{{ label(pet.species) }}</td><td>{{ pet.vaccinated ? 'Al día' : 'Pendiente' }}</td></tr> }
          </tbody></table></div> }
        </div>
      </div>

      <div class="card" style="margin-bottom:16px">
        <div class="card-header"><div><h2>Documentos</h2><p>Documentos asociados al inmueble y disponibles para tu rol.</p></div></div>
        @if (!data.documents?.length) { <app-empty-state symbol="DO" title="Sin documentos" message="No hay documentos publicados para este inmueble." /> }
        @else { <div class="table-wrap"><table class="table"><thead><tr><th>Título</th><th>Categoría</th><th>Publicado</th><th style="width:100px"></th></tr></thead><tbody>
          @for (doc of data.documents; track doc.id) { <tr><td class="main-cell">{{ doc.title }}</td><td>{{ label(doc.category) }}</td><td>{{ doc.createdAt | date:'dd/MM/yyyy' }}</td><td><button type="button" class="btn btn-secondary btn-sm" (click)="download(doc.id, doc.fileName)">Descargar</button></td></tr> }
        </tbody></table></div> }
      </div>

      <div class="card">
        <div class="card-header"><div><h2>Notas públicas</h2><p>Información compartida por la administración.</p></div></div>
        @if (!data.notes?.length) { <app-empty-state symbol="NO" title="Sin notas" message="No hay notas públicas para este inmueble." /> }
        @else { <div class="card-body" style="display:grid;gap:12px">
          @for (note of data.notes; track note.id) {
            <div style="border:1px solid var(--line);border-radius:10px;padding:14px">
              <strong>{{ note.title }}</strong>
              <p style="margin:8px 0 0;white-space:pre-wrap">{{ note.body }}</p>
              <div class="muted" style="margin-top:8px">{{ note.createdAt | date:'dd/MM/yyyy HH:mm' }}</div>
            </div>
          }
        </div> }
      </div>
    }
  `
})
export class MyUnitFilePage {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly units = signal<MyUnit[]>([]);
  readonly view = signal<UnitFileView | null>(null);
  readonly loading = signal(false);
  selectedUnitId = '';

  constructor() { void this.loadUnits(); }

  async loadUnits(): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.get<MyUnit[]>('/my/units'));
      this.units.set(result);
      this.selectedUnitId = result[0]?.unitId ?? '';
      if (this.selectedUnitId) await this.loadFile();
    } catch (error) {
      this.toast.error(describeError(error));
    }
  }

  async loadFile(): Promise<void> {
    if (!this.selectedUnitId) { this.view.set(null); return; }
    this.loading.set(true);
    try {
      this.view.set(await firstValueFrom(this.api.get<UnitFileView>(`/my/units/${this.selectedUnitId}/file`)));
    } catch (error) {
      this.view.set(null);
      this.toast.error(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  async download(id?: string, fileName?: string): Promise<void> {
    if (!id) return;
    try {
      const response = await firstValueFrom(this.api.download(`/my/documents/${id}/download`, undefined, fileName || 'documento'));
      saveBlob(response, fileName || 'documento');
    } catch (error) {
      this.toast.error(describeError(error));
    }
  }
}
