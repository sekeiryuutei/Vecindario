import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { label, statusTone } from '../../core/labels';
import {
  PageResultParcel,
  PageResultPropertyUnit,
  Parcel,
  PropertyUnit,
  ReceiveRequest
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-porter-packages-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, ModalComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Paquetería" subtitle="Recibe paquetes para los inmuebles, notifica a los residentes y registra entregas o devoluciones.">
      <button type="button" class="btn btn-primary" (click)="openReceive()">Recibir paquete</button>
    </app-page-header>

    <div class="filter-bar">
      <div class="field grow">
        <label for="q">Buscar</label>
        <input id="q" name="q" class="input" [(ngModel)]="q" (keyup.enter)="load(0)" placeholder="Destinatario, guía o transportadora">
      </div>
      <div class="field">
        <label for="status">Estado</label>
        <select id="status" name="status" class="select" [(ngModel)]="status" (change)="load(0)">
          <option value="">Todos</option>
          @for (option of statuses; track option) { <option [value]="option">{{ label(option) }}</option> }
        </select>
      </div>
      <button type="button" class="btn btn-secondary" (click)="load(0)">Filtrar</button>
      <button type="button" class="btn btn-ghost" (click)="clearFilters()">Limpiar</button>
    </div>

    <div class="card">
      <div class="card-header"><div><h2>Paquetes</h2><p>{{ totalElements() }} paquete(s) registrados.</p></div><button type="button" class="btn btn-secondary btn-sm" (click)="load(page())">Actualizar</button></div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando paquetes…</div>
      } @else if (!items().length) {
        <app-empty-state symbol="▣" title="Sin paquetes" message="No hay paquetes que coincidan con los filtros seleccionados." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Paquete</th><th>Inmueble</th><th>Destinatario</th><th>Recibido</th><th>Estado</th><th style="width:190px"></th></tr></thead>
            <tbody>
              @for (item of items(); track item.id) {
                <tr>
                  <td>
                    <div class="main-cell">{{ item.description || 'Paquete' }}</div>
                    <div class="muted small">{{ item.carrier || 'Sin transportadora' }}{{ item.trackingNumber ? ' · ' + item.trackingNumber : '' }}</div>
                  </td>
                  <td>{{ item.unitIdentifier || unitName(item.unitId) }}</td>
                  <td>{{ item.recipientName }}</td>
                  <td class="nowrap">{{ item.receivedAt | date:'dd/MM/yyyy HH:mm' }}</td>
                  <td>
                    <span class="badge" [class]="'badge ' + tone(item.status)">{{ label(item.status) }}</span>
                    @if (item.deliveredTo) { <div class="muted small">Entregado a {{ item.deliveredTo }}</div> }
                    @if (item.note) { <div class="muted small">{{ item.note }}</div> }
                  </td>
                  <td>
                    <div class="table-actions">
                      @if (item.status === 'RECEIVED' || item.status === 'NOTIFIED') {
                        <button type="button" class="btn btn-primary btn-sm" (click)="openDeliver(item)">Entregar</button>
                        <button type="button" class="btn btn-secondary btn-sm" (click)="openReturn(item)">Devolver</button>
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

    <app-modal [open]="receiveOpen()" title="Recibir paquete" subtitle="Registra el paquete y el inmueble destinatario." (closed)="receiveOpen.set(false)">
      @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
      <form (ngSubmit)="receive()" novalidate>
        <div class="form-grid">
          <div class="field span-2">
            <label for="receiveUnit">Inmueble *</label>
            <select id="receiveUnit" name="receiveUnit" class="select" required [(ngModel)]="receiveForm.unitId">
              <option value="">Selecciona el inmueble</option>
              @for (unit of units(); track unit.id) { <option [value]="unit.id">{{ unit.identifier }} · {{ unit.tower || 'Sin torre' }}</option> }
            </select>
          </div>
          <div class="field"><label for="recipientName">Destinatario *</label><input id="recipientName" name="recipientName" class="input" required maxlength="200" [(ngModel)]="receiveForm.recipientName"></div>
          <div class="field"><label for="carrier">Transportadora</label><input id="carrier" name="carrier" class="input" maxlength="80" [(ngModel)]="receiveForm.carrier"></div>
          <div class="field"><label for="trackingNumber">Número de guía</label><input id="trackingNumber" name="trackingNumber" class="input mono" maxlength="80" [(ngModel)]="receiveForm.trackingNumber"></div>
          <div class="field"><label for="description">Descripción</label><input id="description" name="description" class="input" maxlength="300" [(ngModel)]="receiveForm.description" placeholder="Caja, sobre, etc."></div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="receiveOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="saving()">{{ saving() ? 'Registrando…' : 'Registrar recepción' }}</button>
        </div>
      </form>
    </app-modal>

    <app-modal [open]="deliverOpen()" title="Entregar paquete" [subtitle]="selected()?.description || 'Paquete'" (closed)="deliverOpen.set(false)">
      @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
      <div class="notice" style="margin-bottom:14px">Paquete para <strong>{{ selected()?.recipientName }}</strong> · {{ selected()?.unitIdentifier || unitName(selected()?.unitId) }}</div>
      <div class="field"><label for="deliveredTo">Entregado a *</label><input id="deliveredTo" name="deliveredTo" class="input" maxlength="200" [(ngModel)]="deliveredTo" placeholder="Nombre de quien recibe"></div>
      <div class="form-actions">
        <button type="button" class="btn btn-secondary" (click)="deliverOpen.set(false)">Cancelar</button>
        <button type="button" class="btn btn-primary" [disabled]="saving() || !deliveredTo.trim()" (click)="deliver()">Confirmar entrega</button>
      </div>
    </app-modal>

    <app-modal [open]="returnOpen()" title="Devolver paquete" [subtitle]="selected()?.description || 'Paquete'" (closed)="returnOpen.set(false)">
      @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
      <div class="field"><label for="returnNote">Motivo de la devolución</label><textarea id="returnNote" name="returnNote" class="textarea" maxlength="300" [(ngModel)]="returnNote" placeholder="Paquete rechazado, destinatario no disponible, etc."></textarea></div>
      <div class="form-actions">
        <button type="button" class="btn btn-secondary" (click)="returnOpen.set(false)">Cancelar</button>
        <button type="button" class="btn btn-danger" [disabled]="saving()" (click)="returnToCarrier()">Registrar devolución</button>
      </div>
    </app-modal>
  `
})
export class PorterPackagesPage {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly statuses = ['RECEIVED', 'NOTIFIED', 'DELIVERED', 'RETURNED'];
  readonly items = signal<Parcel[]>([]);
  readonly units = signal<PropertyUnit[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly receiveOpen = signal(false);
  readonly deliverOpen = signal(false);
  readonly returnOpen = signal(false);
  readonly selected = signal<Parcel | null>(null);
  readonly error = signal('');
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  q = '';
  status = '';
  deliveredTo = '';
  returnNote = '';
  receiveForm: ReceiveRequest = this.emptyReceive();

  constructor() {
    void this.load(0);
    void this.loadUnits();
  }

  async loadUnits(): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.get<PageResultPropertyUnit>('/properties', { size: 500 }));
      this.units.set(result.content ?? []);
    } catch {
      this.units.set([]);
    }
  }

  async load(page = 0): Promise<void> {
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultParcel>('/packages', {
        q: this.q.trim(),
        status: this.status,
        page,
        size: 20
      }));
      this.items.set(result.content ?? []);
      this.page.set(result.page ?? 0);
      this.totalPages.set(result.totalPages ?? 0);
      this.totalElements.set(result.totalElements ?? 0);
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  unitName(id: string | undefined): string {
    return this.units().find((unit) => unit.id === id)?.identifier ?? '—';
  }

  clearFilters(): void {
    this.q = '';
    this.status = '';
    void this.load(0);
  }

  openReceive(): void {
    this.receiveForm = this.emptyReceive();
    this.error.set('');
    this.receiveOpen.set(true);
  }

  async receive(): Promise<void> {
    this.error.set('');
    if (!this.receiveForm.unitId || !this.receiveForm.recipientName.trim()) {
      this.error.set('Selecciona el inmueble e indica el destinatario.');
      return;
    }
    this.saving.set(true);
    try {
      await firstValueFrom(this.api.post<Parcel>('/packages', {
        unitId: this.receiveForm.unitId,
        recipientName: this.receiveForm.recipientName.trim(),
        carrier: this.receiveForm.carrier?.trim() || undefined,
        trackingNumber: this.receiveForm.trackingNumber?.trim() || undefined,
        description: this.receiveForm.description?.trim() || undefined
      }));
      this.toast.success('Paquete recibido. El residente será notificado.');
      this.receiveOpen.set(false);
      await this.load(0);
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  openDeliver(item: Parcel): void {
    this.selected.set(item);
    this.deliveredTo = item.recipientName ?? '';
    this.error.set('');
    this.deliverOpen.set(true);
  }

  async deliver(): Promise<void> {
    const item = this.selected();
    if (!item?.id || !this.deliveredTo.trim()) return;
    this.saving.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.api.post<Parcel>(`/packages/${item.id}/deliver`, { deliveredTo: this.deliveredTo.trim() }));
      this.toast.success('Entrega registrada.');
      this.deliverOpen.set(false);
      await this.load(this.page());
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  openReturn(item: Parcel): void {
    this.selected.set(item);
    this.returnNote = '';
    this.error.set('');
    this.returnOpen.set(true);
  }

  async returnToCarrier(): Promise<void> {
    const item = this.selected();
    if (!item?.id) return;
    this.saving.set(true);
    this.error.set('');
    try {
      await firstValueFrom(this.api.post<Parcel>(`/packages/${item.id}/return`, { note: this.returnNote.trim() || undefined }));
      this.toast.success('Devolución registrada.');
      this.returnOpen.set(false);
      await this.load(this.page());
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  private emptyReceive(): ReceiveRequest {
    return { unitId: '', recipientName: '', carrier: '', trackingNumber: '', description: '' };
  }
}
