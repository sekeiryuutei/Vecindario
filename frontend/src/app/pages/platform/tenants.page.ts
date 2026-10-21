import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { label, statusTone } from '../../core/labels';
import {
  AddMemberRequest,
  Company,
  CreateTenantRequest,
  MemberView,
  PageResultTenantView,
  TenantIntegrations,
  TenantView
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

interface TenantForm {
  companyId: string;
  name: string;
  nit: string;
  slug: string;
  address: string;
  city: string;
  department: string;
  phone: string;
  email: string;
}

interface WompiIntegrationForm {
  publicKey: string;
  privateKey: string;
  integritySecret: string;
  eventsSecret: string;
}

interface AiIntegrationForm {
  provider: string;
  apiKey: string;
  model: string;
}

@Component({
  selector: 'app-tenants-page',
  standalone: true,
  imports: [FormsModule, PageHeaderComponent, ModalComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Copropiedades" subtitle="Crea y administra las copropiedades, sus funcionalidades y sus primeros usuarios.">
      <button type="button" class="btn btn-primary" (click)="openCreate()">Nueva copropiedad</button>
    </app-page-header>

    <div class="grid grid-4">
      <div class="stat accent"><div class="stat-label">Copropiedades</div><div class="stat-value">{{ totalElements() }}</div><div class="stat-hint">Registradas en la plataforma</div></div>
      <div class="stat"><div class="stat-label">Activas</div><div class="stat-value">{{ activeCount() }}</div><div class="stat-hint">En esta página</div></div>
      <div class="stat warn"><div class="stat-label">Aprovisionando</div><div class="stat-value">{{ provisioningCount() }}</div><div class="stat-hint">En esta página</div></div>
      <div class="stat danger"><div class="stat-label">Suspendidas</div><div class="stat-value">{{ suspendedCount() }}</div><div class="stat-hint">En esta página</div></div>
    </div>

    <div class="card">
      <div class="card-header"><div><h2>Copropiedades</h2><p>{{ totalElements() }} copropiedad(es) registradas. Página {{ page() + 1 }} de {{ totalPages() || 1 }}.</p></div><button type="button" class="btn btn-secondary btn-sm" (click)="load(page())">Actualizar</button></div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando copropiedades…</div>
      } @else if (!tenants().length) {
        <app-empty-state symbol="⌂" title="Sin copropiedades" message="Crea la primera copropiedad para iniciar la operación de la plataforma." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Copropiedad</th><th>Ubicación</th><th>Contacto</th><th>Funcionalidades</th><th>Estado</th><th style="width:110px"></th></tr></thead>
            <tbody>
              @for (tenant of tenants(); track tenant.id) {
                <tr>
                  <td><div class="main-cell">{{ tenant.name }}</div><div class="muted small">{{ tenant.slug }}{{ tenant.nit ? ' · ' + tenant.nit : '' }}</div></td>
                  <td><div>{{ tenant.city || '—' }}</div><div class="muted small">{{ tenant.department || 'Sin departamento' }}</div></td>
                  <td><div>{{ tenant.email || '—' }}</div><div class="muted small">{{ tenant.phone || 'Sin teléfono' }}</div></td>
                  <td><span class="badge info">{{ enabledFeatures(tenant) }} activas</span></td>
                  <td><span class="badge" [class]="'badge ' + tone(tenant.status)">{{ label(tenant.status) }}</span></td>
                  <td><button type="button" class="btn btn-secondary btn-sm" (click)="openManage(tenant)">Gestionar</button></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-pagination [page]="page()" [totalPages]="totalPages()" [totalElements]="totalElements()" (pageChange)="load($event)" />
      }
    </div>

    <app-modal [open]="createOpen()" title="Nueva copropiedad" subtitle="El registro crea el espacio de datos y aplica las migraciones del sistema." [wide]="true" (closed)="createOpen.set(false)">
      @if (formError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ formError() }}</div> }
      <form (ngSubmit)="create()" novalidate>
        <div class="form-grid">
          <div class="field span-2">
            <label for="tenantCompany">Empresa administradora *</label>
            <select id="tenantCompany" name="tenantCompany" class="select" required [(ngModel)]="form.companyId">
              <option value="">Selecciona la empresa</option>
              @for (company of companies(); track company.id) { <option [value]="company.id">{{ company.name }}</option> }
            </select>
            @if (!companies().length) { <span class="hint">No hay empresas registradas. Crea una empresa antes de continuar.</span> }
          </div>
          <div class="field"><label for="tenantName">Nombre *</label><input id="tenantName" name="tenantName" class="input" required maxlength="200" [(ngModel)]="form.name" (ngModelChange)="onNameChange($event)"></div>
          <div class="field"><label for="tenantNit">NIT</label><input id="tenantNit" name="tenantNit" class="input mono" maxlength="30" [(ngModel)]="form.nit"></div>
          <div class="field"><label for="tenantSlug">Identificador (slug) *</label><input id="tenantSlug" name="tenantSlug" class="input mono" required minlength="3" maxlength="40" [(ngModel)]="form.slug" (ngModelChange)="slugTouched = true"></div>
          <div class="field"><label for="tenantEmail">Correo de contacto</label><input id="tenantEmail" name="tenantEmail" type="email" class="input" maxlength="254" [(ngModel)]="form.email"></div>
          <div class="field"><label for="tenantPhone">Teléfono</label><input id="tenantPhone" name="tenantPhone" class="input" maxlength="40" [(ngModel)]="form.phone"></div>
          <div class="field"><label for="tenantCity">Ciudad</label><input id="tenantCity" name="tenantCity" class="input" maxlength="100" [(ngModel)]="form.city"></div>
          <div class="field"><label for="tenantDepartment">Departamento</label><input id="tenantDepartment" name="tenantDepartment" class="input" maxlength="100" [(ngModel)]="form.department"></div>
          <div class="field span-2"><label for="tenantAddress">Dirección</label><input id="tenantAddress" name="tenantAddress" class="input" maxlength="250" [(ngModel)]="form.address"></div>
        </div>
        <div class="notice" style="margin-top:12px">Después de crearla podrás activar funcionalidades y agregar el administrador principal desde la gestión de la copropiedad.</div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="createOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="saving() || !companies().length">{{ saving() ? 'Creando…' : 'Crear copropiedad' }}</button>
        </div>
      </form>
    </app-modal>

    <app-modal [open]="manageOpen()" [title]="selected()?.name || 'Copropiedad'" [subtitle]="selected()?.slug || ''" [wide]="true" (closed)="manageOpen.set(false)">
      @if (selected(); as tenant) {
        <div class="grid grid-2">
          <div>
            <h3 class="section-title">Información general</h3>
            <dl class="detail-list">
              <dt>Estado</dt><dd><span class="badge" [class]="'badge ' + tone(tenant.status)">{{ label(tenant.status) }}</span></dd>
              <dt>NIT</dt><dd class="mono">{{ tenant.nit || '—' }}</dd>
              <dt>Ubicación</dt><dd>{{ tenant.city || '—' }}{{ tenant.department ? ', ' + tenant.department : '' }}</dd>
              <dt>Dirección</dt><dd>{{ tenant.address || '—' }}</dd>
              <dt>Contacto</dt><dd>{{ tenant.email || '—' }}{{ tenant.phone ? ' · ' + tenant.phone : '' }}</dd>
              <dt>Moneda / zona</dt><dd>{{ tenant.currency || '—' }} · {{ tenant.timezone || '—' }}</dd>
            </dl>
            <div class="divider"></div>
            <h3 class="section-title">Estado operativo</h3>
            <div class="row" style="align-items:flex-end">
              <div class="field" style="flex:1">
                <label for="tenantStatus">Nuevo estado</label>
                <select id="tenantStatus" name="tenantStatus" class="select" [(ngModel)]="statusValue">
                  @for (option of statuses; track option) { <option [value]="option">{{ label(option) }}</option> }
                </select>
              </div>
              <button type="button" class="btn btn-secondary" [disabled]="saving()" (click)="changeStatus()">Aplicar</button>
            </div>
            <div class="notice" style="margin-top:12px">Suspender una copropiedad bloquea el acceso de sus usuarios al espacio de trabajo, sin eliminar su información.</div>
          </div>
          <div>
            <h3 class="section-title">Funcionalidades</h3>
            <div class="stack" style="gap:9px">
              @for (feature of features; track feature) {
                <label class="check" style="justify-content:space-between;border:1px solid var(--line);border-radius:8px;padding:10px 12px">
                  <span>{{ label(feature) }}</span>
                  <input
                    type="checkbox"
                    [checked]="tenant.features?.[feature] ?? false"
                    [disabled]="featureSaving() === (tenant.id + ':' + feature)"
                    (change)="toggleFeature(tenant, feature, $any($event.target).checked)">
                </label>
              }
            </div>
            <div class="divider"></div>
            <h3 class="section-title">Agregar usuario</h3>
            @if (memberError()) { <div class="alert alert-error" style="margin-bottom:12px">{{ memberError() }}</div> }
            @if (lastMember(); as member) {
              <div class="notice" style="margin-bottom:12px">Usuario {{ member.email }} agregado como {{ label(member.roleCode) }}.</div>
            }
            <div class="form-grid">
              <div class="field"><label for="memberName">Nombre completo *</label><input id="memberName" name="memberName" class="input" maxlength="200" [(ngModel)]="memberForm.fullName"></div>
              <div class="field"><label for="memberEmail">Correo *</label><input id="memberEmail" name="memberEmail" type="email" class="input" maxlength="254" [(ngModel)]="memberForm.email"></div>
              <div class="field">
                <label for="memberRole">Rol *</label>
                <select id="memberRole" name="memberRole" class="select" [(ngModel)]="memberForm.role">
                  @for (role of roles; track role) { <option [value]="role">{{ label(role) }}</option> }
                </select>
              </div>
              <div class="field" style="justify-content:flex-end"><button type="button" class="btn btn-primary" [disabled]="memberSaving()" (click)="addMember()">{{ memberSaving() ? 'Agregando…' : 'Agregar usuario' }}</button></div>
            </div>
            <p class="muted small" style="margin-top:8px">El usuario recibirá una invitación o se vinculará a su cuenta existente según su correo.</p>
          </div>
        </div>
        <div class="divider"></div>
        <h3 class="section-title">Integraciones de la copropiedad</h3>
        <p class="muted small" style="margin-bottom:12px">Las credenciales se cifran antes de guardarse y nunca se devuelven al navegador. Si dejas un secreto vacío, se conserva el valor actual.</p>
        @if (integrationsError()) { <div class="alert alert-error" style="margin-bottom:12px">{{ integrationsError() }}</div> }
        @if (integrationsLoading()) {
          <p class="muted small"><span class="spinner"></span> Consultando integraciones...</p>
        } @else if (integrations(); as cfg) {
          <div class="grid grid-2">
            <div>
              <h4 style="margin:0 0 8px">Wompi</h4>
              <p class="muted small" style="margin-bottom:10px">
                {{ cfg.wompi?.inherited ? 'Usando la configuración global de la plataforma.' : 'Configuración propia de esta copropiedad.' }}
              </p>
              <div class="form-grid">
                <div class="field span-2"><label for="wompiPublicKey">Llave pública</label><input id="wompiPublicKey" name="wompiPublicKey" class="input" maxlength="200" [(ngModel)]="wompiForm.publicKey" placeholder="pub_prod_..."></div>
                <div class="field"><label for="wompiPrivateKey">Llave privada</label><input id="wompiPrivateKey" name="wompiPrivateKey" class="input" type="password" autocomplete="new-password" maxlength="200" [(ngModel)]="wompiForm.privateKey" placeholder="Dejar vacío para conservar"></div>
                <div class="field"><label for="wompiIntegrity">Secreto de integridad</label><input id="wompiIntegrity" name="wompiIntegrity" class="input" type="password" autocomplete="new-password" maxlength="300" [(ngModel)]="wompiForm.integritySecret" placeholder="Dejar vacío para conservar"></div>
                <div class="field span-2"><label for="wompiEvents">Secreto de eventos</label><input id="wompiEvents" name="wompiEvents" class="input" type="password" autocomplete="new-password" maxlength="300" [(ngModel)]="wompiForm.eventsSecret" placeholder="Debe ser distinto al de integridad"></div>
              </div>
              <div class="row" style="margin-top:10px">
                <button type="button" class="btn btn-primary btn-sm" [disabled]="integrationSaving() === 'WOMPI'" (click)="saveWompi()">{{ integrationSaving() === 'WOMPI' ? 'Guardando…' : 'Guardar Wompi' }}</button>
                @if (!cfg.wompi?.inherited) {
                  <button type="button" class="btn btn-ghost btn-sm" [disabled]="integrationSaving() === 'WOMPI'" (click)="resetIntegration('WOMPI')">Volver a la configuración global</button>
                }
              </div>
            </div>
            <div>
              <h4 style="margin:0 0 8px">Inteligencia artificial</h4>
              <p class="muted small" style="margin-bottom:10px">
                {{ cfg.ai?.inherited ? 'Usando la configuración global de la plataforma.' : 'Credenciales propias de esta copropiedad.' }}
              </p>
              <div class="form-grid">
                <div class="field">
                  <label for="aiProvider">Proveedor</label>
                  <select id="aiProvider" name="aiProvider" class="select" [(ngModel)]="aiForm.provider">
                    <option value="gemini">Gemini</option>
                    <option value="openai">OpenAI compatible</option>
                    <option value="local">Respuestas locales</option>
                  </select>
                </div>
                <div class="field"><label for="aiModel">Modelo</label><input id="aiModel" name="aiModel" class="input" maxlength="120" [(ngModel)]="aiForm.model" placeholder="gemini-3.7-flash"></div>
                <div class="field span-2"><label for="aiKey">Clave del proveedor</label><input id="aiKey" name="aiKey" class="input" type="password" autocomplete="new-password" maxlength="500" [(ngModel)]="aiForm.apiKey" placeholder="Dejar vacío para conservar"></div>
              </div>
              <div class="row" style="margin-top:10px">
                <button type="button" class="btn btn-primary btn-sm" [disabled]="integrationSaving() === 'AI'" (click)="saveAi()">{{ integrationSaving() === 'AI' ? 'Guardando…' : 'Guardar IA' }}</button>
                @if (!cfg.ai?.inherited) {
                  <button type="button" class="btn btn-ghost btn-sm" [disabled]="integrationSaving() === 'AI'" (click)="resetIntegration('AI')">Volver a la configuración global</button>
                }
              </div>
            </div>
          </div>
        }
      }
    </app-modal>
  `
})
export class TenantsPage {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly statuses = ['PROVISIONING', 'ACTIVE', 'SUSPENDED', 'FAILED'];
  readonly features = ['WHATSAPP', 'CAMERAS', 'LPR', 'ONLINE_PAYMENTS'];
  readonly roles = ['ADMINISTRADOR', 'SECRETARIA_ADMINISTRACION', 'CONTADOR', 'CONSEJO', 'PORTERO', 'SUPERVISOR_PORTERIA', 'PROPIETARIO', 'ARRENDATARIO', 'RESIDENTE', 'MANTENIMIENTO'];
  readonly tenants = signal<TenantView[]>([]);
  readonly companies = signal<Company[]>([]);
  readonly selected = signal<TenantView | null>(null);
  readonly lastMember = signal<MemberView | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly createOpen = signal(false);
  readonly manageOpen = signal(false);
  readonly formError = signal('');
  readonly memberError = signal('');
  readonly memberSaving = signal(false);
  readonly featureSaving = signal('');
  readonly integrations = signal<TenantIntegrations | null>(null);
  readonly integrationsLoading = signal(false);
  readonly integrationsError = signal('');
  readonly integrationSaving = signal('');
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly activeCount = computed(() => this.tenants().filter((tenant) => tenant.status === 'ACTIVE').length);
  readonly provisioningCount = computed(() => this.tenants().filter((tenant) => tenant.status === 'PROVISIONING').length);
  readonly suspendedCount = computed(() => this.tenants().filter((tenant) => tenant.status === 'SUSPENDED').length);
  slugTouched = false;
  statusValue = 'ACTIVE';
  form: TenantForm = this.emptyForm();
  memberForm: AddMemberRequest = { email: '', fullName: '', role: 'ADMINISTRADOR' };
  wompiForm: WompiIntegrationForm = { publicKey: '', privateKey: '', integritySecret: '', eventsSecret: '' };
  aiForm: AiIntegrationForm = { provider: 'gemini', apiKey: '', model: '' };

  constructor() {
    void this.loadCompanies();
    void this.load(0);
  }

  async loadCompanies(): Promise<void> {
    try {
      this.companies.set(await firstValueFrom(this.api.get<Company[]>('/platform/companies')));
    } catch {
      this.companies.set([]);
    }
  }

  async load(page = 0): Promise<void> {
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultTenantView>('/platform/tenants', { page, size: 20 }));
      this.tenants.set(result.content ?? []);
      this.page.set(result.page ?? 0);
      this.totalPages.set(result.totalPages ?? 0);
      this.totalElements.set(result.totalElements ?? 0);
    } catch (error) {
      this.tenants.set([]);
      this.toast.error(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  enabledFeatures(tenant: TenantView): number {
    return this.features.filter((feature) => tenant.features?.[feature]).length;
  }

  openCreate(): void {
    this.form = this.emptyForm();
    this.slugTouched = false;
    this.formError.set('');
    this.createOpen.set(true);
    if (!this.companies().length) void this.loadCompanies();
  }

  onNameChange(value: string): void {
    this.form.name = value;
    if (!this.slugTouched) this.form.slug = this.slugify(value);
  }

  async create(): Promise<void> {
    this.formError.set('');
    if (!this.form.companyId || !this.form.name.trim() || !this.form.slug.trim()) {
      this.formError.set('Selecciona la empresa e indica el nombre y el identificador.');
      return;
    }
    if (this.form.slug.trim().length < 3) {
      this.formError.set('El identificador debe tener al menos 3 caracteres.');
      return;
    }
    const body: CreateTenantRequest = {
      companyId: this.form.companyId,
      name: this.form.name.trim(),
      slug: this.form.slug.trim(),
      nit: this.form.nit.trim() || undefined,
      address: this.form.address.trim() || undefined,
      city: this.form.city.trim() || undefined,
      department: this.form.department.trim() || undefined,
      phone: this.form.phone.trim() || undefined,
      email: this.form.email.trim() || undefined
    };
    this.saving.set(true);
    try {
      const created = await firstValueFrom(this.api.post<TenantView>('/platform/tenants', body));
      this.toast.success('Copropiedad creada.');
      this.createOpen.set(false);
      await this.load(0);
      this.openManage(created);
    } catch (error) {
      this.formError.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  openManage(tenant: TenantView): void {
    this.selected.set(tenant);
    this.statusValue = tenant.status ?? 'ACTIVE';
    this.memberForm = { email: '', fullName: '', role: 'ADMINISTRADOR' };
    this.memberError.set('');
    this.lastMember.set(null);
    this.integrations.set(null);
    this.integrationsError.set('');
    this.wompiForm = { publicKey: '', privateKey: '', integritySecret: '', eventsSecret: '' };
    this.aiForm = { provider: 'gemini', apiKey: '', model: '' };
    this.manageOpen.set(true);
    if (tenant.id) void this.loadIntegrations(tenant.id);
  }

  async loadIntegrations(tenantId: string): Promise<void> {
    this.integrationsLoading.set(true);
    this.integrationsError.set('');
    try {
      const view = await firstValueFrom(this.api.get<TenantIntegrations>(`/platform/tenants/${tenantId}/integrations`));
      this.integrations.set(view);
      this.wompiForm = {
        publicKey: view.wompi?.publicKey ?? '',
        privateKey: '',
        integritySecret: '',
        eventsSecret: ''
      };
      this.aiForm = {
        provider: view.ai?.provider ?? 'gemini',
        apiKey: '',
        model: view.ai?.model ?? ''
      };
    } catch (error) {
      this.integrationsError.set(describeError(error));
    } finally {
      this.integrationsLoading.set(false);
    }
  }

  async saveWompi(): Promise<void> {
    const tenant = this.selected();
    if (!tenant?.id) return;
    this.integrationSaving.set('WOMPI');
    this.integrationsError.set('');
    try {
      this.integrations.set(await firstValueFrom(this.api.put<TenantIntegrations>(
        `/platform/tenants/${tenant.id}/integrations/wompi`, this.wompiForm)));
      this.wompiForm = {
        publicKey: this.integrations()?.wompi?.publicKey ?? '',
        privateKey: '',
        integritySecret: '',
        eventsSecret: ''
      };
      this.toast.success('Credenciales de Wompi actualizadas.');
    } catch (error) {
      this.integrationsError.set(describeError(error));
    } finally {
      this.integrationSaving.set('');
    }
  }

  async saveAi(): Promise<void> {
    const tenant = this.selected();
    if (!tenant?.id) return;
    this.integrationSaving.set('AI');
    this.integrationsError.set('');
    try {
      this.integrations.set(await firstValueFrom(this.api.put<TenantIntegrations>(
        `/platform/tenants/${tenant.id}/integrations/ai`, this.aiForm)));
      this.aiForm = {
        provider: this.integrations()?.ai?.provider ?? this.aiForm.provider,
        apiKey: '',
        model: this.integrations()?.ai?.model ?? ''
      };
      this.toast.success('Configuración de IA actualizada.');
    } catch (error) {
      this.integrationsError.set(describeError(error));
    } finally {
      this.integrationSaving.set('');
    }
  }

  async resetIntegration(provider: string): Promise<void> {
    const tenant = this.selected();
    if (!tenant?.id) return;
    this.integrationSaving.set(provider);
    this.integrationsError.set('');
    try {
      await firstValueFrom(this.api.delete<void>(`/platform/tenants/${tenant.id}/integrations/${provider}`));
      await this.loadIntegrations(tenant.id);
      this.toast.success('La copropiedad volvió a la configuración global.');
    } catch (error) {
      this.integrationsError.set(describeError(error));
    } finally {
      this.integrationSaving.set('');
    }
  }

  async changeStatus(): Promise<void> {
    const tenant = this.selected();
    if (!tenant?.id) return;
    this.saving.set(true);
    try {
      const updated = await firstValueFrom(this.api.patch<TenantView>(`/platform/tenants/${tenant.id}/status`, { status: this.statusValue }));
      this.selected.set(updated);
      this.tenants.update((items) => items.map((item) => item.id === updated.id ? updated : item));
      this.toast.success('Estado de la copropiedad actualizado.');
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async toggleFeature(tenant: TenantView, feature: string, enabled: boolean): Promise<void> {
    if (!tenant.id) return;
    this.featureSaving.set(`${tenant.id}:${feature}`);
    try {
      const features = await firstValueFrom(this.api.put<Record<string, boolean>>(`/platform/tenants/${tenant.id}/features/${feature}`, { enabled }));
      const updated: TenantView = { ...tenant, features };
      this.selected.set(updated);
      this.tenants.update((items) => items.map((item) => item.id === tenant.id ? updated : item));
      this.toast.success(`${label(feature)} ${enabled ? 'habilitada' : 'deshabilitada'}.`);
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.featureSaving.set('');
    }
  }

  async addMember(): Promise<void> {
    const tenant = this.selected();
    this.memberError.set('');
    if (!tenant?.id) return;
    if (!this.memberForm.fullName.trim() || !this.memberForm.email.trim() || !this.memberForm.role) {
      this.memberError.set('Indica el nombre, el correo y el rol del usuario.');
      return;
    }
    this.memberSaving.set(true);
    try {
      const member = await firstValueFrom(this.api.post<MemberView>(`/platform/tenants/${tenant.id}/members`, {
        email: this.memberForm.email.trim(),
        fullName: this.memberForm.fullName.trim(),
        role: this.memberForm.role
      }));
      this.lastMember.set(member);
      this.memberForm = { email: '', fullName: '', role: 'ADMINISTRADOR' };
      this.toast.success('Usuario agregado a la copropiedad.');
    } catch (error) {
      this.memberError.set(describeError(error));
    } finally {
      this.memberSaving.set(false);
    }
  }

  private slugify(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 40);
  }

  private emptyForm(): TenantForm {
    return { companyId: '', name: '', nit: '', slug: '', address: '', city: '', department: '', phone: '', email: '' };
  }
}
