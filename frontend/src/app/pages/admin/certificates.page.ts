import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService, saveBlob } from '../../core/api.service';
import { label, statusTone } from '../../core/labels';
import { IssuedCertificate, PageResultPropertyUnit, PropertyUnit } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { endOfMonth, startOfMonth } from '../../core/utils';
import { EmptyStateComponent, PageHeaderComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-certificates-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Certificados" subtitle="Emite estados de cuenta, paz y salvo y certificados de propiedad con código público de verificación." />

    <div class="grid grid-2">
      <div class="card">
        <div class="card-header"><div><h2>Emitir certificado</h2><p>Selecciona el inmueble y el tipo de documento.</p></div></div>
        <div class="card-body">
          <div class="form-grid">
            <div class="field span-2">
              <label for="unit">Inmueble</label>
              <select id="unit" name="unit" class="select" [(ngModel)]="unitId" (change)="loadHistory()">
                <option value="">Selecciona un inmueble</option>
                @for (unit of units(); track unit.id) { <option [value]="unit.id">{{ unit.identifier }} · {{ unit.tower || 'Sin torre' }}</option> }
              </select>
            </div>
            <div class="field"><label for="from">Desde (estado de cuenta)</label><input id="from" name="from" type="date" class="input" [(ngModel)]="from"></div>
            <div class="field"><label for="to">Hasta (estado de cuenta)</label><input id="to" name="to" type="date" class="input" [(ngModel)]="to"></div>
          </div>
          <div class="grid grid-3" style="margin-top:16px">
            <button type="button" class="btn btn-secondary" style="min-height:74px;flex-direction:column;gap:5px" [disabled]="!unitId || generating()" (click)="generate('statement')">
              <span style="font-weight:700">Estado de cuenta</span><span class="muted small">PDF con movimientos y saldos</span>
            </button>
            <button type="button" class="btn btn-secondary" style="min-height:74px;flex-direction:column;gap:5px" [disabled]="!unitId || generating()" (click)="generate('paz-y-salvo')">
              <span style="font-weight:700">Paz y salvo</span><span class="muted small">Certifica obligaciones al día</span>
            </button>
            <button type="button" class="btn btn-secondary" style="min-height:74px;flex-direction:column;gap:5px" [disabled]="!unitId || generating()" (click)="generate('ownership')">
              <span style="font-weight:700">Propiedad</span><span class="muted small">Titularidad registrada</span>
            </button>
          </div>
          @if (generating()) { <div class="loading"><span class="spinner"></span> Generando documento…</div> }
          <div class="notice" style="margin-top:16px">Cada certificado recibe un código de verificación público. Compártelo con el beneficiario para que pueda validarlo en <strong>/verificar-certificado</strong>.</div>
        </div>
      </div>

      <div class="card">
        <div class="card-header"><div><h2>Certificados emitidos</h2><p>Historial del inmueble seleccionado.</p></div><button type="button" class="btn btn-secondary btn-sm" [disabled]="!unitId" (click)="loadHistory()">Actualizar</button></div>
        @if (!unitId) {
          <app-empty-state symbol="▤" title="Selecciona un inmueble" message="El historial de certificados se muestra por inmueble." />
        } @else if (loadingHistory()) {
          <div class="loading"><span class="spinner"></span> Cargando historial…</div>
        } @else if (!history().length) {
          <app-empty-state symbol="▤" title="Sin certificados" message="Aún no se han emitido certificados para este inmueble." />
        } @else {
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Tipo</th><th>Emisión</th><th>Beneficiario</th><th>Código</th><th>Estado</th><th></th></tr></thead>
              <tbody>
                @for (certificate of history(); track certificate.id) {
                  <tr>
                    <td class="main-cell">{{ label(certificate.certificateType) }}</td>
                    <td>{{ certificate.issuedAt | date:'dd/MM/yyyy HH:mm' }}</td>
                    <td>{{ certificate.recipientName }}</td>
                    <td class="mono" style="font-size:11px">{{ certificate.verificationCode }}</td>
                    <td><span class="badge" [class]="'badge ' + tone(certificate.status)">{{ label(certificate.status) }}</span></td>
                    <td>
                      <div class="table-actions">
                        <button type="button" class="btn-icon" title="Copiar enlace" (click)="copyLink(certificate)">⧉</button>
                        <a class="btn-icon" title="Verificar" [href]="'/verificar-certificado/' + certificate.verificationCode" target="_blank" rel="noopener">↗</a>
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    </div>
  `
})
export class CertificatesPage {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly units = signal<PropertyUnit[]>([]);
  readonly history = signal<IssuedCertificate[]>([]);
  readonly loadingHistory = signal(false);
  readonly generating = signal(false);
  unitId = '';
  from = startOfMonth();
  to = endOfMonth();

  constructor() {
    void this.loadUnits();
  }

  async loadUnits(): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.get<PageResultPropertyUnit>('/properties', { size: 200 }));
      this.units.set(result.content ?? []);
    } catch {
      // El interceptor global informa el error.
    }
  }

  async loadHistory(): Promise<void> {
    if (!this.unitId) {
      this.history.set([]);
      return;
    }
    this.loadingHistory.set(true);
    try {
      const result = await firstValueFrom(this.api.get<IssuedCertificate[]>(`/certificates/units/${this.unitId}/history`));
      this.history.set(result);
    } finally {
      this.loadingHistory.set(false);
    }
  }

  async generate(type: 'statement' | 'paz-y-salvo' | 'ownership'): Promise<void> {
    if (!this.unitId) return;
    this.generating.set(true);
    try {
      const params = type === 'statement' ? { from: this.from, to: this.to } : undefined;
      const response = await firstValueFrom(this.api.download(`/certificates/units/${this.unitId}/${type}`, params, `${type}.pdf`));
      saveBlob(response, `${type}-${this.unitId.slice(0, 8)}.pdf`);
      this.toast.success('Certificado generado y descargado.');
      await this.loadHistory();
    } finally {
      this.generating.set(false);
    }
  }

  copyLink(certificate: IssuedCertificate): void {
    const url = `${window.location.origin}/verificar-certificado/${certificate.verificationCode}`;
    void navigator.clipboard.writeText(url).then(
      () => this.toast.success('Enlace de verificación copiado.'),
      () => this.toast.warning('No fue posible copiar el enlace. Puedes abrirlo desde el botón de verificación.')
    );
  }
}
