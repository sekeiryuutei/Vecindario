import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ConfirmService } from '../../core/confirm.service';
import { label } from '../../core/labels';
import { PageResultPet, PageResultPropertyUnit, Pet, PetRequest, PropertyUnit } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError, vaccineState } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-pets-page',
  standalone: true,
  imports: [FormsModule, PageHeaderComponent, ModalComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Mascotas" subtitle="Registro de mascotas por inmueble, vacunación y estado dentro de la copropiedad.">
      @if (auth.hasPermission('PETS_CREATE')) {
        <button type="button" class="btn btn-primary" (click)="openCreate()">Registrar mascota</button>
      }
    </app-page-header>

    <div class="filter-bar">
      <div class="field">
        <label for="petSearch">Buscar</label>
        <input id="petSearch" name="petSearch" class="input" [(ngModel)]="q" (keyup.enter)="load(0)" placeholder="Nombre o microchip">
      </div>
      <div class="field">
        <label for="petUnit">Inmueble</label>
        <select id="petUnit" name="petUnit" class="select" [(ngModel)]="unitFilter" (change)="load(0)">
          <option value="">Todos</option>
          @for (unit of units(); track unit.id) { <option [value]="unit.id">{{ unit.identifier }} · {{ unit.tower || 'Sin torre' }}</option> }
        </select>
      </div>
      <div class="field">
        <label for="petSpecies">Especie</label>
        <select id="petSpecies" name="petSpecies" class="select" [(ngModel)]="speciesFilter" (change)="load(0)">
          <option value="">Todas</option>
          @for (option of speciesOptions; track option) { <option [value]="option">{{ label(option) }}</option> }
        </select>
      </div>
      <div class="field">
        <label for="petStatus">Estado</label>
        <select id="petStatus" name="petStatus" class="select" [(ngModel)]="statusFilter" (change)="load(0)">
          <option value="">Todos</option>
          <option value="ACTIVE">Activas</option>
          <option value="INACTIVE">Inactivas</option>
        </select>
      </div>
      <button type="button" class="btn btn-secondary" (click)="load(0)">Filtrar</button>
      <button type="button" class="btn btn-ghost" (click)="clearFilters()">Limpiar</button>
    </div>

    <div class="card">
      <div class="card-header">
        <div><h2>Mascotas registradas</h2><p>{{ totalElements() }} mascota(s).</p></div>
        <button type="button" class="btn btn-secondary btn-sm" (click)="load(page())">Actualizar</button>
      </div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando mascotas.</div>
      } @else if (!items().length) {
        <app-empty-state symbol="🐾" title="Sin mascotas" message="No hay mascotas que coincidan con los filtros." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr><th>Mascota</th><th>Especie</th><th>Inmueble</th><th>Vacunación</th><th>Microchip</th><th>Estado</th><th style="width:170px"></th></tr>
            </thead>
            <tbody>
              @for (item of items(); track item.id) {
                <tr>
                  <td class="main-cell">
                    <div>{{ item.name }}</div>
                    <div class="muted">{{ item.breed || 'Sin raza' }}{{ item.color ? ' · ' + item.color : '' }}</div>
                  </td>
                  <td>{{ label(item.species) }}</td>
                  <td>{{ item.unitIdentifier || '—' }}</td>
                  <td>
                    <span class="badge" [class]="'badge ' + vaccine(item.vaccinated, item.vaccineExpiresOn).tone">
                      {{ vaccine(item.vaccinated, item.vaccineExpiresOn).label }}
                    </span>
                    @if (item.vaccineExpiresOn) { <div class="muted">{{ item.vaccineExpiresOn }}</div> }
                  </td>
                  <td class="mono">{{ item.microchip || '—' }}</td>
                  <td><span class="badge" [class]="'badge ' + (item.status === 'ACTIVE' ? 'success' : 'neutral')">{{ item.status === 'ACTIVE' ? 'Activa' : 'Inactiva' }}</span></td>
                  <td>
                    <div class="table-actions">
                      @if (auth.hasPermission('PETS_UPDATE')) { <button type="button" class="btn btn-secondary btn-sm" (click)="openEdit(item)">Editar</button> }
                      @if (auth.hasPermission('PETS_DELETE')) { <button type="button" class="btn btn-danger btn-sm" (click)="remove(item)">Eliminar</button> }
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

    <app-modal [open]="modalOpen()" [title]="editing() ? 'Editar mascota' : 'Registrar mascota'" subtitle="La mascota queda asociada a un inmueble y a su historial de vacunación." (closed)="modalOpen.set(false)">
      @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
      <form (ngSubmit)="save()" novalidate>
        <div class="form-grid">
          <div class="field">
            <label for="petFormUnit">Inmueble *</label>
            <select id="petFormUnit" name="petFormUnit" class="select" required [(ngModel)]="form.unitId">
              <option value="">Selecciona el inmueble</option>
              @for (unit of units(); track unit.id) { <option [value]="unit.id">{{ unit.identifier }} · {{ unit.tower || 'Sin torre' }}</option> }
            </select>
          </div>
          <div class="field">
            <label for="petName">Nombre *</label>
            <input id="petName" name="petName" class="input" maxlength="80" required [(ngModel)]="form.name">
          </div>
          <div class="field">
            <label for="petFormSpecies">Especie *</label>
            <select id="petFormSpecies" name="petFormSpecies" class="select" required [(ngModel)]="form.species">
              @for (option of speciesOptions; track option) { <option [value]="option">{{ label(option) }}</option> }
            </select>
          </div>
          <div class="field"><label for="petBreed">Raza</label><input id="petBreed" name="petBreed" class="input" maxlength="80" [(ngModel)]="form.breed"></div>
          <div class="field"><label for="petColor">Color</label><input id="petColor" name="petColor" class="input" maxlength="40" [(ngModel)]="form.color"></div>
          <div class="field"><label for="petBirthDate">Fecha de nacimiento</label><input id="petBirthDate" name="petBirthDate" type="date" class="input" [(ngModel)]="form.birthDate"></div>
          <div class="field"><label for="petWeight">Peso (kg)</label><input id="petWeight" name="petWeight" type="number" min="0.01" max="200" step="0.01" class="input" [(ngModel)]="form.weightKg"></div>
          <div class="field"><label for="petMicrochip">Microchip</label><input id="petMicrochip" name="petMicrochip" class="input mono" maxlength="40" [(ngModel)]="form.microchip"></div>
          <div class="field">
            <label class="check">
              <input type="checkbox" name="petVaccinated" [(ngModel)]="form.vaccinated" (change)="onVaccinatedChange()"> Vacunada
            </label>
          </div>
          <div class="field">
            <label for="petVaccineExpires">Próxima vacunación</label>
            <input id="petVaccineExpires" name="petVaccineExpires" type="date" class="input" [disabled]="!form.vaccinated" [(ngModel)]="form.vaccineExpiresOn">
            <span class="hint">Si no hay fecha, se considera al día con el esquema registrado.</span>
          </div>
          <div class="field">
            <label for="petFormStatus">Estado</label>
            <select id="petFormStatus" name="petFormStatus" class="select" [(ngModel)]="form.status">
              <option value="ACTIVE">Activa</option>
              <option value="INACTIVE">Inactiva</option>
            </select>
          </div>
          <div class="field" style="grid-column: 1 / -1">
            <label for="petNotes">Observaciones</label>
            <textarea id="petNotes" name="petNotes" class="input" rows="3" maxlength="500" [(ngModel)]="form.notes"></textarea>
          </div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="modalOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="saving()">{{ saving() ? 'Guardando…' : (editing() ? 'Guardar cambios' : 'Registrar mascota') }}</button>
        </div>
      </form>
    </app-modal>
  `
})
export class PetsPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly vaccine = vaccineState;
  readonly speciesOptions = ['PERRO', 'GATO', 'AVE', 'OTRO'] as const;
  readonly items = signal<Pet[]>([]);
  readonly units = signal<PropertyUnit[]>([]);
  readonly loading = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly modalOpen = signal(false);
  readonly editing = signal<Pet | null>(null);
  readonly saving = signal(false);
  readonly error = signal('');
  q = '';
  unitFilter = '';
  speciesFilter = '';
  statusFilter = '';
  form: PetRequest = { name: '' };

  constructor() {
    void this.load();
    void this.loadUnits();
  }

  async load(page = 0): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      const result = await firstValueFrom(this.api.get<PageResultPet>('/pets', {
        q: this.q.trim(),
        unitId: this.unitFilter,
        species: this.speciesFilter,
        status: this.statusFilter,
        page,
        size: 20
      }));
      this.items.set(result.content ?? []);
      this.page.set(result.page ?? 0);
      this.totalPages.set(result.totalPages ?? 0);
      this.totalElements.set(result.totalElements ?? 0);
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  async loadUnits(): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.get<PageResultPropertyUnit>('/properties', { size: 200 }));
      this.units.set(result.content ?? []);
    } catch {
      this.units.set([]);
    }
  }

  clearFilters(): void {
    this.q = '';
    this.unitFilter = '';
    this.speciesFilter = '';
    this.statusFilter = '';
    void this.load(0);
  }

  openCreate(): void {
    this.editing.set(null);
    this.error.set('');
    this.form = {
      name: '',
      species: 'PERRO',
      unitId: this.units()[0]?.id ?? '',
      vaccinated: false,
      status: 'ACTIVE'
    };
    this.modalOpen.set(true);
  }

  openEdit(item: Pet): void {
    this.editing.set(item);
    this.error.set('');
    this.form = {
      name: item.name ?? '',
      species: item.species ?? 'PERRO',
      breed: item.breed ?? '',
      color: item.color ?? '',
      birthDate: item.birthDate ?? undefined,
      weightKg: item.weightKg ?? undefined,
      microchip: item.microchip ?? '',
      vaccinated: item.vaccinated ?? false,
      vaccineExpiresOn: item.vaccineExpiresOn ?? undefined,
      notes: item.notes ?? '',
      status: item.status ?? 'ACTIVE',
      unitId: item.unitId ?? ''
    };
    this.modalOpen.set(true);
  }

  onVaccinatedChange(): void {
    if (!this.form.vaccinated) this.form.vaccineExpiresOn = undefined;
  }

  async save(): Promise<void> {
    this.error.set('');
    if (!this.form.name.trim() || !this.form.species || !this.form.unitId) {
      this.error.set('Indica el inmueble, el nombre y la especie de la mascota.');
      return;
    }
    const body: PetRequest = {
      name: this.form.name.trim(),
      species: this.form.species,
      unitId: this.form.unitId,
      breed: this.form.breed?.trim() || undefined,
      color: this.form.color?.trim() || undefined,
      birthDate: this.form.birthDate || undefined,
      weightKg: this.form.weightKg ? Number(this.form.weightKg) : undefined,
      microchip: this.form.microchip?.trim() || undefined,
      vaccinated: !!this.form.vaccinated,
      vaccineExpiresOn: this.form.vaccinated ? (this.form.vaccineExpiresOn || undefined) : undefined,
      notes: this.form.notes?.trim() || undefined,
      status: this.form.status ?? 'ACTIVE'
    };
    this.saving.set(true);
    try {
      const current = this.editing();
      if (current?.id) {
        await firstValueFrom(this.api.put<Pet>(`/pets/${current.id}`, body));
        this.toast.success('Mascota actualizada.');
      } else {
        await firstValueFrom(this.api.post<Pet>('/pets', body));
        this.toast.success('Mascota registrada.');
      }
      this.modalOpen.set(false);
      await this.load(this.page());
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async remove(item: Pet): Promise<void> {
    if (!item.id) return;
    const accepted = await this.confirm.confirm({
      title: 'Eliminar mascota',
      message: `¿Deseas eliminar el registro de ${item.name || 'esta mascota'}? El historial se conserva.`,
      confirmLabel: 'Eliminar',
      danger: true
    });
    if (!accepted) return;
    try {
      await firstValueFrom(this.api.delete<void>(`/pets/${item.id}`));
      this.toast.success('Mascota eliminada.');
      await this.load(this.page());
    } catch (error) {
      this.toast.error(describeError(error));
    }
  }
}
