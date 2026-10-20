import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BrandingService } from '../../core/branding.service';

@Component({
  selector: 'app-not-found-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="public-page">
      <header class="public-top">
        <div class="brand-mark" style="width:36px;height:36px;border-radius:10px;font-size:15px">{{ initial() }}</div>
        <div style="font-weight:700;font-size:14px">{{ branding.appName() }}</div>
      </header>
      <main class="public-main" style="display:grid;place-items:center;text-align:center">
        <div>
          <div style="font-size:64px;font-weight:800;color:#0f766e;letter-spacing:-.06em">404</div>
          <h1 style="font-size:24px;margin-top:8px">Página no encontrada</h1>
          <p class="muted" style="margin:10px auto 20px;max-width:420px;font-size:13.5px">La dirección que abriste no existe o cambió de ubicación. Vuelve al portal para continuar.</p>
          <a class="btn btn-primary" routerLink="/login">Volver al portal</a>
        </div>
      </main>
      <footer class="public-footer">© {{ year }} {{ branding.company() || branding.appName() }}</footer>
    </div>
  `
})
export class NotFoundPage {
  readonly branding = inject(BrandingService);
  readonly year = new Date().getFullYear();

  initial(): string {
    return this.branding.appName().trim().charAt(0).toUpperCase() || 'V';
  }
}
