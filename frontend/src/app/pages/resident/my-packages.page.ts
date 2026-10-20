import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { label, statusTone } from '../../core/labels';
import { Parcel } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, PageHeaderComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-my-packages-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Mis paquetes" subtitle="Consulta los paquetes recibidos en portería para tus inmuebles y su estado de entrega." />

    <div class="grid grid-4">
      <div class="stat accent"><div class="stat-label">Paquetes</div><div class="stat-value">{{ packages().length }}</div><div class="stat-hint">Según el filtro actual</div></div>
      <div class="stat warn"><div class="stat-label">Por entregar</div><div class="stat-value">{{ pendingCount() }}</div><div class="stat-hint">Disponibles en portería</div></div>
      <div class="stat"><div class="stat-label">Entregados</div><div class="stat-value">{{ deliveredCount() }}</div><div class="stat-hint">Recibidos por el residente</div></div>
      <div class="stat"><div class="stat-label">Devueltos</div><div class="stat-value">{{ returnedCount() }}</div><div class="stat-hint">Retornados a la transportadora</div></div>
    </div>

    <div class="filter-bar">
      <div class="field">
        <label for="statusFilter">Estado</label>
        <select id="statusFilter" name="statusFilter" class="select" [(ngModel)]="status" (change)="load()">
          <option value="">Todos</option>
          @for (option of statuses; track option) { <option [value]="option">{{ label(option) }}</option> }
        </select>
      </div>
      <button type="button" class="btn btn-secondary" (click)="load()">Filtrar</button>
      <button type="button" class="btn btn-ghost" (click)="status = ''; load()">Limpiar</button>
    </div>

    <div class="card">
      <div class="card-header"><div><h2>Paquetes de mis inmuebles</h2><p>Portería registra la recepción y la entrega de cada paquete.</p></div><button type="button" class="btn btn-secondary btn-sm" (click)="load()">Actualizar</button></div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando paquetes…</div>
      } @else if (!packages().length) {
        <app-empty-state symbol="▣" title="Sin paquetes" message="No hay paquetes registrados para tus inmuebles con el filtro seleccionado." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Paquete</th><th>Inmueble</th><th>Destinatario</th><th>Recepción</th><th>Entrega</th><th>Estado</th></tr></thead>
            <tbody>
              @for (item of packages(); track item.id) {
                <tr>
                  <td>
                    <div class="main-cell">{{ item.description || item.carrier || 'Paquete' }}</div>
                    <div class="muted small">{{ item.carrier || 'Sin transportadora' }}{{ item.trackingNumber ? ' · ' + item.trackingNumber : '' }}</div>
                  </td>
                  <td>{{ item.unitIdentifier || '—' }}</td>
                  <td>{{ item.recipientName || '—' }}</td>
                  <td class="nowrap">{{ item.receivedAt | date:'dd/MM/yyyy HH:mm' }}@if (item.notifiedAt) { <div class="muted small">Notificado {{ item.notifiedAt | date:'dd/MM HH:mm' }}</div> }</td>
                  <td class="nowrap">
                    @if (item.deliveredAt) {
                      {{ item.deliveredAt | date:'dd/MM/yyyy HH:mm' }}
                      @if (item.deliveredTo) { <div class="muted small">A {{ item.deliveredTo }}</div> }
                    } @else if (item.returnedAt) {
                      {{ item.returnedAt | date:'dd/MM/yyyy HH:mm' }}
                      <div class="muted small">Devuelto a la transportadora</div>
                    } @else { <span class="muted">Pendiente</span> }
                  </td>
                  <td>
                    <span class="badge" [class]="'badge ' + tone(item.status)">{{ label(item.status) }}</span>
                    @if (item.note) { <div class="muted small" style="margin-top:4px">{{ item.note }}</div> }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `
})
export class MyPackagesPage {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly statuses = ['RECEIVED', 'NOTIFIED', 'DELIVERED', 'RETURNED'];
  readonly packages = signal<Parcel[]>([]);
  readonly loading = signal(false);
  readonly pendingCount = computed(() => this.packages().filter((item) => item.status === 'RECEIVED' || item.status === 'NOTIFIED').length);
  readonly deliveredCount = computed(() => this.packages().filter((item) => item.status === 'DELIVERED').length);
  readonly returnedCount = computed(() => this.packages().filter((item) => item.status === 'RETURNED').length);
  status = '';

  constructor() {
    void this.load();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.packages.set(await firstValueFrom(this.api.get<Parcel[]>('/my/packages', { status: this.status })));
    } catch (error) {
      this.packages.set([]);
      this.toast.error(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }
}
