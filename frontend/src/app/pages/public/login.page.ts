import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { BrandingService } from '../../core/branding.service';
import { describeError } from '../../core/utils';
import { IonIcon } from '@ionic/angular/standalone';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [FormsModule, RouterLink, IonIcon],
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
          <h1>La administración de tu copropiedad, en un solo lugar.</h1>
          <p>Gestiona inmuebles, cartera, visitantes, reservas, comunicados y asambleas con trazabilidad y seguridad para toda la comunidad.</p>
          <div class="auth-points">
            <div class="auth-point"><ion-icon name="shieldCheckmarkOutline"></ion-icon><span>Accesos por rol y permisos auditables</span></div>
            <div class="auth-point"><ion-icon name="peopleOutline"></ion-icon><span>Portería conectada con residentes y administración</span></div>
            <div class="auth-point"><ion-icon name="walletOutline"></ion-icon><span>Estado de cuenta y pagos en línea</span></div>
          </div>
        </div>
        <div class="auth-foot">© {{ year }} {{ branding.company() || branding.appName() }}</div>
      </section>

      <section class="auth-panel">
        <div class="auth-card">
          <h2>Iniciar sesión</h2>
          <p class="lead">Ingresa con el correo registrado por la administración de tu copropiedad.</p>

          @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
          @if (notice()) { <div class="alert alert-success" style="margin-bottom:14px">{{ notice() }}</div> }

          <form (ngSubmit)="submit()" novalidate>
            <div class="stack">
              <div class="field">
                <label for="email">Correo electrónico</label>
                <input id="email" name="email" type="email" class="input" autocomplete="username" required
                       [(ngModel)]="form.email" placeholder="nombre@correo.com">
              </div>
              <div class="field">
                <label for="password">Contraseña</label>
                <div style="position:relative">
                  <input id="password" name="password" [type]="showPassword() ? 'text' : 'password'" class="input"
                         autocomplete="current-password" required [(ngModel)]="form.password" style="padding-right:44px">
                  <button type="button" class="btn-icon" style="position:absolute;right:4px;top:3px" (click)="showPassword.set(!showPassword())"
                          [attr.aria-label]="showPassword() ? 'Ocultar contraseña' : 'Mostrar contraseña'">
                    <ion-icon [name]="showPassword() ? 'eyeOutline' : 'lockClosedOutline'"></ion-icon>
                  </button>
                </div>
              </div>
              <button type="submit" class="btn btn-primary btn-block" [disabled]="loading()">
                @if (loading()) { <span class="spinner" style="border-color:rgba(255,255,255,.4);border-top-color:#fff"></span> }
                {{ loading() ? 'Ingresando…' : 'Ingresar' }}
              </button>
            </div>
          </form>

          <div class="auth-links">
            <a routerLink="/recuperar">¿Olvidaste tu contraseña?</a>
            <a routerLink="/verificar-certificado">Verificar un certificado</a>
          </div>
          <p class="auth-note">El acceso está restringido a usuarios autorizados. Los intentos de inicio de sesión quedan registrados para la seguridad de la copropiedad.</p>
        </div>
      </section>
    </div>
  `
})
export class LoginPage {
  readonly auth = inject(AuthService);
  readonly branding = inject(BrandingService);
  private readonly router = inject(Router);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly showPassword = signal(false);
  readonly year = new Date().getFullYear();
  form = { email: '', password: '' };

  constructor() {
    const navigation = this.router.lastSuccessfulNavigation;
    if (navigation?.finalUrl?.toString().includes('reset=1')) {
      this.notice.set('Tu contraseña fue actualizada. Ya puedes iniciar sesión.');
    }
  }

  initial(): string {
    return this.branding.appName().trim().charAt(0).toUpperCase() || 'V';
  }

  async submit(): Promise<void> {
    this.error.set('');
    if (!this.form.email.trim() || !this.form.password) {
      this.error.set('Ingresa tu correo y contraseña.');
      return;
    }
    this.loading.set(true);
    try {
      const session = await this.auth.login({ email: this.form.email.trim(), password: this.form.password });
      if (session.activeTenantId) {
        await this.router.navigateByUrl(this.auth.homeRoute());
        return;
      }
      if (session.user?.platformRole === 'SUPER_ADMIN_PLATFORM' && !session.tenants?.length) {
        await this.router.navigateByUrl('/app/plataforma');
        return;
      }
      if ((session.tenants?.length ?? 0) === 1) {
        const tenant = session.tenants![0];
        await this.auth.selectTenant(tenant.id!);
        await this.router.navigateByUrl('/app/inicio');
        return;
      }
      if ((session.tenants?.length ?? 0) > 1) {
        await this.router.navigateByUrl('/seleccionar-copropiedad');
        return;
      }
      this.error.set('Tu usuario no tiene una copropiedad activa. Contacta a la administración.');
      await this.auth.logout();
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }
}
