import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ConfirmService } from '../../core/confirm.service';
import { label, statusTone } from '../../core/labels';
import {
  DecisionRequest,
  Invitation,
  InvitationCreated,
  InvitationRequest,
  MyUnit,
  Visit
} from '../../core/models';
import { toQrDataUrl } from '../../core/qr';
import { ToastService } from '../../core/toast.service';
import { describeError, toIsoInstant } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-my-visitors-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, ModalComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Visitas e invitaciones" subtitle="Autoriza a quienes llegan a tus inmuebles y genera códigos QR temporales para tus invitados.">
      @if (auth.hasPermission('VISITORS_CREATE')) {
        <button type="button" class="btn btn-primary" [disabled]="!units().length" (click)="openCreate()">Nueva invitación</button>
      }
    </app-page-header>

    <div class="grid grid-4">
      <div class="stat accent"><div class="stat-label">Solicitudes pendientes</div><div class="stat-value">{{ pending().length }}</div><div class="stat-hint">Requieren tu decisión</div></div>
      <div class="stat"><div class="stat-label">Invitaciones activas</div><div class="stat-value">{{ activeInvitations() }}</div><div class="stat-hint">Con QR vigente</div></div>
      <div class="stat"><div class="stat-label">Visitantes dentro</div><div class="stat-value">{{ inside().length }}</div><div class="stat-hint">En tus inmuebles</div></div>
      <div class="stat"><div class="stat-label">Inmuebles</div><div class="stat-value">{{ units().length }}</div><div class="stat-hint">Asociados a tu usuario</div></div>
    </div>

    <div class="card">
      <div class="card-header">
        <div><h2>Solicitudes de ingreso</h2><p>Visitantes sin invitación registrados por portería.</p></div>
        <button type="button" class="btn btn-secondary btn-sm" (click)="loadRequests()">Actualizar</button>
      </div>
      @if (loadingRequests()) {
        <div class="loading"><span class="spinner"></span> Cargando solicitudes…</div>
      } @else if (!pending().length) {
        <app-empty-state symbol="✓" title="Sin solicitudes pendientes" message="No hay visitantes esperando tu autorización." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Solicitada</th><th>Visitante</th><th>Inmueble</th><th>Personas</th><th>Observación</th><th style="width:180px"></th></tr></thead>
            <tbody>
              @for (visit of pending(); track visit.id) {
                <tr>
                  <td class="nowrap">{{ visit.requestedAt | date:'dd/MM/yyyy HH:mm' }}</td>
                  <td><div class="main-cell">{{ visit.visitorName }}</div><div class="muted small">{{ visit.documentNumber || 'Sin documento' }}{{ visit.plate ? ' · ' + visit.plate : '' }}</div></td>
                  <td>{{ visit.unitIdentifier || unitName(visit.unitId) }}</td>
                  <td>{{ visit.peopleCount || 1 }}</td>
                  <td>{{ visit.note || '—' }}</td>
                  <td>
                    <div class="table-actions">
                      <button type="button" class="btn btn-primary btn-sm" [disabled]="acting() === visit.id" (click)="openDecision(visit, 'authorize')">Autorizar</button>
                      <button type="button" class="btn btn-secondary btn-sm" [disabled]="acting() === visit.id" (click)="openDecision(visit, 'reject')">Rechazar</button>
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>

    <div class="card">
      <div class="card-header">
        <div><h2>Mis invitaciones</h2><p>Comparte el código QR con tus visitantes. Cada ingreso consume un uso de la invitación.</p></div>
        <div class="row">
          <div class="field" style="min-width:190px">
            <label for="unitFilter">Inmueble</label>
            <select id="unitFilter" name="unitFilter" class="select" [(ngModel)]="unitFilter" (change)="loadInvitations()">
              <option value="">Todos mis inmuebles</option>
              @for (unit of units(); track unit.unitId) { <option [value]="unit.unitId">{{ unit.identifier }} · {{ unit.tower || 'Sin torre' }}</option> }
            </select>
          </div>
          <button type="button" class="btn btn-secondary btn-sm" (click)="loadInvitations()">Actualizar</button>
        </div>
      </div>
      @if (loadingInvitations()) {
        <div class="loading"><span class="spinner"></span> Cargando invitaciones…</div>
      } @else if (!invitations().length) {
        <app-empty-state symbol="▣" title="Sin invitaciones" message="Crea una invitación para generar un código QR temporal para tu visitante." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Visitante</th><th>Inmueble</th><th>Vigencia</th><th>Usos</th><th>Vehículo</th><th>Estado</th><th style="width:230px"></th></tr></thead>
            <tbody>
              @for (invitation of invitations(); track invitation.id) {
                <tr>
                  <td><div class="main-cell">{{ invitation.visitorName }}</div><div class="muted small">{{ invitation.documentNumber || 'Sin documento' }}</div></td>
                  <td>{{ invitation.unitIdentifier || unitName(invitation.unitId) }}</td>
                  <td class="nowrap">{{ invitation.validFrom | date:'dd/MM HH:mm' }}<br><span class="muted small">hasta {{ invitation.validTo | date:'dd/MM HH:mm' }}</span></td>
                  <td>{{ invitation.usedCount ?? 0 }} / {{ invitation.maxEntries ?? 1 }}</td>
                  <td class="mono">{{ invitation.plate || '—' }}</td>
                  <td><span class="badge" [class]="'badge ' + tone(invitation.status)">{{ label(invitation.status) }}</span></td>
                  <td>
                    <div class="table-actions">
                      @if (invitation.status === 'ACTIVE') {
                        @if (auth.hasPermission('VISITORS_CREATE')) {
                          <button type="button" class="btn btn-secondary btn-sm" (click)="regenerate(invitation)">Nuevo QR</button>
                          <button type="button" class="btn btn-ghost btn-sm" (click)="cancel(invitation)">Cancelar</button>
                        }
                      }
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>

    <div class="card">
      <div class="card-header"><div><h2>Visitantes dentro</h2><p>Visitas activas en tus inmuebles.</p></div><span class="badge info">{{ inside().length }}</span></div>
      @if (!inside().length) {
        <app-empty-state symbol="⇄" title="Sin visitantes dentro" message="No hay visitantes activos en tus inmuebles." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Visitante</th><th>Inmueble</th><th>Ingreso</th><th>Vehículo</th></tr></thead>
            <tbody>
              @for (visit of inside(); track visit.id) {
                <tr>
                  <td class="main-cell">{{ visit.visitorName }}</td>
                  <td>{{ visit.unitIdentifier || unitName(visit.unitId) }}</td>
                  <td>{{ visit.entryTime | date:'dd/MM/yyyy HH:mm' }}</td>
                  <td class="mono">{{ visit.plate || '—' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>

    <app-modal [open]="createOpen()" title="Nueva invitación" subtitle="El código QR se genera una sola vez; si se pierde puedes regenerarlo." (closed)="createOpen.set(false)">
      @if (formError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ formError() }}</div> }
      <form (ngSubmit)="create()" novalidate>
        <div class="form-grid">
          <div class="field span-2">
            <label for="invUnit">Inmueble *</label>
            <select id="invUnit" name="invUnit" class="select" required [(ngModel)]="form.unitId">
              <option value="">Selecciona el inmueble</option>
              @for (unit of units(); track unit.unitId) { <option [value]="unit.unitId">{{ unit.identifier }} · {{ unit.tower || 'Sin torre' }}</option> }
            </select>
          </div>
          <div class="field"><label for="visitorName">Nombre del visitante *</label><input id="visitorName" name="visitorName" class="input" required maxlength="200" [(ngModel)]="form.visitorName"></div>
          <div class="field"><label for="documentNumber">Documento</label><input id="documentNumber" name="documentNumber" class="input" maxlength="30" [(ngModel)]="form.documentNumber"></div>
          <div class="field"><label for="phone">Teléfono</label><input id="phone" name="phone" class="input" maxlength="40" [(ngModel)]="form.phone"></div>
          <div class="field"><label for="plate">Placa del vehículo</label><input id="plate" name="plate" class="input mono" maxlength="12" [(ngModel)]="form.plate" style="text-transform:uppercase"></div>
          <div class="field"><label for="peopleCount">Número de personas *</label><input id="peopleCount" name="peopleCount" type="number" min="1" max="50" class="input" [(ngModel)]="form.peopleCount"></div>
          <div class="field"><label for="maxEntries">Ingresos permitidos *</label><input id="maxEntries" name="maxEntries" type="number" min="1" max="20" class="input" [(ngModel)]="form.maxEntries"></div>
          <div class="field"><label for="validFrom">Válida desde *</label><input id="validFrom" name="validFrom" type="datetime-local" class="input" required [(ngModel)]="validFrom"></div>
          <div class="field"><label for="validTo">Válida hasta *</label><input id="validTo" name="validTo" type="datetime-local" class="input" required [(ngModel)]="validTo"></div>
          <div class="field span-2"><label for="notes">Notas</label><textarea id="notes" name="notes" class="textarea" maxlength="300" [(ngModel)]="form.notes"></textarea></div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="createOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="saving()">{{ saving() ? 'Generando…' : 'Crear invitación' }}</button>
        </div>
      </form>
    </app-modal>

    <app-modal [open]="qrOpen()" title="Código QR de la invitación" [subtitle]="qrInvitation()?.visitorName || ''" (closed)="qrOpen.set(false)">
      @if (qrImage()) {
        <div style="text-align:center">
          <div class="qr-box"><img [src]="qrImage()" alt="Código QR de la invitación"></div>
          <div class="muted small" style="margin-top:12px">Presenta este código en portería. También puedes compartir la imagen con tu visitante.</div>
          <div class="row" style="justify-content:center;margin-top:16px">
            <button type="button" class="btn btn-primary" (click)="downloadQr()">Descargar QR</button>
            <button type="button" class="btn btn-secondary" (click)="copyToken()">Copiar código</button>
          </div>
        </div>
      } @else {
        <div class="loading"><span class="spinner"></span> Generando código QR…</div>
      }
      @if (qrInvitation(); as invitation) {
        <dl class="detail-list" style="margin-top:18px">
          <dt>Inmueble</dt><dd>{{ invitation.unitIdentifier || unitName(invitation.unitId) }}</dd>
          <dt>Vigencia</dt><dd>{{ invitation.validFrom | date:'dd/MM/yyyy HH:mm' }} a {{ invitation.validTo | date:'dd/MM/yyyy HH:mm' }}</dd>
          <dt>Usos</dt><dd>{{ invitation.usedCount ?? 0 }} de {{ invitation.maxEntries ?? 1 }}</dd>
          <dt>Personas</dt><dd>{{ invitation.peopleCount || 1 }}</dd>
        </dl>
      }
    </app-modal>

    <app-modal [open]="decisionOpen()" [title]="decisionType() === 'authorize' ? 'Autorizar visitante' : 'Rechazar visitante'" [subtitle]="selectedRequest()?.visitorName || ''" [narrow]="true" (closed)="decisionOpen.set(false)">
      <div class="notice" style="margin-bottom:14px">
        {{ selectedRequest()?.visitorName }} llega al inmueble {{ selectedRequest()?.unitIdentifier || unitName(selectedRequest()?.unitId) }}.
      </div>
      <div class="field"><label for="decisionNote">Nota para portería (opcional)</label><textarea id="decisionNote" name="decisionNote" class="textarea" maxlength="300" [(ngModel)]="decisionNote"></textarea></div>
      <div class="form-actions">
        <button type="button" class="btn btn-secondary" (click)="decisionOpen.set(false)">Volver</button>
        <button type="button" class="btn" [class.btn-primary]="decisionType() === 'authorize'" [class.btn-danger]="decisionType() === 'reject'" [disabled]="acting() !== ''" (click)="submitDecision()">
          {{ decisionType() === 'authorize' ? 'Autorizar ingreso' : 'Rechazar ingreso' }}
        </button>
      </div>
    </app-modal>
  `
})
export class MyVisitorsPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly units = signal<MyUnit[]>([]);
  readonly invitations = signal<Invitation[]>([]);
  readonly pending = signal<Visit[]>([]);
  readonly inside = signal<Visit[]>([]);
  readonly loadingInvitations = signal(false);
  readonly loadingRequests = signal(false);
  readonly saving = signal(false);
  readonly acting = signal('');
  readonly createOpen = signal(false);
  readonly qrOpen = signal(false);
  readonly decisionOpen = signal(false);
  readonly qrImage = signal('');
  readonly qrInvitation = signal<Invitation | null>(null);
  readonly selectedRequest = signal<Visit | null>(null);
  readonly decisionType = signal<'authorize' | 'reject'>('authorize');
  readonly formError = signal('');
  private qrToken = '';
  unitFilter = '';
  validFrom = this.localDateTime(0);
  validTo = this.localDateTime(1);
  decisionNote = '';
  form = this.emptyForm();

  constructor() {
    void this.loadUnits();
    void this.loadRequests();
    void this.loadInside();
    void this.loadInvitations();
  }

  activeInvitations(): number {
    return this.invitations().filter((invitation) => invitation.status === 'ACTIVE').length;
  }

  unitName(id: string | undefined): string {
    return this.units().find((unit) => unit.unitId === id)?.identifier ?? '—';
  }

  async loadUnits(): Promise<void> {
    try {
      const units = await firstValueFrom(this.api.get<MyUnit[]>('/my/units'));
      this.units.set(units);
      if (units.length === 1 && units[0].unitId) this.unitFilter = units[0].unitId;
      await this.loadInvitations();
    } catch (error) {
      this.toast.error(describeError(error));
    }
  }

  async loadInvitations(): Promise<void> {
    this.loadingInvitations.set(true);
    try {
      this.invitations.set(await firstValueFrom(this.api.get<Invitation[]>('/my/visitors/invitations', { unitId: this.unitFilter })));
    } catch (error) {
      this.toast.error(describeError(error));
      this.invitations.set([]);
    } finally {
      this.loadingInvitations.set(false);
    }
  }

  async loadRequests(): Promise<void> {
    this.loadingRequests.set(true);
    try {
      this.pending.set(await firstValueFrom(this.api.get<Visit[]>('/my/visitors/requests', { status: 'PENDING_AUTH' })));
    } catch (error) {
      this.toast.error(describeError(error));
      this.pending.set([]);
    } finally {
      this.loadingRequests.set(false);
    }
  }

  async loadInside(): Promise<void> {
    try {
      this.inside.set(await firstValueFrom(this.api.get<Visit[]>('/my/visitors/requests', { status: 'INSIDE' })));
    } catch {
      this.inside.set([]);
    }
  }

  openCreate(): void {
    this.form = this.emptyForm();
    if (this.units().length === 1) this.form.unitId = this.units()[0].unitId ?? '';
    this.validFrom = this.localDateTime(0);
    this.validTo = this.localDateTime(1);
    this.formError.set('');
    this.createOpen.set(true);
  }

  async create(): Promise<void> {
    this.formError.set('');
    if (!this.form.unitId || !this.form.visitorName.trim()) {
      this.formError.set('Selecciona el inmueble e indica el nombre del visitante.');
      return;
    }
    if (!this.validFrom || !this.validTo) {
      this.formError.set('Indica la vigencia de la invitación.');
      return;
    }
    const body: InvitationRequest = {
      unitId: this.form.unitId,
      visitorName: this.form.visitorName.trim(),
      documentNumber: this.form.documentNumber?.trim() || undefined,
      phone: this.form.phone?.trim() || undefined,
      plate: this.form.plate?.trim().toUpperCase() || undefined,
      peopleCount: Number(this.form.peopleCount || 1),
      maxEntries: Number(this.form.maxEntries || 1),
      validFrom: toIsoInstant(this.validFrom),
      validTo: toIsoInstant(this.validTo) ?? '',
      notes: this.form.notes?.trim() || undefined
    };
    this.saving.set(true);
    try {
      const result = await firstValueFrom(this.api.post<InvitationCreated>('/my/visitors/invitations', body));
      this.createOpen.set(false);
      await this.showQr(result);
      this.toast.success('Invitación creada.');
      await this.loadInvitations();
    } catch (error) {
      this.formError.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async regenerate(invitation: Invitation): Promise<void> {
    if (!invitation.id) return;
    try {
      const result = await firstValueFrom(this.api.post<InvitationCreated>(`/my/visitors/invitations/${invitation.id}/regenerate-qr`));
      await this.showQr(result);
      this.toast.success('Código QR regenerado. El anterior quedó invalidado.');
      await this.loadInvitations();
    } catch (error) {
      this.toast.error(describeError(error));
    }
  }

  async cancel(invitation: Invitation): Promise<void> {
    if (!invitation.id) return;
    const accepted = await this.confirm.confirm({
      title: 'Cancelar invitación',
      message: `¿Deseas cancelar la invitación de ${invitation.visitorName}? El código QR dejará de ser válido.`,
      confirmLabel: 'Cancelar invitación',
      danger: true
    });
    if (!accepted) return;
    try {
      await firstValueFrom(this.api.post<void>(`/my/visitors/invitations/${invitation.id}/cancel`));
      this.toast.success('Invitación cancelada.');
      await this.loadInvitations();
    } catch (error) {
      this.toast.error(describeError(error));
    }
  }

  openDecision(visit: Visit, type: 'authorize' | 'reject'): void {
    this.selectedRequest.set(visit);
    this.decisionType.set(type);
    this.decisionNote = '';
    this.decisionOpen.set(true);
  }

  async submitDecision(): Promise<void> {
    const visit = this.selectedRequest();
    if (!visit?.id) return;
    this.acting.set(visit.id);
    const body: DecisionRequest = { note: this.decisionNote.trim() || undefined };
    try {
      await firstValueFrom(this.api.post<Visit>(`/my/visitors/requests/${visit.id}/${this.decisionType()}`, body));
      this.toast.success(this.decisionType() === 'authorize' ? 'Ingreso autorizado.' : 'Ingreso rechazado.');
      this.decisionOpen.set(false);
      await Promise.all([this.loadRequests(), this.loadInside()]);
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.acting.set('');
    }
  }

  downloadQr(): void {
    if (!this.qrImage()) return;
    const anchor = document.createElement('a');
    anchor.href = this.qrImage();
    anchor.download = `invitacion-${(this.qrInvitation()?.visitorName ?? 'visitante').replace(/[^\p{L}\p{N}]+/gu, '-').toLowerCase()}.png`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
  }

  copyToken(): void {
    if (!this.qrToken) return;
    void navigator.clipboard.writeText(this.qrToken).then(
      () => this.toast.success('Código copiado.'),
      () => this.toast.warning('No fue posible copiar el código.')
    );
  }

  private async showQr(result: InvitationCreated): Promise<void> {
    this.qrInvitation.set(result.invitation ?? null);
    this.qrToken = result.qrToken ?? '';
    this.qrImage.set('');
    this.qrOpen.set(true);
    if (!this.qrToken) return;
    try {
      this.qrImage.set(await toQrDataUrl(this.qrToken));
    } catch {
      this.toast.error('No fue posible generar la imagen del código QR.');
    }
  }

  private emptyForm(): InvitationRequest {
    return {
      unitId: '',
      visitorName: '',
      documentNumber: '',
      phone: '',
      plate: '',
      peopleCount: 1,
      maxEntries: 1,
      validTo: '',
      notes: ''
    };
  }

  private localDateTime(daysFromNow: number): string {
    const date = new Date();
    date.setDate(date.getDate() + daysFromNow);
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }
}
