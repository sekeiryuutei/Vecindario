import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { label } from '../../core/labels';
import { MyUnit } from '../../core/models';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, PageHeaderComponent } from '../../shared/ui.components';
import { ToastService } from '../../core/toast.service';

@Component({
  selector: 'app-my-units-page',
  standalone: true,
  imports: [RouterLink, PageHeaderComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Mis inmuebles" subtitle="Inmuebles asociados a tu usuario y las relaciones vigentes que tienes con cada uno." />

    @if (loading()) {
      <div class="loading"><span class="spinner"></span> Cargando tus inmuebles…</div>
    } @else if (!units().length) {
      <div class="card"><app-empty-state symbol="⌂" title="Sin inmuebles asociados" message="Tu usuario aún no tiene inmuebles vinculados. Contacta a la administración para completar la asignación." /></div>
    } @else {
      <div class="grid grid-2">
        @for (unit of units(); track unit.unitId) {
          <div class="card">
            <div class="card-header">
              <div><h2>{{ unit.identifier }}</h2><p>{{ unit.tower || 'Sin torre' }} · {{ label(unit.unitType) }}</p></div>
              <div class="tag-list">
                @for (relation of unit.relations; track relation) { <span class="chip">{{ label(relation) }}</span> }
              </div>
            </div>
            <div class="card-body">
              <dl class="detail-list">
                <dt>Identificador</dt><dd class="mono">{{ unit.identifier }}</dd>
                <dt>Torre</dt><dd>{{ unit.tower || 'No registrada' }}</dd>
                <dt>Tipo</dt><dd>{{ label(unit.unitType) }}</dd>
                <dt>Relación</dt><dd>{{ relationText(unit) }}</dd>
              </dl>
              <div class="divider"></div>
              <div class="grid grid-2">
                @if (auth.hasPermission('FINANCE_VIEW_OWN')) {
                  <a class="btn btn-secondary" routerLink="/app/residente/estado-cuenta">Estado de cuenta</a>
                }
                @if (auth.hasPermission('CERTIFICATES_VIEW_OWN')) {
                  <a class="btn btn-secondary" routerLink="/app/residente/certificados">Certificados</a>
                }
                @if (auth.hasPermission('VEHICLES_VIEW_OWN')) {
                  <a class="btn btn-secondary" routerLink="/app/residente/vehiculos">Vehículos</a>
                }
                @if (auth.hasPermission('RESERVATIONS_VIEW_OWN')) {
                  <a class="btn btn-secondary" routerLink="/app/residente/reservas">Reservas</a>
                }
              </div>
            </div>
          </div>
        }
      </div>
    }

    @if (error()) { <div class="alert alert-error" style="margin-top:16px">{{ error() }}</div> }
  `
})
export class MyUnitsPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly units = signal<MyUnit[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');

  constructor() {
    void this.load();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      this.units.set(await firstValueFrom(this.api.get<MyUnit[]>('/my/units')));
    } catch (error) {
      this.error.set(describeError(error));
      this.toast.error(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  relationText(unit: MyUnit): string {
    const relations = (unit.relations ?? []).map((relation) => label(relation));
    return relations.length ? relations.join(' · ') : 'Sin relación registrada';
  }
}
