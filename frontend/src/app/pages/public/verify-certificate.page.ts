import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { BrandingService } from '../../core/branding.service';
import { CertificateVerification } from '../../core/models';
import { describeError } from '../../core/utils';
import { label, statusTone } from '../../core/labels';

@Component({
  selector: 'app-verify-certificate-page',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe],
  template: `
    <div class="public-page">
      <header class="public-top">
        <div class="brand-mark" style="width:36px;height:36px;border-radius:10px;font-size:15px">{{ initial() }}</div>
        <div>
          <div style="font-weight:700;font-size:14px">{{ branding.appName() }}</div>
          <div style="color:#94a3b8;font-size:11px">{{ branding.company() }}</div>
        </div>
        <a routerLink="/login" style="margin-left:auto;color:#99f6e4;font-size:12.5px">Iniciar sesión</a>
      </header>
      <main class="public-main">
        <div class="page-head" style="margin-bottom:18px">
          <div>
            <h1>Verificación de certificados</h1>
            <p>Comprueba la autenticidad de un certificado emitido por la copropiedad usando su código de verificación.</p>
          </div>
        </div>

        <div class="card">
          <div class="card-body">
            <form (ngSubmit)="verify()" novalidate>
              <div class="field">
                <label for="code">Código de verificación</label>
                <div class="row" style="flex-wrap:nowrap">
                  <input id="code" name="code" class="input mono" required [(ngModel)]="form.code" placeholder="Ej. VC-XXXX-XXXX-XXXX" autocomplete="off">
                  <button type="submit" class="btn btn-primary" [disabled]="loading()">
                    @if (loading()) { <span class="spinner" style="border-color:rgba(255,255,255,.4);border-top-color:#fff"></span> }
                    Verificar
                  </button>
                </div>
              </div>
            </form>
            @if (error()) { <div class="alert alert-error" style="margin-top:14px">{{ error() }}</div> }
          </div>
        </div>

        @if (result()) {
          <div class="card" style="margin-top:18px">
            <div class="card-header">
              <div>
                <h2>Certificado verificado</h2>
                <p>La información coincide con un certificado emitido por la copropiedad.</p>
              </div>
              <span class="badge" [class]="'badge ' + tone(result()!.status)">{{ label(result()!.status) }}</span>
            </div>
            <div class="card-body">
              <dl class="detail-list">
                <dt>Copropiedad</dt><dd>{{ result()!.tenantName }}</dd>
                <dt>Tipo de certificado</dt><dd>{{ label(result()!.certificateType) }}</dd>
                <dt>Inmueble</dt><dd>{{ result()!.unitIdentifier }}</dd>
                <dt>Beneficiario</dt><dd>{{ result()!.recipientName }}</dd>
                <dt>Fecha de emisión</dt><dd>{{ result()!.issuedAt | date:'dd/MM/yyyy HH:mm' }}</dd>
                <dt>Código</dt><dd class="mono">{{ result()!.verificationCode }}</dd>
                <dt>Huella de contenido</dt><dd class="mono" style="font-size:11px;word-break:break-all">{{ result()!.contentHash }}</dd>
              </dl>
              <div class="notice" style="margin-top:16px">Este resultado confirma que el certificado fue registrado por la copropiedad. Para trámites formales, conserva el documento original y su código de verificación.</div>
            </div>
          </div>
        }
      </main>
      <footer class="public-footer">© {{ year }} {{ branding.company() || branding.appName() }}</footer>
    </div>
  `
})
export class VerifyCertificatePage {
  readonly branding = inject(BrandingService);
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  readonly year = new Date().getFullYear();
  readonly loading = signal(false);
  readonly error = signal('');
  readonly result = signal<CertificateVerification | null>(null);
  readonly label = label;
  readonly tone = statusTone;
  form = { code: '' };

  constructor() {
    const code = this.route.snapshot.paramMap.get('code') ?? '';
    this.form.code = code;
    if (code) void this.verify();
  }

  initial(): string {
    return this.branding.appName().trim().charAt(0).toUpperCase() || 'V';
  }

  async verify(): Promise<void> {
    const code = this.form.code.trim();
    this.error.set('');
    this.result.set(null);
    if (!code) {
      this.error.set('Ingresa el código de verificación del certificado.');
      return;
    }
    this.loading.set(true);
    try {
      const result = await new Promise<CertificateVerification>((resolve, reject) => {
        this.api.get<CertificateVerification>(`/public/certificates/verify/${encodeURIComponent(code)}`).subscribe({ next: resolve, error: reject });
      });
      this.result.set(result);
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }
}
