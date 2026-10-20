import { Component, OnDestroy, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { BrandingService } from '../../core/branding.service';
import { OnlinePaymentIntent } from '../../core/models';
import { label, statusTone } from '../../core/labels';
import { describeError } from '../../core/utils';
import { MoneyPipe } from '../../core/format.pipes';

@Component({
  selector: 'app-wompi-result-page',
  standalone: true,
  imports: [RouterLink, DatePipe, MoneyPipe],
  template: `
    <div class="public-page">
      <header class="public-top">
        <div class="brand-mark" style="width:36px;height:36px;border-radius:10px;font-size:15px">{{ initial() }}</div>
        <div>
          <div style="font-weight:700;font-size:14px">{{ branding.appName() }}</div>
          <div style="color:#94a3b8;font-size:11px">{{ branding.company() }}</div>
        </div>
        <a routerLink="/app/inicio" style="margin-left:auto;color:#99f6e4;font-size:12.5px">Ir al portal</a>
      </header>
      <main class="public-main">
        <div class="card">
          <div class="card-body" style="text-align:center;padding:34px 24px">
            @if (loading()) {
              <div class="spinner lg" style="margin:0 auto 16px"></div>
              <h1 style="font-size:21px">Consultando el resultado del pago…</h1>
              <p class="muted" style="margin-top:8px;font-size:13px">Esto puede tardar unos segundos mientras la pasarela confirma la transacción.</p>
            } @else if (!auth.isAuthenticated()) {
              <div style="font-size:40px;margin-bottom:10px">🔒</div>
              <h1 style="font-size:21px">Inicia sesión para consultar el pago</h1>
              <p class="muted" style="margin:8px auto 18px;max-width:420px;font-size:13px">La referencia del pago se conserva en este dispositivo, pero necesitas autenticarte para ver su estado.</p>
              <a class="btn btn-primary" routerLink="/login">Iniciar sesión</a>
            } @else if (error()) {
              <div class="alert alert-error" style="text-align:left">{{ error() }}</div>
              <div class="row" style="justify-content:center;margin-top:16px">
                <button type="button" class="btn btn-secondary" (click)="load()">Reintentar</button>
                <a class="btn btn-primary" routerLink="/app/residente/estado-cuenta">Ir a mi estado de cuenta</a>
              </div>
            } @else if (intent()) {
              <div class="badge" [class]="'badge ' + tone(intent()!.status)" style="font-size:12px;padding:6px 12px">{{ label(intent()!.status) }}</div>
              <h1 style="font-size:24px;margin-top:14px">
                @switch (intent()!.status) {
                  @case ('APPROVED') { Pago aprobado }
                  @case ('DECLINED') { El pago fue rechazado }
                  @case ('VOIDED') { El pago fue anulado }
                  @case ('ERROR') { No fue posible procesar el pago }
                  @default { Pago en proceso }
                }
              </h1>
              <p class="muted" style="margin-top:8px;font-size:13px">
                Referencia <span class="mono">{{ intent()!.reference }}</span>
              </p>
              <div class="stat" style="max-width:320px;margin:20px auto 0;text-align:left">
                <div class="stat-label">Valor</div>
                <div class="stat-value">{{ (intent()!.amountInCents || 0) / 100 | money:intent()!.currency }}</div>
                <div class="stat-hint">{{ intent()!.createdAt | date:'dd/MM/yyyy HH:mm' }}</div>
              </div>
              @if (intent()!.status === 'PENDING') {
                <p class="muted" style="margin-top:16px;font-size:12.5px">La pasarela aún no ha confirmado la transacción. Puedes actualizar el estado en unos segundos.</p>
                <button type="button" class="btn btn-secondary" style="margin-top:12px" (click)="load()">Actualizar estado</button>
              } @else if (intent()!.status === 'APPROVED') {
                <div class="notice" style="margin:18px auto 0;max-width:500px;text-align:left">El pago fue aplicado a tu estado de cuenta. Puedes consultar el detalle y las imputaciones en tu cartera.</div>
                <a class="btn btn-primary" style="margin-top:16px" routerLink="/app/residente/estado-cuenta">Ver estado de cuenta</a>
              } @else {
                <p class="muted" style="margin-top:14px;font-size:12.5px">{{ intent()!.errorMessage || 'Puedes intentar nuevamente desde tu estado de cuenta.' }}</p>
                <a class="btn btn-primary" style="margin-top:16px" routerLink="/app/residente/estado-cuenta">Volver al estado de cuenta</a>
              }
            } @else {
              <h1 style="font-size:21px">No encontramos una referencia de pago</h1>
              <p class="muted" style="margin:8px auto 18px;max-width:420px;font-size:13px">Ingresa a tu estado de cuenta para iniciar un nuevo pago o consultar tus movimientos.</p>
              <a class="btn btn-primary" routerLink="/app/residente/estado-cuenta">Ir a mi estado de cuenta</a>
            }
          </div>
        </div>
      </main>
      <footer class="public-footer">© {{ year }} {{ branding.company() || branding.appName() }}</footer>
    </div>
  `
})
export class WompiResultPage implements OnDestroy {
  readonly auth = inject(AuthService);
  readonly branding = inject(BrandingService);
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private timer: number | null = null;
  private attempts = 0;
  readonly year = new Date().getFullYear();
  readonly loading = signal(true);
  readonly error = signal('');
  readonly intent = signal<OnlinePaymentIntent | null>(null);
  readonly label = label;
  readonly tone = statusTone;

  constructor() {
    void this.load();
  }

  ngOnDestroy(): void {
    if (this.timer !== null) window.clearTimeout(this.timer);
  }

  initial(): string {
    return this.branding.appName().trim().charAt(0).toUpperCase() || 'V';
  }

  async load(): Promise<void> {
    if (!this.auth.isAuthenticated()) {
      this.loading.set(false);
      return;
    }
    const reference = this.route.snapshot.queryParamMap.get('reference')
      ?? sessionStorage.getItem('vecindad.wompi.reference');
    if (!reference) {
      this.loading.set(false);
      return;
    }
    this.error.set('');
    this.loading.set(true);
    try {
      const result = await new Promise<OnlinePaymentIntent>((resolve, reject) => {
        this.api.get<OnlinePaymentIntent>(`/my/payments/online/${encodeURIComponent(reference)}`).subscribe({ next: resolve, error: reject });
      });
      this.intent.set(result);
      if (result.status === 'PENDING' && this.attempts < 5) {
        this.attempts += 1;
        this.scheduleRefresh();
      }
      if (result.status === 'APPROVED') sessionStorage.removeItem('vecindad.wompi.reference');
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  private scheduleRefresh(): void {
    if (this.timer !== null) window.clearTimeout(this.timer);
    this.timer = window.setTimeout(() => void this.load(), 3000);
  }
}
