import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { label, statusTone } from '../../core/labels';
import { Company, CreateCompanyRequest } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-companies-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, ModalComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Empresas administradoras" subtitle="Registra y consulta las empresas que administran copropiedades dentro de la plataforma.">
      <button type="button" class="btn btn-primary" (click)="openCreate()">Nueva empresa</button>
    </app-page-header>

    <div class="grid grid-4">
      <div class="stat accent"><div class="stat-label">Empresas</div><div class="stat-value">{{ companies().length }}</div><div class="stat-hint">Registradas en la plataforma</div></div>
      <div class="stat"><div class="stat-label">Activas</div><div class="stat-value">{{ activeCount() }}</div><div class="stat-hint">Habilitadas para operar</div></div>
      <div class="stat"><div class="stat-label">Con NIT registrado</div><div class="stat-value">{{ withNit() }}</div><div class="stat-hint">Información tributaria completa</div></div>
      <div class="stat"><div class="stat-label">Última creación</div><div class="stat-value" style="font-size:20px">{{ latest() ? (latest() | date:'dd/MM/yyyy') : 'Sin registros' }}</div><div class="stat-hint">Empresa más reciente</div></div>
    </div>

    <div class="filter-bar">
      <div class="field" style="min-width:280px">
        <label for="search">Buscar</label>
        <input id="search" name="search" class="input" [(ngModel)]="search" placeholder="Nombre o NIT">
      </div>
      <button type="button" class="btn btn-secondary" (click)="load()">Actualizar</button>
    </div>

    <div class="card">
      <div class="card-header"><div><h2>Empresas</h2><p>{{ filtered().length }} empresa(s) coinciden con la búsqueda.</p></div></div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando empresas…</div>
      } @else if (!filtered().length) {
        <app-empty-state symbol="▥" title="Sin empresas" message="Registra una empresa administradora para poder crear copropiedades en la plataforma." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Empresa</th><th>NIT</th><th>Estado</th><th>Creada</th></tr></thead>
            <tbody>
              @for (company of filtered(); track company.id) {
                <tr>
                  <td class="main-cell">{{ company.name }}</td>
                  <td class="mono">{{ company.nit || '—' }}</td>
                  <td><span class="badge" [class]="'badge ' + tone(company.status)">{{ label(company.status || 'ACTIVE') }}</span></td>
                  <td class="nowrap">{{ company.createdAt | date:'dd/MM/yyyy HH:mm' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>

    <app-modal [open]="createOpen()" title="Nueva empresa administradora" subtitle="La empresa podrá agrupar una o varias copropiedades." [narrow]="true" (closed)="createOpen.set(false)">
      @if (formError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ formError() }}</div> }
      <form (ngSubmit)="create()" novalidate>
        <div class="field">
          <label for="companyName">Nombre *</label>
          <input id="companyName" name="companyName" class="input" required maxlength="200" [(ngModel)]="form.name" placeholder="Razón social de la empresa">
        </div>
        <div class="field" style="margin-top:12px">
          <label for="companyNit">NIT</label>
          <input id="companyNit" name="companyNit" class="input mono" maxlength="30" [(ngModel)]="form.nit" placeholder="Opcional">
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="createOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="saving()">{{ saving() ? 'Guardando…' : 'Registrar empresa' }}</button>
        </div>
      </form>
    </app-modal>
  `
})
export class CompaniesPage {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly companies = signal<Company[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly createOpen = signal(false);
  readonly formError = signal('');
  readonly activeCount = computed(() => this.companies().filter((company) => !company.status || company.status === 'ACTIVE').length);
  readonly withNit = computed(() => this.companies().filter((company) => Boolean(company.nit)).length);
  readonly latest = computed(() => {
    const dates = this.companies().map((company) => company.createdAt).filter((value): value is string => Boolean(value)).sort();
    return dates.length ? dates[dates.length - 1] : '';
  });
  readonly filtered = computed(() => {
    const term = this.search.trim().toLowerCase();
    if (!term) return this.companies();
    return this.companies().filter((company) =>
      (company.name ?? '').toLowerCase().includes(term) || (company.nit ?? '').toLowerCase().includes(term)
    );
  });
  search = '';
  form: CreateCompanyRequest = { name: '', nit: '' };

  constructor() {
    void this.load();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.companies.set(await firstValueFrom(this.api.get<Company[]>('/platform/companies')));
    } catch (error) {
      this.companies.set([]);
      this.toast.error(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  openCreate(): void {
    this.form = { name: '', nit: '' };
    this.formError.set('');
    this.createOpen.set(true);
  }

  async create(): Promise<void> {
    this.formError.set('');
    if (!this.form.name.trim()) {
      this.formError.set('El nombre de la empresa es obligatorio.');
      return;
    }
    this.saving.set(true);
    try {
      await firstValueFrom(this.api.post<Company>('/platform/companies', {
        name: this.form.name.trim(),
        nit: this.form.nit?.trim() || undefined
      }));
      this.toast.success('Empresa registrada.');
      this.createOpen.set(false);
      await this.load();
    } catch (error) {
      this.formError.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }
}
