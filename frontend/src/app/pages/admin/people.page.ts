import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ConfirmService } from '../../core/confirm.service';
import { label, statusTone } from '../../core/labels';
import { PageResultPerson, PageResultPropertyUnit, Person, PersonRequest, PropertyUnit, RelationRequest, UnitRelation } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError, today } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-people-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, ModalComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Personas" subtitle="Propietarios, arrendatarios, residentes y contactos vinculados a los inmuebles.">
      @if (auth.hasPermission('RESIDENTES_CREATE')) { <button type="button" class="btn btn-primary" (click)="openCreate()">Nueva persona</button> }
    </app-page-header>

    <div class="filter-bar">
      <div class="field grow">
        <label for="q">Buscar</label>
        <input id="q" name="q" class="input" [(ngModel)]="query" (keyup.enter)="load(0)" placeholder="Nombre, documento, correo o teléfono">
      </div>
      <button type="button" class="btn btn-secondary" (click)="load(0)">Filtrar</button>
      <button type="button" class="btn btn-ghost" (click)="query = ''; load(0)">Limpiar</button>
    </div>

    <div class="card">
      <div class="card-header">
        <div><h2>Directorio</h2><p>{{ totalElements() }} persona(s) registradas.</p></div>
        <button type="button" class="btn btn-secondary btn-sm" (click)="load(page())">Actualizar</button>
      </div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando personas…</div>
      } @else if (!items().length) {
        <app-empty-state symbol="◎" title="Sin personas" message="No hay personas que coincidan con la búsqueda." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Nombre</th><th>Documento</th><th>Correo</th><th>Teléfono</th><th>Usuario</th><th style="width:150px"></th></tr></thead>
            <tbody>
              @for (person of items(); track person.id) {
                <tr>
                  <td class="main-cell">{{ person.fullName }}</td>
                  <td>{{ person.documentType || '' }} {{ person.documentNumber || '—' }}</td>
                  <td>{{ person.email || '—' }}</td>
                  <td>{{ person.phone || '—' }}</td>
                  <td>@if (person.userId) { <span class="badge success">Vinculado</span> } @else { <span class="badge neutral">Sin usuario</span> }</td>
                  <td>
                    <div class="table-actions">
                      <button type="button" class="btn btn-secondary btn-sm" (click)="openRelations(person)">Inmuebles</button>
                      @if (auth.hasPermission('RESIDENTES_UPDATE')) { <button type="button" class="btn-icon" title="Editar" (click)="openEdit(person)">✎</button> }
                      @if (auth.hasPermission('RESIDENTES_UPDATE')) { <button type="button" class="btn-icon" title="Vincular usuario" (click)="openLinkUser(person)">🔗</button> }
                      @if (auth.hasPermission('RESIDENTES_DELETE')) { <button type="button" class="btn-icon danger" title="Eliminar" (click)="remove(person)">🗑</button> }
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

    <app-modal [open]="formOpen()" [title]="editing() ? 'Editar persona' : 'Nueva persona'" subtitle="Los datos de contacto se usan para notificaciones y certificados." (closed)="formOpen.set(false)">
      @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
      <form (ngSubmit)="save()" novalidate>
        <div class="form-grid">
          <div class="field span-2">
            <label for="fullName">Nombre completo *</label>
            <input id="fullName" name="fullName" class="input" required [(ngModel)]="form.fullName">
          </div>
          <div class="field">
            <label for="docType">Tipo de documento</label>
            <input id="docType" name="docType" class="input" [(ngModel)]="form.documentType" placeholder="CC, CE, NIT, PASAPORTE">
          </div>
          <div class="field">
            <label for="docNumber">Número de documento</label>
            <input id="docNumber" name="docNumber" class="input" [(ngModel)]="form.documentNumber">
          </div>
          <div class="field">
            <label for="email">Correo electrónico</label>
            <input id="email" name="email" type="email" class="input" [(ngModel)]="form.email">
          </div>
          <div class="field">
            <label for="phone">Teléfono</label>
            <input id="phone" name="phone" class="input" [(ngModel)]="form.phone">
          </div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="formOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="saving()">{{ editing() ? 'Guardar cambios' : 'Crear persona' }}</button>
        </div>
      </form>
    </app-modal>

    <app-modal [open]="linkOpen()" title="Vincular usuario" subtitle="Asocia esta persona con una cuenta de acceso existente mediante su correo." (closed)="linkOpen.set(false)">
      @if (linkError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ linkError() }}</div> }
      <form (ngSubmit)="linkUser()" novalidate>
        <div class="field">
          <label for="linkEmail">Correo de la cuenta</label>
          <input id="linkEmail" name="linkEmail" type="email" class="input" required [(ngModel)]="linkEmail">
          <span class="hint">El usuario debe estar registrado previamente en la copropiedad.</span>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="linkOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="linkSaving()">Vincular</button>
        </div>
      </form>
    </app-modal>

    <app-modal [open]="relationsOpen()" [title]="'Inmuebles de ' + (selected()?.fullName || '')" subtitle="Relaciones vigentes e históricas con inmuebles." [wide]="true" (closed)="relationsOpen.set(false)">
      @if (relationError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ relationError() }}</div> }
      <div class="grid grid-2">
        <div>
          <h3 class="section-title">Relaciones registradas</h3>
          @for (relation of relations(); track relation.id) {
            <div class="row between" style="padding:10px 0;border-bottom:1px solid #eef2f7">
              <div>
                <div style="font-weight:600">{{ relation.unitIdentifier }}</div>
                <div class="muted small">{{ label(relation.type) }} · desde {{ relation.startDate | date:'dd/MM/yyyy' }}{{ relation.endDate ? ' hasta ' + (relation.endDate | date:'dd/MM/yyyy') : '' }}</div>
              </div>
              @if (!relation.endDate) {
                <button type="button" class="btn btn-secondary btn-sm" (click)="endRelation(relation)">Finalizar</button>
              } @else { <span class="badge neutral">Finalizada</span> }
            </div>
          } @empty { <p class="muted small">Esta persona no tiene relaciones con inmuebles.</p> }
        </div>
        <div>
          <h3 class="section-title">Agregar relación</h3>
          <form (ngSubmit)="addRelation()" novalidate>
            <div class="stack">
              <div class="field">
                <label for="unit">Inmueble</label>
                <select id="unit" name="unit" class="select" required [(ngModel)]="relationForm.unitId">
                  <option value="">Selecciona un inmueble</option>
                  @for (unit of units(); track unit.id) { <option [value]="unit.id">{{ unit.identifier }} · {{ label(unit.type) }}</option> }
                </select>
              </div>
              <div class="field">
                <label for="relationType">Tipo de relación</label>
                <select id="relationType" name="relationType" class="select" [(ngModel)]="relationForm.type">
                  <option value="OWNER">Propietario</option>
                  <option value="TENANT">Arrendatario</option>
                  <option value="RESIDENT">Residente</option>
                </select>
              </div>
              <div class="field">
                <label for="startDate">Fecha de inicio</label>
                <input id="startDate" name="startDate" type="date" class="input" [(ngModel)]="relationForm.startDate">
              </div>
              <button type="submit" class="btn btn-primary" [disabled]="relationSaving()">Agregar relación</button>
            </div>
          </form>
        </div>
      </div>
    </app-modal>
  `
})
export class PeoplePage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly items = signal<Person[]>([]);
  readonly loading = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly formOpen = signal(false);
  readonly editing = signal<Person | null>(null);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly linkOpen = signal(false);
  readonly linkSaving = signal(false);
  readonly linkError = signal('');
  readonly relationsOpen = signal(false);
  readonly selected = signal<Person | null>(null);
  readonly relations = signal<UnitRelation[]>([]);
  readonly units = signal<PropertyUnit[]>([]);
  readonly relationError = signal('');
  readonly relationSaving = signal(false);
  query = '';
  linkEmail = '';
  form: PersonRequest = { fullName: '' };
  relationForm: RelationRequest = { personId: '', type: 'OWNER', startDate: today() };

  constructor() {
    void this.load();
    void this.loadUnits();
  }

  async load(page = 0): Promise<void> {
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultPerson>('/residents', { q: this.query.trim(), page, size: 20 }));
      this.items.set(result.content ?? []);
      this.page.set(result.page ?? 0);
      this.totalPages.set(result.totalPages ?? 0);
      this.totalElements.set(result.totalElements ?? 0);
    } finally {
      this.loading.set(false);
    }
  }

  async loadUnits(): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.get<PageResultPropertyUnit>('/properties', { size: 200 }));
      this.units.set(result.content ?? []);
    } catch {
      // El selector de inmuebles se reintenta al abrir relaciones.
    }
  }

  openCreate(): void {
    this.editing.set(null);
    this.form = { fullName: '' };
    this.error.set('');
    this.formOpen.set(true);
  }

  openEdit(person: Person): void {
    this.editing.set(person);
    this.form = {
      fullName: person.fullName ?? '',
      documentType: person.documentType,
      documentNumber: person.documentNumber,
      email: person.email,
      phone: person.phone
    };
    this.error.set('');
    this.formOpen.set(true);
  }

  async save(): Promise<void> {
    this.error.set('');
    if (!this.form.fullName.trim()) {
      this.error.set('El nombre completo es obligatorio.');
      return;
    }
    this.saving.set(true);
    try {
      const editing = this.editing();
      if (editing?.id) {
        await firstValueFrom(this.api.put<Person>(`/residents/${editing.id}`, this.form));
        this.toast.success('Persona actualizada.');
      } else {
        await firstValueFrom(this.api.post<Person>('/residents', this.form));
        this.toast.success('Persona creada.');
      }
      this.formOpen.set(false);
      await this.load(this.page());
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async remove(person: Person): Promise<void> {
    const accepted = await this.confirm.confirm({
      title: 'Eliminar persona',
      message: `¿Deseas eliminar el registro de ${person.fullName}? Las relaciones y datos históricos podrían impedir la operación.`,
      confirmLabel: 'Eliminar',
      danger: true
    });
    if (!accepted || !person.id) return;
    try {
      await firstValueFrom(this.api.delete<void>(`/residents/${person.id}`));
      this.toast.success('Persona eliminada.');
      await this.load(this.page());
    } catch {
      // El interceptor global muestra el detalle.
    }
  }

  openLinkUser(person: Person): void {
    this.selected.set(person);
    this.linkEmail = person.email ?? '';
    this.linkError.set('');
    this.linkOpen.set(true);
  }

  async linkUser(): Promise<void> {
    const person = this.selected();
    this.linkError.set('');
    if (!person?.id || !this.linkEmail.trim()) {
      this.linkError.set('Ingresa el correo de la cuenta.');
      return;
    }
    this.linkSaving.set(true);
    try {
      await firstValueFrom(this.api.put<Person>(`/residents/${person.id}/user`, { email: this.linkEmail.trim() }));
      this.toast.success('Usuario vinculado a la persona.');
      this.linkOpen.set(false);
      await this.load(this.page());
    } catch (error) {
      this.linkError.set(describeError(error));
    } finally {
      this.linkSaving.set(false);
    }
  }

  async openRelations(person: Person): Promise<void> {
    this.selected.set(person);
    this.relationsOpen.set(true);
    this.relations.set([]);
    this.relationError.set('');
    this.relationForm = { personId: person.id ?? '', type: 'OWNER', startDate: today() };
    if (!this.units().length) await this.loadUnits();
    await this.loadRelations();
  }

  async loadRelations(): Promise<void> {
    const person = this.selected();
    if (!person?.id) return;
    try {
      const relations = await firstValueFrom(this.api.get<UnitRelation[]>(`/residents/${person.id}/relations`));
      this.relations.set(relations);
    } catch {
      // El interceptor global informa el error.
    }
  }

  async addRelation(): Promise<void> {
    this.relationError.set('');
    if (!this.relationForm.unitId || !this.selected()?.id) {
      this.relationError.set('Selecciona un inmueble y una persona válidos.');
      return;
    }
    this.relationSaving.set(true);
    try {
      await firstValueFrom(this.api.post<UnitRelation>(`/properties/${this.relationForm.unitId}/relations`, {
        personId: this.selected()!.id,
        type: this.relationForm.type,
        startDate: this.relationForm.startDate
      }));
      this.toast.success('Relación agregada.');
      this.relationForm.unitId = '';
      await this.loadRelations();
    } catch (error) {
      this.relationError.set(describeError(error));
    } finally {
      this.relationSaving.set(false);
    }
  }

  async endRelation(relation: UnitRelation): Promise<void> {
    if (!relation.id) return;
    const accepted = await this.confirm.confirm({
      title: 'Finalizar relación',
      message: `¿Deseas finalizar la relación de ${relation.personName} con ${relation.unitIdentifier}?`,
      confirmLabel: 'Finalizar',
      danger: true
    });
    if (!accepted) return;
    try {
      await firstValueFrom(this.api.post<void>(`/relations/${relation.id}/end`));
      this.toast.success('Relación finalizada.');
      await this.loadRelations();
    } catch {
      // El interceptor global muestra el detalle.
    }
  }
}
