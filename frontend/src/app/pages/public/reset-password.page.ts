import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { BrandingService } from '../../core/branding.service';
import { describeError } from '../../core/utils';

@Component({
  selector: 'app-reset-password-page',
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
          <h1>Crea una nueva contraseña.</h1>
          <p>Usa al menos 8 caracteres. Te recomendamos combinar mayúsculas, minúsculas, números y símbolos, y no reutilizar contraseñas de otros servicios.</p>
        </div>
        <div class="auth-foot">© {{ year }} {{ branding.company() || branding.appName() }}</div>
      </section>
      <section class="auth-panel">
        <div class="auth-card">
          <h2>Restablecer contraseña</h2>
          @if (!token) {
            <div class="alert alert-error" style="margin-bottom:14px">El enlace no contiene un token válido. Solicita uno nuevo.</div>
            <a class="btn btn-primary" routerLink="/recuperar">Solicitar un nuevo enlace</a>
          } @else if (done()) {
            <div class="alert alert-success">Contraseña actualizada correctamente.</div>
            <div class="auth-links"><a routerLink="/login">Iniciar sesión</a></div>
          } @else {
            <p class="lead">Define la nueva contraseña de tu cuenta.</p>
            @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
            <form (ngSubmit)="submit()" novalidate>
              <div class="stack">
                <div class="field">
                  <label for="password">Nueva contraseña</label>
                  <input id="password" name="password" type="password" class="input" autocomplete="new-password" required [(ngModel)]="password">
                </div>
                <div class="field">
                  <label for="confirm">Confirmar contraseña</label>
                  <input id="confirm" name="confirm" type="password" class="input" autocomplete="new-password" required [(ngModel)]="confirmation">
                </div>
                <button type="submit" class="btn btn-primary btn-block" [disabled]="loading()">
                  @if (loading()) { <span class="spinner" style="border-color:rgba(255,255,255,.4);border-top-color:#fff"></span> }
                  {{ loading() ? 'Guardando…' : 'Guardar contraseña' }}
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
export class ResetPasswordPage {
  readonly branding = inject(BrandingService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly token = this.route.snapshot.queryParamMap.get('token') ?? '';
  readonly year = new Date().getFullYear();
  readonly loading = signal(false);
  readonly done = signal(false);
  readonly error = signal('');
  password = '';
  confirmation = '';

  initial(): string {
    return this.branding.appName().trim().charAt(0).toUpperCase() || 'V';
  }

  async submit(): Promise<void> {
    this.error.set('');
    if (this.password.length < 8) {
      this.error.set('La contraseña debe tener al menos 8 caracteres.');
      return;
    }
    if (this.password !== this.confirmation) {
      this.error.set('Las contraseñas no coinciden.');
      return;
    }
    this.loading.set(true);
    try {
      await this.auth.resetPassword({ token: this.token, newPassword: this.password });
      this.done.set(true);
      window.setTimeout(() => void this.router.navigate(['/login'], { queryParams: { reset: 1 } }), 1600);
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }
}
