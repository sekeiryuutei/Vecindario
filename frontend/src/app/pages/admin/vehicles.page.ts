import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ConfirmService } from '../../core/confirm.service';
import { label, statusTone } from '../../core/labels';
import {
  PageResultPerson,
  PageResultPropertyUnit,
  PageResultVehicle,
  ParkingSpace,
  Person,
  PropertyUnit,
  Vehicle,
  VehicleRequest,
  VehicleType
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-vehicles-page',
  standalone: true,
  imports: [FormsModule, PageHeaderComponent, ModalComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Vehículos" subtitle="Vehículos autorizados, propietarios, parqueaderos y presencia en la copropiedad.">
      @if (auth.hasPermission('VEHICLES_CREATE')) { <button type="button" class="btn btn-primary" (click)="openCreate()">Nuevo vehículo</button> }
    </app-page-header>

    <div class="filter-bar">
      <div class="field"><label for="plate">Placa</label><input id="plate" name="plate" class="input" [(ngModel)]="plate" (keyup.enter)="load(0)" placeholder="ABC123"></div>
      <div class="field">
        <label for="presence">Presencia</label>
        <select id="presence" name="presence" class="select" [(ngModel)]="presence" (change)="load(0)">
          <option value="">Todos</option><option value="INSIDE">Dentro</option><option value="OUTSIDE">Fuera</option>
        </select>
      </div>
      <button type="button" class="btn btn-secondary" (click)="load(0)">Filtrar</button>
      <button type="button" class="btn btn-ghost" (click)="plate = ''; presence = ''; load(0)">Limpiar</button>
    </div>

    <div class="card">
      <div class="card-header"><div><h2>Vehículos registrados</h2><p>{{ totalElements() }} vehículo(s).</p></div><button type="button" class="btn btn-secondary btn-sm" (click)="load(page())">Actualizar</button></div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando vehículos…</div>
      } @else if (!items().length) {
        <app-empty-state symbol="⛟" title="Sin vehículos" message="No hay vehículos que coincidan con los filtros." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Placa</th><th>Tipo</th><th>Marca / modelo</th><th>Inmueble</th><th>Presencia</th><th>Estado</th><th style="width:90px"></th></tr></thead>
            <tbody>
              @for (item of items(); track item.id) {
                <tr>
                  <td class="main-cell mono">{{ item.plate || '—' }}</td>
                  <td>{{ label(item.typeCode) }}</td>
                  <td>{{ item.brand || '—' }} {{ item.model || '' }}</td>
                  <td>{{ item.unitIdentifier || '—' }}</td>
                  <td><span class="badge" [class]="'badge ' + (item.presence === 'INSIDE' ? 'info' : 'neutral')">{{ label(item.presence) }}</span></td>
                  <td><span class="badge" [class]="'badge ' + tone(item.status)">{{ label(item.status) }}</span></td>
                  <td>
                    <div class="table-actions">
                      @if (auth.hasPermission('VEHICLES_UPDATE')) { <button type="button" class="btn-icon" title="Editar" (click)="openEdit(item)">✎</button> }
                      @if (auth.hasPermission('VEHICLES_DELETE')) { <button type="button" class="btn-icon danger" title="Eliminar" (click)="remove(item)">🗑</button> }
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

    <app-modal [open]="modalOpen()" [title]="editing() ? 'Editar vehículo' : 'Nuevo vehículo'" subtitle="El vehículo queda asociado a un inmueble y puede registrarse en portería." (closed)="modalOpen.set(false)">
      @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
      <form (ngSubmit)="save()" novalidate>
        <div class="form-grid">
          <div class="field">
            <label for="typeCode">Tipo *</label>
            <select id="typeCode" name="typeCode" class="select" required [(ngModel)]="form.typeCode">
              <option value="">Selecciona</option>
              @for (type of types(); track type.code) { <option [value]="type.code">{{ type.name }}</option> }
            </select>
          </div>
          <div class="field">
            <label for="plateValue">Placa</label>
            <input id="plateValue" name="plateValue" class="input mono" [(ngModel)]="form.plate" placeholder="ABC123">
          </div>
          <div class="field">
            <label for="unitId">Inmueble</label>
            <select id="unitId" name="unitId" class="select" [(ngModel)]="form.unitId">
              <option value="">Sin inmueble</option>
              @for (unit of units(); track unit.id) { <option [value]="unit.id">{{ unit.identifier }} · {{ unit.tower || 'Sin torre' }}</option> }
            </select>
          </div>
          <div class="field">
            <label for="ownerPersonId">Propietario</label>
            <select id="ownerPersonId" name="ownerPersonId" class="select" [(ngModel)]="form.ownerPersonId">
              <option value="">Sin propietario</option>
              @for (person of people(); track person.id) { <option [value]="person.id">{{ person.fullName }}</option> }
            </select>
          </div>
          <div class="field"><label for="brand">Marca</label><input id="brand" name="brand" class="input" [(ngModel)]="form.brand"></div>
          <div class="field"><label for="model">Modelo</label><input id="model" name="model" class="input" [(ngModel)]="form.model"></div>
          <div class="field"><label for="color">Color</label><input id="color" name="color" class="input" [(ngModel)]="form.color"></div>
          <div class="field"><label for="modelYear">Año</label><input id="modelYear" name="modelYear" type="number" class="input" [(ngModel)]="form.modelYear"></div>
          <div class="field">
            <label for="parkingSpaceId">Parqueadero</label>
            <select id="parkingSpaceId" name="parkingSpaceId" class="select" [(ngModel)]="form.parkingSpaceId">
              <option value="">Sin asignar</option>
              @for (space of parkingSpaces(); track space.id) { <option [value]="space.id">{{ space.code }} · {{ label(space.kind) }}</option> }
            </select>
          </div>
          <div class="field">
            <label for="vehicleStatus">Estado</label>
            <select id="vehicleStatus" name="vehicleStatus" class="select" [(ngModel)]="form.status">
              <option value="AUTHORIZED">Autorizado</option><option value="BLOCKED">Bloqueado</option>
            </select>
          </div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="modalOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="saving()">{{ editing() ? 'Guardar cambios' : 'Crear vehículo' }}</button>
        </div>
      </form>
    </app-modal>
  `
})
export class VehiclesPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly items = signal<Vehicle[]>([]);
  readonly types = signal<VehicleType[]>([]);
  readonly units = signal<PropertyUnit[]>([]);
  readonly people = signal<Person[]>([]);
  readonly parkingSpaces = signal<ParkingSpace[]>([]);
  readonly loading = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly modalOpen = signal(false);
  readonly editing = signal<Vehicle | null>(null);
  readonly saving = signal(false);
  readonly error = signal('');
  plate = '';
  presence = '';
  form: VehicleRequest = { typeCode: '' };

  constructor() {
    void this.load();
    void this.loadCatalogs();
  }

  async load(page = 0): Promise<void> {
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultVehicle>('/vehicles', {
        plate: this.plate.trim(),
        presence: this.presence,
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

  async loadCatalogs(): Promise<void> {
    try {
      const [types, units, people, spaces] = await Promise.all([
        firstValueFrom(this.api.get<VehicleType[]>('/vehicle-types')),
        firstValueFrom(this.api.get<PageResultPropertyUnit>('/properties', { size: 200 })),
        firstValueFrom(this.api.get<PageResultPerson>('/residents', { size: 200 })),
        firstValueFrom(this.api.get<ParkingSpace[]>('/parking-spaces'))
      ]);
      this.types.set(types);
      this.units.set(units.content ?? []);
      this.people.set(people.content ?? []);
      this.parkingSpaces.set(spaces);
    } catch {
      // Los catálogos se reintentan al abrir el formulario.
    }
  }

  openCreate(): void {
    this.editing.set(null);
    this.form = { typeCode: this.types()[0]?.code ?? '', status: 'AUTHORIZED' };
    this.error.set('');
    this.modalOpen.set(true);
  }

  openEdit(item: Vehicle): void {
    this.editing.set(item);
    this.form = {
      typeCode: item.typeCode ?? '',
      plate: item.plate,
      brand: item.brand,
      model: item.model,
      color: item.color,
      modelYear: item.modelYear,
      ownerPersonId: item.ownerPersonId,
      unitId: item.unitId,
      parkingSpaceId: item.parkingSpaceId,
      status: item.status ?? 'AUTHORIZED'
    };
    this.error.set('');
    this.modalOpen.set(true);
  }

  async save(): Promise<void> {
    this.error.set('');
    if (!this.form.typeCode) {
      this.error.set('Selecciona el tipo de vehículo.');
      return;
    }
    this.saving.set(true);
    try {
      const editing = this.editing();
      if (editing?.id) {
        await firstValueFrom(this.api.put<Vehicle>(`/vehicles/${editing.id}`, this.form));
        this.toast.success('Vehículo actualizado.');
      } else {
        await firstValueFrom(this.api.post<Vehicle>('/vehicles', this.form));
        this.toast.success('Vehículo creado.');
      }
      this.modalOpen.set(false);
      await this.load(this.page());
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async remove(item: Vehicle): Promise<void> {
    const accepted = await this.confirm.confirm({
      title: 'Eliminar vehículo',
      message: `¿Deseas eliminar el vehículo ${item.plate || ''}? Los registros de acceso históricos se conservan.`,
      confirmLabel: 'Eliminar',
      danger: true
    });
    if (!accepted || !item.id) return;
    try {
      await firstValueFrom(this.api.delete<void>(`/vehicles/${item.id}`));
      this.toast.success('Vehículo eliminado.');
      await this.load(this.page());
    } catch {
      // El interceptor global muestra el detalle.
    }
  }
}
