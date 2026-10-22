import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ConfirmService } from '../../core/confirm.service';
import { label } from '../../core/labels';
import { MyUnit, Pet, PetRequest } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError, vaccineState } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-my-pets-page',
  standalone: true,
  imports: [FormsModule, PageHeaderComponent, ModalComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Mis mascotas" subtitle="Registra y mantén al día la información de las mascotas de tus inmuebles.">
      @if (auth.hasPermission('PETS_MANAGE_OWN')) {
        <button type="button" class="btn btn-primary" (click)="openForm()">Registrar mascota</button>
      }
    </app-page-header>

    <div class="filter-bar">
      <div class="field">
        <label for="myPetUnit">Inmueble</label>
        <select id="myPetUnit" name="myPetUnit" class="select" [(ngModel)]="unitFilter" (change)="load()">
          <option value="">Todos mis inmuebles</option>
          @for (unit of units(); track unit.unitId) { <option [value]="unit.unitId">{{ unit.identifier }} · {{ unit.tower || 'Sin torre' }}</option> }
        </select>
      </div>
      <button type="button" class="btn btn-secondary" (click)="load()">Actualizar</button>
    </div>

    @if (loading()) {
      <div class="loading"><span class="spinner"></span> Cargando mascotas.</div>
    } @else if (!pets().length) {
      <div class="card"><app-empty-state symbol="🐾" title="Sin mascotas" message="No tienes mascotas registradas para el inmueble seleccionado." /></div>
    } @else {
      <div class="grid grid-2">
        @for (pet of pets(); track pet.id) {
          <div class="card">
            <div class="card-header">
              <div>
                <h2>{{ pet.name }}</h2>
                <p>{{ label(pet.species) }} · {{ pet.breed || 'Sin raza' }}{{ pet.color ? ' · ' + pet.color : '' }}</p>
              </div>
              <div class="row" style="gap:6px">
                <span class="badge" [class]="'badge ' + vaccine(pet.vaccinated, pet.vaccineExpiresOn).tone">{{ vaccine(pet.vaccinated, pet.vaccineExpiresOn).label }}</span>
                <span class="badge" [class]="'badge ' + (pet.status === 'ACTIVE' ? 'success' : 'neutral')">{{ pet.status === 'ACTIVE' ? 'Activa' : 'Inactiva' }}</span>
              </div>
            </div>
            <div class="card-body">
              <dl class="detail-list">
                <dt>Inmueble</dt><dd>{{ unitName(pet.unitId) }}</dd>
                <dt>Nacimiento</dt><dd>{{ pet.birthDate || 'No registrado' }}</dd>
                <dt>Peso</dt><dd>{{ pet.weightKg ? pet.weightKg + ' kg' : 'No registrado' }}</dd>
                <dt>Microchip</dt><dd class="mono">{{ pet.microchip || 'No registrado' }}</dd>
                <dt>Vacunación</dt><dd>{{ pet.vaccinated ? (pet.vaccineExpiresOn ? 'Próxima: ' + pet.vaccineExpiresOn : 'Al día') : 'Sin registrar' }}</dd>
              </dl>
              @if (pet.notes) { <div class="notice" style="margin-top:12px">{{ pet.notes }}</div> }
              @if (auth.hasPermission('PETS_MANAGE_OWN')) {
                <div class="form-actions">
                  <button type="button" class="btn btn-secondary btn-sm" (click)="openForm(pet)">Editar</button>
                  <button type="button" class="btn btn-danger btn-sm" (click)="remove(pet)">Eliminar</button>
                </div>
              }
            </div>
          </div>
        }
      </div>
    }

    @if (error()) { <div class="alert alert-error" style="margin-top:16px">{{ error() }}</div> }

    <app-modal [open]="formOpen()" [title]="editing() ? 'Editar mascota' : 'Registrar mascota'" subtitle="La información ayuda a portería y administración a identificar a tu mascota." (closed)="formOpen.set(false)">
      @if (formError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ formError() }}</div> }
      <form (ngSubmit)="save()" novalidate>
        <div class="form-grid">
          <div class="field">
            <label for="myPetFormUnit">Inmueble *</label>
            <select id="myPetFormUnit" name="myPetFormUnit" class="select" required [(ngModel)]="form.unitId" [disabled]="!!editing()">
              <option value="">Selecciona el inmueble</option>
              @for (unit of units(); track unit.unitId) { <option [value]="unit.unitId">{{ unit.identifier }} · {{ unit.tower || 'Sin torre' }}</option> }
            </select>
          </div>
          <div class="field">
            <label for="myPetName">Nombre *</label>
            <input id="myPetName" name="myPetName" class="input" maxlength="80" required [(ngModel)]="form.name">
          </div>
          <div class="field">
            <label for="myPetSpecies">Especie *</label>
            <select id="myPetSpecies" name="myPetSpecies" class="select" required [(ngModel)]="form.species">
              @for (option of speciesOptions; track option) { <option [value]="option">{{ label(option) }}</option> }
            </select>
          </div>
          <div class="field"><label for="myPetBreed">Raza</label><input id="myPetBreed" name="myPetBreed" class="input" maxlength="80" [(ngModel)]="form.breed"></div>
          <div class="field"><label for="myPetColor">Color</label><input id="myPetColor" name="myPetColor" class="input" maxlength="40" [(ngModel)]="form.color"></div>
          <div class="field"><label for="myPetBirthDate">Fecha de nacimiento</label><input id="myPetBirthDate" name="myPetBirthDate" type="date" class="input" [(ngModel)]="form.birthDate"></div>
          <div class="field"><label for="myPetWeight">Peso (kg)</label><input id="myPetWeight" name="myPetWeight" type="number" min="0.01" max="200" step="0.01" class="input" [(ngModel)]="form.weightKg"></div>
          <div class="field"><label for="myPetMicrochip">Microchip</label><input id="myPetMicrochip" name="myPetMicrochip" class="input mono" maxlength="40" [(ngModel)]="form.microchip"></div>
          <div class="field">
            <label class="check">
              <input type="checkbox" name="myPetVaccinated" [(ngModel)]="form.vaccinated" (change)="onVaccinatedChange()"> Vacunada
            </label>
          </div>
          <div class="field">
            <label for="myPetVaccineExpires">Próxima vacunación</label>
            <input id="myPetVaccineExpires" name="myPetVaccineExpires" type="date" class="input" [disabled]="!form.vaccinated" [(ngModel)]="form.vaccineExpiresOn">
          </div>
          <div class="field" style="grid-column: 1 / -1">
            <label for="myPetNotes">Observaciones</label>
            <textarea id="myPetNotes" name="myPetNotes" class="input" rows="3" maxlength="500" [(ngModel)]="form.notes"></textarea>
          </div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="formOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="saving()">{{ saving() ? 'Guardando…' : (editing() ? 'Guardar cambios' : 'Registrar mascota') }}</button>
        </div>
      </form>
    </app-modal>
  `
})
export class MyPetsPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly vaccine = vaccineState;
  readonly speciesOptions = ['PERRO', 'GATO', 'AVE', 'OTRO'] as const;
  readonly units = signal<MyUnit[]>([]);
  readonly pets = signal<Pet[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly formOpen = signal(false);
  readonly editing = signal<Pet | null>(null);
  readonly error = signal('');
  readonly formError = signal('');
  unitFilter = '';
  form: PetRequest = { name: '' };

  constructor() {
    void this.loadUnits();
    void this.load();
  }

  async loadUnits(): Promise<void> {
    try {
      this.units.set(await firstValueFrom(this.api.get<MyUnit[]>('/my/units')));
    } catch (error) {
      this.error.set(describeError(error));
    }
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      this.pets.set(await firstValueFrom(this.api.get<Pet[]>('/my/pets', { unitId: this.unitFilter })));
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  unitName(id: string | undefined): string {
    return this.units().find((unit) => unit.unitId === id)?.identifier ?? '—';
  }

  openForm(pet?: Pet): void {
    this.editing.set(pet ?? null);
    this.formError.set('');
    this.form = pet ? {
      name: pet.name ?? '',
      species: pet.species ?? 'PERRO',
      breed: pet.breed ?? '',
      color: pet.color ?? '',
      birthDate: pet.birthDate ?? undefined,
      weightKg: pet.weightKg ?? undefined,
      microchip: pet.microchip ?? '',
      vaccinated: pet.vaccinated ?? false,
      vaccineExpiresOn: pet.vaccineExpiresOn ?? undefined,
      notes: pet.notes ?? '',
      unitId: pet.unitId ?? ''
    } : {
      name: '',
      species: 'PERRO',
      unitId: this.unitFilter || (this.units().length === 1 ? this.units()[0].unitId ?? '' : ''),
      vaccinated: false
    };
    this.formOpen.set(true);
  }

  onVaccinatedChange(): void {
    if (!this.form.vaccinated) this.form.vaccineExpiresOn = undefined;
  }

  async save(): Promise<void> {
    this.formError.set('');
    if (!this.form.unitId || !this.form.name.trim() || !this.form.species) {
      this.formError.set('Selecciona el inmueble e indica el nombre y la especie de la mascota.');
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
      notes: this.form.notes?.trim() || undefined
    };
    this.saving.set(true);
    try {
      const current = this.editing();
      if (current?.id) {
        await firstValueFrom(this.api.put<Pet>(`/my/pets/${current.id}`, body));
        this.toast.success('Mascota actualizada.');
      } else {
        await firstValueFrom(this.api.post<Pet>('/my/pets', body));
        this.toast.success('Mascota registrada.');
      }
      this.formOpen.set(false);
      await this.load();
    } catch (error) {
      this.formError.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async remove(pet: Pet): Promise<void> {
    if (!pet.id) return;
    const accepted = await this.confirm.confirm({
      title: 'Eliminar mascota',
      message: `¿Deseas eliminar el registro de ${pet.name || 'esta mascota'}? Esta acción no se puede deshacer.`,
      confirmLabel: 'Eliminar',
      danger: true
    });
    if (!accepted) return;
    try {
      await firstValueFrom(this.api.delete<void>(`/my/pets/${pet.id}`));
      this.toast.success('Mascota eliminada.');
      await this.load();
    } catch (error) {
      this.toast.error(describeError(error));
    }
  }
}
