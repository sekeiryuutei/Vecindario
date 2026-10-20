import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ConfirmService } from '../../core/confirm.service';
import { label, statusTone } from '../../core/labels';
import { MyUnit, Vehicle, VehicleRequest, VehicleType } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-my-vehicles-page',
  standalone: true,
  imports: [FormsModule, PageHeaderComponent, ModalComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Mis vehículos" subtitle="Consulta y administra los vehículos registrados en tus inmuebles.">
      @if (auth.hasPermission('VEHICLES_MANAGE_OWN')) {
        <button type="button" class="btn btn-primary" (click)="openForm()">Registrar vehículo</button>
      }
    </app-page-header>

    <div class="filter-bar">
      <div class="field">
        <label for="unitFilter">Inmueble</label>
        <select id="unitFilter" name="unitFilter" class="select" [(ngModel)]="unitFilter" (change)="load()">
          <option value="">Todos mis inmuebles</option>
          @for (unit of units(); track unit.unitId) { <option [value]="unit.unitId">{{ unit.identifier }} · {{ unit.tower || 'Sin torre' }}</option> }
        </select>
      </div>
      <button type="button" class="btn btn-secondary" (click)="load()">Actualizar</button>
    </div>

    @if (loading()) {
      <div class="loading"><span class="spinner"></span> Cargando vehículos…</div>
    } @else if (!vehicles().length) {
      <div class="card"><app-empty-state symbol="▣" title="Sin vehículos" message="No tienes vehículos registrados para el inmueble seleccionado." /></div>
    } @else {
      <div class="grid grid-2">
        @for (vehicle of vehicles(); track vehicle.id) {
          <div class="card">
            <div class="card-header">
              <div><h2 class="mono">{{ vehicle.plate || 'Sin placa' }}</h2><p>{{ label(vehicle.typeCode) }} · {{ vehicle.brand || 'Sin marca' }} {{ vehicle.model || '' }}</p></div>
              <div class="row" style="gap:6px">
                <span class="badge" [class]="'badge ' + (vehicle.status === 'AUTHORIZED' ? 'success' : 'danger')">{{ label(vehicle.status) }}</span>
                <span class="badge" [class]="'badge ' + (vehicle.presence === 'INSIDE' ? 'info' : 'neutral')">{{ label(vehicle.presence) }}</span>
              </div>
            </div>
            <div class="card-body">
              <dl class="detail-list">
                <dt>Inmueble</dt><dd>{{ unitName(vehicle.unitId) }}</dd>
                <dt>Color</dt><dd>{{ vehicle.color || 'No registrado' }}</dd>
                <dt>Modelo</dt><dd>{{ vehicle.modelYear || 'No registrado' }}</dd>
                <dt>Titular</dt><dd>{{ vehicle.primary ? 'Vehículo principal' : 'Vehículo asociado' }}</dd>
              </dl>
              @if (auth.hasPermission('VEHICLES_MANAGE_OWN')) {
                <div class="form-actions">
                  <button type="button" class="btn btn-secondary btn-sm" (click)="openForm(vehicle)">Editar</button>
                  <button type="button" class="btn btn-danger btn-sm" (click)="remove(vehicle)">Eliminar</button>
                </div>
              }
              @if (vehicle.presence === 'INSIDE') {
                <div class="notice" style="margin-top:14px">El vehículo está dentro de la copropiedad. Registra su salida en portería antes de modificarlo o eliminarlo.</div>
              }
            </div>
          </div>
        }
      </div>
    }

    @if (error()) { <div class="alert alert-error" style="margin-top:16px">{{ error() }}</div> }

    <app-modal [open]="formOpen()" [title]="editing() ? 'Editar vehículo' : 'Registrar vehículo'" subtitle="El parqueadero y la autorización los define la administración." (closed)="formOpen.set(false)">
      @if (formError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ formError() }}</div> }
      <form (ngSubmit)="save()" novalidate>
        <div class="form-grid">
          <div class="field">
            <label for="vehicleUnit">Inmueble *</label>
            <select id="vehicleUnit" name="vehicleUnit" class="select" required [(ngModel)]="form.unitId" [disabled]="!!editing()">
              <option value="">Selecciona el inmueble</option>
              @for (unit of units(); track unit.unitId) { <option [value]="unit.unitId">{{ unit.identifier }} · {{ unit.tower || 'Sin torre' }}</option> }
            </select>
          </div>
          <div class="field">
            <label for="typeCode">Tipo de vehículo *</label>
            <select id="typeCode" name="typeCode" class="select" required [(ngModel)]="form.typeCode">
              <option value="">Selecciona el tipo</option>
              @for (type of types(); track type.code) { <option [value]="type.code">{{ type.name }}</option> }
            </select>
          </div>
          <div class="field"><label for="plate">Placa {{ requiresPlate() ? '*' : '' }}</label><input id="plate" name="plate" class="input mono" maxlength="12" [(ngModel)]="form.plate" style="text-transform:uppercase" [required]="requiresPlate()"></div>
          <div class="field"><label for="brand">Marca</label><input id="brand" name="brand" class="input" maxlength="60" [(ngModel)]="form.brand"></div>
          <div class="field"><label for="model">Modelo / línea</label><input id="model" name="model" class="input" maxlength="60" [(ngModel)]="form.model"></div>
          <div class="field"><label for="color">Color</label><input id="color" name="color" class="input" maxlength="40" [(ngModel)]="form.color"></div>
          <div class="field"><label for="modelYear">Año</label><input id="modelYear" name="modelYear" type="number" min="1900" max="2100" class="input" [(ngModel)]="form.modelYear"></div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="formOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="saving()">{{ saving() ? 'Guardando…' : (editing() ? 'Guardar cambios' : 'Registrar vehículo') }}</button>
        </div>
      </form>
    </app-modal>
  `
})
export class MyVehiclesPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly units = signal<MyUnit[]>([]);
  readonly vehicles = signal<Vehicle[]>([]);
  readonly types = signal<VehicleType[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly formOpen = signal(false);
  readonly editing = signal<Vehicle | null>(null);
  readonly error = signal('');
  readonly formError = signal('');
  unitFilter = '';
  form: Required<Pick<VehicleRequest, 'typeCode' | 'unitId'>> & VehicleRequest = this.emptyForm();

  constructor() {
    void this.loadUnits();
    void this.loadTypes();
    void this.load();
  }

  requiresPlate(): boolean {
    return this.types().find((type) => type.code === this.form.typeCode)?.requiresPlate ?? true;
  }

  async loadUnits(): Promise<void> {
    try {
      this.units.set(await firstValueFrom(this.api.get<MyUnit[]>('/my/units')));
    } catch (error) {
      this.error.set(describeError(error));
    }
  }

  async loadTypes(): Promise<void> {
    try {
      this.types.set(await firstValueFrom(this.api.get<VehicleType[]>('/vehicle-types')));
    } catch {
      this.types.set([]);
    }
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      this.vehicles.set(await firstValueFrom(this.api.get<Vehicle[]>('/my/vehicles', { unitId: this.unitFilter })));
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  unitName(id: string | undefined): string {
    return this.units().find((unit) => unit.unitId === id)?.identifier ?? '—';
  }

  openForm(vehicle?: Vehicle): void {
    this.editing.set(vehicle ?? null);
    this.formError.set('');
    this.form = vehicle ? {
      unitId: vehicle.unitId ?? '',
      typeCode: vehicle.typeCode ?? '',
      plate: vehicle.plate ?? '',
      brand: vehicle.brand ?? '',
      model: vehicle.model ?? '',
      color: vehicle.color ?? '',
      modelYear: vehicle.modelYear ?? undefined
    } : this.emptyForm();
    if (!this.form.unitId && this.units().length === 1) this.form.unitId = this.units()[0].unitId ?? '';
    this.formOpen.set(true);
  }

  async save(): Promise<void> {
    this.formError.set('');
    if (!this.form.unitId || !this.form.typeCode) {
      this.formError.set('Selecciona el inmueble y el tipo de vehículo.');
      return;
    }
    if (this.requiresPlate() && !this.form.plate?.trim()) {
      this.formError.set('La placa es obligatoria para este tipo de vehículo.');
      return;
    }
    const body: VehicleRequest = {
      typeCode: this.form.typeCode,
      unitId: this.form.unitId,
      plate: this.form.plate?.trim().toUpperCase() || undefined,
      brand: this.form.brand?.trim() || undefined,
      model: this.form.model?.trim() || undefined,
      color: this.form.color?.trim() || undefined,
      modelYear: this.form.modelYear ? Number(this.form.modelYear) : undefined
    };
    this.saving.set(true);
    try {
      const current = this.editing();
      if (current?.id) {
        await firstValueFrom(this.api.put<Vehicle>(`/my/vehicles/${current.id}`, body));
        this.toast.success('Vehículo actualizado.');
      } else {
        await firstValueFrom(this.api.post<Vehicle>('/my/vehicles', body));
        this.toast.success('Vehículo registrado.');
      }
      this.formOpen.set(false);
      await this.load();
    } catch (error) {
      this.formError.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async remove(vehicle: Vehicle): Promise<void> {
    if (!vehicle.id) return;
    const accepted = await this.confirm.confirm({
      title: 'Eliminar vehículo',
      message: `¿Deseas eliminar el vehículo con placa ${vehicle.plate || 'sin placa'}? Esta acción no se puede deshacer.`,
      confirmLabel: 'Eliminar',
      danger: true
    });
    if (!accepted) return;
    try {
      await firstValueFrom(this.api.delete<void>(`/my/vehicles/${vehicle.id}`));
      this.toast.success('Vehículo eliminado.');
      await this.load();
    } catch (error) {
      this.toast.error(describeError(error));
    }
  }

  private emptyForm(): Required<Pick<VehicleRequest, 'typeCode' | 'unitId'>> & VehicleRequest {
    return { typeCode: '', unitId: '', plate: '', brand: '', model: '', color: '', modelYear: undefined };
  }
}
