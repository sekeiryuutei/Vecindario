import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { BrandingService } from '../../core/branding.service';
import { describeError } from '../../core/utils';

@Component({
  selector: 'app-forgot-password-page',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <section class="auth-hero">
        <div class="auth-brand">
          <div class="brand-mark">{{ initial() }}</div>
          <div>
            <div class="auth-brand-name">{{ branding.appName() }}</div>
            <div class="auth-brand-company">{{ branding.company() }}</div>
          </div>
        </div>
        <div class="auth-hero-copy">
          <h1>Recupera el acceso de forma segura.</h1>
          <p>Enviaremos un enlace de un solo uso al correo registrado. Por seguridad, el enlace tiene una vigencia limitada y no confirma si el correo existe.</p>
        </div>
        <div class="auth-foot">© {{ year }} {{ branding.company() || branding.appName() }}</div>
      </section>
      <section class="auth-panel">
        <div class="auth-card">
          <h2>Recuperar contraseña</h2>
          <p class="lead">Escribe el correo de tu cuenta y revisa la bandeja de entrada.</p>
          @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
          @if (sent()) {
            <div class="alert alert-success">Si el correo está registrado, recibirás un enlace para restablecer tu contraseña en los próximos minutos. Revisa también la carpeta de correo no deseado.</div>
            <div class="auth-links"><a routerLink="/login">Volver al inicio de sesión</a></div>
          } @else {
            <form (ngSubmit)="submit()" novalidate>
              <div class="stack">
                <div class="field">
                  <label for="email">Correo electrónico</label>
                  <input id="email" name="email" type="email" class="input" autocomplete="email" required [(ngModel)]="email" placeholder="nombre@correo.com">
                </div>
                <button type="submit" class="btn btn-primary btn-block" [disabled]="loading()">
                  @if (loading()) { <span class="spinner" style="border-color:rgba(255,255,255,.4);border-top-color:#fff"></span> }
                  {{ loading() ? 'Enviando…' : 'Enviar enlace de recuperación' }}
                </button>
              </div>
            </form>
            <div class="auth-links"><a routerLink="/login">Volver al inicio de sesión</a></div>
          }
        </div>
      </section>
    </div>
  `
})
export class ForgotPasswordPage {
  readonly branding = inject(BrandingService);
  private readonly auth = inject(AuthService);
  readonly year = new Date().getFullYear();
  readonly loading = signal(false);
  readonly sent = signal(false);
  readonly error = signal('');
  email = '';

  initial(): string {
    return this.branding.appName().trim().charAt(0).toUpperCase() || 'V';
  }

  async submit(): Promise<void> {
    this.error.set('');
    if (!this.email.trim()) {
      this.error.set('Ingresa tu correo electrónico.');
      return;
    }
    this.loading.set(true);
    try {
      await this.auth.forgotPassword({ email: this.email.trim() });
      this.sent.set(true);
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }
}
