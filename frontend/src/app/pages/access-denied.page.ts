import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-access-denied-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="page-head">
      <div>
        <h1>Acceso restringido</h1>
        <p>Tu rol actual no tiene permisos para consultar esta sección.</p>
      </div>
    </div>
    <div class="card">
      <div class="card-body empty">
        <div style="font-size:38px;margin-bottom:10px">🔐</div>
        <h3>No tienes permisos para esta sección</h3>
        <p>Si necesitas acceso, solicita a la administración de la copropiedad que revise los permisos de tu rol.</p>
        <div class="row" style="justify-content:center;margin-top:18px">
          <a class="btn btn-primary" [routerLink]="auth.homeRoute()">Volver al inicio</a>
          <a class="btn btn-secondary" routerLink="/app/perfil">Mi cuenta</a>
        </div>
      </div>
    </div>
  `
})
export class AccessDeniedPage {
  readonly auth = inject(AuthService);
}
