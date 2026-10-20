import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { label, statusTone } from '../../core/labels';
import {
  Invitation,
  PageResultInvitation,
  PageResultPropertyUnit,
  PageResultVisit,
  PropertyUnit,
  QrValidation,
  Visit,
  WalkInRequest
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { QrScannerComponent } from '../../shared/qr-scanner.component';
import { EmptyStateComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-porter-visitors-page',
  standalone: true,
  imports: [FormsModule, DatePipe, QrScannerComponent, PageHeaderComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Visitantes" subtitle="Valida códigos QR, registra visitantes sin invitación y controla el ingreso y la salida de visitas.">
      <button type="button" class="btn btn-secondary" (click)="refresh()">Actualizar</button>
    </app-page-header>

    <div class="grid grid-2">
      <div class="card">
        <div class="card-header"><div><h2>Validar código QR</h2><p>Escanea la invitación del visitante o digita el código manualmente.</p></div></div>
        <div class="card-body">
          <app-qr-scanner (decoded)="onDecoded($event)" />
          <div class="divider"></div>
          <div class="row" style="align-items:flex-end">
            <div class="field grow" style="flex:1">
              <label for="qrToken">Código de la invitación</label>
              <input id="qrToken" name="qrToken" class="input mono" [(ngModel)]="qrToken" (keyup.enter)="validate()" placeholder="Pega o digita el código QR" autocomplete="off">
            </div>
            <button type="button" class="btn btn-primary" [disabled]="validating() || !qrToken.trim()" (click)="validate()">{{ validating() ? 'Validando…' : 'Validar' }}</button>
          </div>
          @if (validationError()) { <div class="alert alert-error" style="margin-top:14px">{{ validationError() }}</div> }
          @if (validation(); as result) {
            <div class="divider"></div>
            @if (result.valid && result.invitation; as invitation) {
              <div class="alert alert-success">Invitación válida para {{ invitation.visitorName }}.</div>
              <dl class="detail-list" style="margin-top:14px">
                <dt>Inmueble</dt><dd>{{ invitation.unitIdentifier || unitName(invitation.unitId) }}</dd>
                <dt>Documento</dt><dd>{{ invitation.documentNumber || '—' }}</dd>
                <dt>Personas</dt><dd>{{ invitation.peopleCount || 1 }}</dd>
                <dt>Vigencia</dt><dd>{{ invitation.validFrom | date:'dd/MM/yyyy HH:mm' }} a {{ invitation.validTo | date:'dd/MM/yyyy HH:mm' }}</dd>
                <dt>Usos</dt><dd>{{ invitation.usedCount ?? 0 }} de {{ invitation.maxEntries ?? 1 }}</dd>
                <dt>Vehículo</dt><dd>{{ invitation.plate || 'Sin placa' }}</dd>
              </dl>
              <div class="form-actions">
                <button type="button" class="btn btn-ghost" (click)="clearValidation()">Limpiar</button>
                <button type="button" class="btn btn-primary" [disabled]="checkingIn()" (click)="checkInQr()">{{ checkingIn() ? 'Registrando…' : 'Registrar ingreso' }}</button>
              </div>
            } @else {
              <div class="alert alert-error">{{ result.reason || 'El código QR no es utilizable.' }}</div>
              @if (result.invitation; as invitation) {
                <dl class="detail-list" style="margin-top:14px">
                  <dt>Visitante</dt><dd>{{ invitation.visitorName }}</dd>
                  <dt>Inmueble</dt><dd>{{ invitation.unitIdentifier || unitName(invitation.unitId) }}</dd>
                  <dt>Vigencia</dt><dd>{{ invitation.validFrom | date:'dd/MM/yyyy HH:mm' }} a {{ invitation.validTo | date:'dd/MM/yyyy HH:mm' }}</dd>
                </dl>
              }
            }
          }
        </div>
      </div>

      <div class="card">
        <div class="card-header"><div><h2>Visitante sin invitación</h2><p>Registra la solicitud para que el residente autorice o rechace el ingreso.</p></div></div>
        <div class="card-body">
          @if (walkInError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ walkInError() }}</div> }
          @if (walkInSuccess()) { <div class="alert alert-success" style="margin-bottom:14px">{{ walkInSuccess() }}</div> }
          <form (ngSubmit)="walkIn()" novalidate>
            <div class="form-grid">
              <div class="field span-2">
                <label for="walkUnit">Inmueble *</label>
                <select id="walkUnit" name="walkUnit" class="select" required [(ngModel)]="walkForm.unitId">
                  <option value="">Selecciona el inmueble</option>
                  @for (unit of units(); track unit.id) { <option [value]="unit.id">{{ unit.identifier }} · {{ unit.tower || 'Sin torre' }}</option> }
                </select>
              </div>
              <div class="field span-2"><label for="walkName">Nombre del visitante *</label><input id="walkName" name="walkName" class="input" required maxlength="200" [(ngModel)]="walkForm.visitorName"></div>
              <div class="field"><label for="walkDocument">Documento</label><input id="walkDocument" name="walkDocument" class="input" maxlength="30" [(ngModel)]="walkForm.documentNumber"></div>
              <div class="field"><label for="walkPhone">Teléfono</label><input id="walkPhone" name="walkPhone" class="input" maxlength="40" [(ngModel)]="walkForm.phone"></div>
              <div class="field"><label for="walkPlate">Placa</label><input id="walkPlate" name="walkPlate" class="input mono" maxlength="12" [(ngModel)]="walkForm.plate" style="text-transform:uppercase"></div>
              <div class="field"><label for="walkPeople">Personas</label><input id="walkPeople" name="walkPeople" type="number" min="1" max="50" class="input" [(ngModel)]="walkForm.peopleCount"></div>
              <div class="field span-2"><label for="walkNote">Observación</label><textarea id="walkNote" name="walkNote" class="textarea" maxlength="300" [(ngModel)]="walkForm.note"></textarea></div>
            </div>
            <div class="form-actions">
              <button type="submit" class="btn btn-primary" [disabled]="walkingIn()">{{ walkingIn() ? 'Registrando…' : 'Solicitar autorización' }}</button>
            </div>
          </form>
        </div>
      </div>
    </div>

    <div class="grid grid-2">
      <div class="card">
        <div class="card-header"><div><h2>Visitas dentro</h2><p>Visitantes que ingresaron y aún no han salido.</p></div><span class="badge info">{{ inside().length }}</span></div>
        @if (!inside().length) {
          <app-empty-state symbol="⇄" title="Sin visitantes dentro" message="No hay visitantes registrados dentro de la copropiedad." />
        } @else {
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Visitante</th><th>Inmueble</th><th>Ingreso</th><th style="width:90px"></th></tr></thead>
              <tbody>
                @for (visit of inside(); track visit.id) {
                  <tr>
                    <td><div class="main-cell">{{ visit.visitorName }}</div><div class="muted small">{{ visit.documentNumber || 'Sin documento' }}{{ visit.plate ? ' · ' + visit.plate : '' }}</div></td>
                    <td>{{ visit.unitIdentifier || unitName(visit.unitId) }}</td>
                    <td>{{ visit.entryTime | date:'dd/MM HH:mm' }}</td>
                    <td><button type="button" class="btn btn-secondary btn-sm" [disabled]="acting() === visit.id" (click)="checkOut(visit)">Salida</button></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>

      <div class="card">
        <div class="card-header"><div><h2>Visitas autorizadas</h2><p>Pueden ingresar cuando se presenten en portería.</p></div><span class="badge warning">{{ authorized().length }}</span></div>
        @if (!authorized().length) {
          <app-empty-state symbol="✓" title="Sin autorizaciones pendientes" message="Las visitas autorizadas por los residentes aparecerán aquí." />
        } @else {
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Visitante</th><th>Inmueble</th><th>Autorizada</th><th style="width:90px"></th></tr></thead>
              <tbody>
                @for (visit of authorized(); track visit.id) {
                  <tr>
                    <td><div class="main-cell">{{ visit.visitorName }}</div><div class="muted small">{{ visit.source === 'WALK_IN' ? 'Sin invitación' : 'Invitación' }}</div></td>
                    <td>{{ visit.unitIdentifier || unitName(visit.unitId) }}</td>
                    <td>{{ visit.decidedAt | date:'dd/MM HH:mm' }}</td>
                    <td><button type="button" class="btn btn-primary btn-sm" [disabled]="acting() === visit.id" (click)="checkIn(visit)">Ingreso</button></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    </div>

    <div class="card">
      <div class="card-header">
        <div><h2>Solicitudes pendientes de autorización</h2><p>Visitantes sin invitación que esperan la decisión del residente.</p></div>
        <span class="badge warning">{{ pending().length }}</span>
      </div>
      @if (!pending().length) {
        <app-empty-state symbol="⌛" title="Sin solicitudes pendientes" message="No hay visitantes esperando autorización del residente." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Solicitado</th><th>Visitante</th><th>Inmueble</th><th>Personas</th><th>Nota</th></tr></thead>
            <tbody>
              @for (visit of pending(); track visit.id) {
                <tr>
                  <td class="nowrap">{{ visit.requestedAt | date:'dd/MM HH:mm' }}</td>
                  <td class="main-cell">{{ visit.visitorName }}</td>
                  <td>{{ visit.unitIdentifier || unitName(visit.unitId) }}</td>
                  <td>{{ visit.peopleCount || 1 }}</td>
                  <td>{{ visit.note || '—' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>

    <div class="card">
      <div class="card-header">
        <div><h2>Invitaciones vigentes</h2><p>Códigos QR habilitados en este momento.</p></div>
        <div class="row">
          <button type="button" class="btn btn-secondary btn-sm" (click)="loadInvitations(page())">Actualizar</button>
        </div>
      </div>
      @if (loadingInvitations()) {
        <div class="loading"><span class="spinner"></span> Cargando invitaciones…</div>
      } @else if (!invitations().length) {
        <app-empty-state symbol="▣" title="Sin invitaciones vigentes" message="No hay invitaciones activas para el rango de fechas actual." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Visitante</th><th>Inmueble</th><th>Vigencia</th><th>Usos</th><th>Vehículo</th><th>Estado</th></tr></thead>
            <tbody>
              @for (invitation of invitations(); track invitation.id) {
                <tr>
                  <td class="main-cell">{{ invitation.visitorName }}</td>
                  <td>{{ invitation.unitIdentifier || unitName(invitation.unitId) }}</td>
                  <td class="nowrap">{{ invitation.validFrom | date:'dd/MM HH:mm' }} a {{ invitation.validTo | date:'dd/MM HH:mm' }}</td>
                  <td>{{ invitation.usedCount ?? 0 }} / {{ invitation.maxEntries ?? 1 }}</td>
                  <td class="mono">{{ invitation.plate || '—' }}</td>
                  <td><span class="badge" [class]="'badge ' + tone(invitation.status)">{{ label(invitation.status) }}</span></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-pagination [page]="page()" [totalPages]="totalPages()" [totalElements]="totalElements()" (pageChange)="loadInvitations($event)" />
      }
    </div>
  `
})
export class PorterVisitorsPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly units = signal<PropertyUnit[]>([]);
  readonly inside = signal<Visit[]>([]);
  readonly authorized = signal<Visit[]>([]);
  readonly pending = signal<Visit[]>([]);
  readonly invitations = signal<Invitation[]>([]);
  readonly validation = signal<QrValidation | null>(null);
  readonly validationError = signal('');
  readonly walkInError = signal('');
  readonly walkInSuccess = signal('');
  readonly validating = signal(false);
  readonly checkingIn = signal(false);
  readonly walkingIn = signal(false);
  readonly acting = signal('');
  readonly loadingInvitations = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  qrToken = '';
  walkForm: WalkInRequest = this.emptyWalkIn();

  constructor() {
    void this.refresh();
    void this.loadUnits();
  }

  async refresh(): Promise<void> {
    await Promise.all([
      this.loadVisits(),
      this.loadInvitations(0)
    ]);
  }

  async loadUnits(): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.get<PageResultPropertyUnit>('/properties', { size: 500 }));
      this.units.set(result.content ?? []);
    } catch {
      this.units.set([]);
    }
  }

  async loadVisits(): Promise<void> {
    try {
      const [inside, authorized, pending] = await Promise.all([
        firstValueFrom(this.api.get<PageResultVisit>('/visitors/visits', { status: 'INSIDE', size: 50 })),
        firstValueFrom(this.api.get<PageResultVisit>('/visitors/visits', { status: 'AUTHORIZED', size: 50 })),
        firstValueFrom(this.api.get<PageResultVisit>('/visitors/visits', { status: 'PENDING_AUTH', size: 50 }))
      ]);
      this.inside.set(inside.content ?? []);
      this.authorized.set(authorized.content ?? []);
      this.pending.set(pending.content ?? []);
    } catch {
      this.inside.set([]);
      this.authorized.set([]);
      this.pending.set([]);
    }
  }

  async loadInvitations(page = 0): Promise<void> {
    this.loadingInvitations.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultInvitation>('/visitors/invitations', { validNow: true, page, size: 20 }));
      this.invitations.set(result.content ?? []);
      this.page.set(result.page ?? 0);
      this.totalPages.set(result.totalPages ?? 0);
      this.totalElements.set(result.totalElements ?? 0);
    } catch {
      this.invitations.set([]);
    } finally {
      this.loadingInvitations.set(false);
    }
  }

  unitName(id: string | undefined): string {
    return this.units().find((unit) => unit.id === id)?.identifier ?? '—';
  }

  onDecoded(token: string): void {
    this.qrToken = token;
    this.validationError.set('');
    void this.validate();
  }

  clearValidation(): void {
    this.validation.set(null);
    this.validationError.set('');
    this.qrToken = '';
  }

  async validate(): Promise<void> {
    const token = this.qrToken.trim();
    if (!token) return;
    this.validating.set(true);
    this.validationError.set('');
    this.validation.set(null);
    try {
      this.validation.set(await firstValueFrom(this.api.post<QrValidation>('/visitors/validate-qr', { token })));
    } catch (error) {
      this.validationError.set(describeError(error));
    } finally {
      this.validating.set(false);
    }
  }

  async checkInQr(): Promise<void> {
    const token = this.qrToken.trim();
    if (!token) return;
    this.checkingIn.set(true);
    try {
      await firstValueFrom(this.api.post<Visit>('/visitors/check-in-qr', { token }));
      this.toast.success('Ingreso registrado con el código QR.');
      this.clearValidation();
      await this.loadVisits();
    } catch (error) {
      this.validationError.set(describeError(error));
    } finally {
      this.checkingIn.set(false);
    }
  }

  async walkIn(): Promise<void> {
    this.walkInError.set('');
    this.walkInSuccess.set('');
    if (!this.walkForm.unitId || !this.walkForm.visitorName.trim()) {
      this.walkInError.set('Selecciona el inmueble e indica el nombre del visitante.');
      return;
    }
    this.walkingIn.set(true);
    try {
      const body: WalkInRequest = {
        unitId: this.walkForm.unitId,
        visitorName: this.walkForm.visitorName.trim(),
        documentNumber: this.walkForm.documentNumber?.trim() || undefined,
        phone: this.walkForm.phone?.trim() || undefined,
        plate: this.walkForm.plate?.trim().toUpperCase() || undefined,
        peopleCount: Number(this.walkForm.peopleCount || 1),
        note: this.walkForm.note?.trim() || undefined
      };
      await firstValueFrom(this.api.post<Visit>('/visitors/walk-in', body));
      this.walkForm = this.emptyWalkIn();
      this.walkInSuccess.set('Solicitud registrada. El residente recibirá la notificación para autorizar o rechazar el ingreso.');
      await this.loadVisits();
    } catch (error) {
      this.walkInError.set(describeError(error));
    } finally {
      this.walkingIn.set(false);
    }
  }

  async checkIn(visit: Visit): Promise<void> {
    if (!visit.id) return;
    this.acting.set(visit.id);
    try {
      await firstValueFrom(this.api.post<Visit>(`/visitors/visits/${visit.id}/check-in`));
      this.toast.success('Ingreso registrado.');
      await this.loadVisits();
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.acting.set('');
    }
  }

  async checkOut(visit: Visit): Promise<void> {
    if (!visit.id) return;
    this.acting.set(visit.id);
    try {
      await firstValueFrom(this.api.post<Visit>(`/visitors/visits/${visit.id}/check-out`));
      this.toast.success('Salida registrada.');
      await this.loadVisits();
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.acting.set('');
    }
  }

  private emptyWalkIn(): WalkInRequest {
    return {
      unitId: '',
      visitorName: '',
      documentNumber: '',
      phone: '',
      plate: '',
      peopleCount: 1,
      note: ''
    };
  }
}
