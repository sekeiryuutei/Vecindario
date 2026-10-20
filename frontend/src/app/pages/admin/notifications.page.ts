import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ConfirmService } from '../../core/confirm.service';
import { label, statusTone } from '../../core/labels';
import {
  NotificationLog,
  PageResultNotificationLog,
  TenantNotificationSettings,
  UpdateSettingsRequest
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, PageHeaderComponent, PaginationComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-notifications-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, PaginationComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Notificaciones" subtitle="Canales de comunicación, recordatorios de cartera y trazabilidad de envíos.">
      <button type="button" class="btn btn-secondary" [disabled]="sendingReminders()" (click)="sendReminders()">
        {{ sendingReminders() ? 'Enviando…' : 'Enviar recordatorios de cartera' }}
      </button>
    </app-page-header>

    <div class="grid grid-2">
      <div class="card">
        <div class="card-header"><div><h2>Canales de notificación</h2><p>Configura los medios habilitados para la copropiedad.</p></div></div>
        <div class="card-body">
          @if (settingsError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ settingsError() }}</div> }
          @if (loadingSettings()) {
            <div class="loading"><span class="spinner"></span> Cargando configuración…</div>
          } @else {
            <form (ngSubmit)="saveSettings()" novalidate>
              <div class="stack">
                <label class="check"><input type="checkbox" [(ngModel)]="form.emailEnabled" name="emailEnabled"> Correo electrónico</label>
                <label class="check"><input type="checkbox" [(ngModel)]="form.pushEnabled" name="pushEnabled"> Notificaciones push</label>
                <label class="check"><input type="checkbox" [(ngModel)]="form.whatsappEnabled" name="whatsappEnabled"> WhatsApp</label>
                <div class="field">
                  <label for="senderDisplayName">Nombre del remitente *</label>
                  <input id="senderDisplayName" name="senderDisplayName" class="input" required maxlength="100" [(ngModel)]="form.senderDisplayName">
                  <span class="hint">Así aparecerá la administración en correos y mensajes.</span>
                </div>
              </div>
              <div class="form-actions">
                <button type="submit" class="btn btn-primary" [disabled]="savingSettings()">Guardar configuración</button>
              </div>
            </form>
          }
        </div>
      </div>

      <div class="card">
        <div class="card-header"><div><h2>Recordatorios de cartera</h2><p>Avisos masivos a propietarios con obligaciones vencidas.</p></div></div>
        <div class="card-body">
          <p class="muted" style="font-size:13px;line-height:1.6">El proceso identifica los inmuebles con cartera vencida y despacha los recordatorios por los canales habilitados. Cada envío queda registrado en el historial inferior.</p>
          @if (reminderResult()) {
            <div class="alert alert-success" style="margin-top:14px">Recordatorios despachados: {{ reminderResult() }}</div>
          }
          <div class="notice" style="margin-top:14px">Los recordatorios respetan las preferencias de canal y se registran como notificación enviada solo cuando el proveedor confirma la entrega.</div>
        </div>
      </div>
    </div>

    <div class="card" style="margin-top:18px">
      <div class="card-header"><div><h2>Historial de notificaciones</h2><p>Resultado de los envíos realizados por la copropiedad.</p></div><button type="button" class="btn btn-secondary btn-sm" (click)="loadLogs(logsPage())">Actualizar</button></div>
      @if (loadingLogs()) {
        <div class="loading"><span class="spinner"></span> Cargando historial…</div>
      } @else if (!logs().length) {
        <app-empty-state symbol="✉" title="Sin notificaciones" message="Aún no se han registrado envíos de notificaciones." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Fecha</th><th>Canal</th><th>Tipo</th><th>Destinatario</th><th>Asunto</th><th>Estado</th></tr></thead>
            <tbody>
              @for (log of logs(); track log.id) {
                <tr>
                  <td>{{ log.createdAt | date:'dd/MM/yyyy HH:mm' }}</td>
                  <td>{{ label(log.channel) }}</td>
                  <td>{{ label(log.type) }}</td>
                  <td><div>{{ log.recipientEmail || log.recipientPhone || '—' }}</div>@if (log.errorDetail) { <div class="muted small" [title]="log.errorDetail">{{ log.errorDetail }}</div> }</td>
                  <td>{{ log.subject || '—' }}</td>
                  <td><span class="badge" [class]="'badge ' + tone(log.status)">{{ label(log.status) }}</span></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-pagination [page]="logsPage()" [totalPages]="logsPages()" [totalElements]="logsTotal()" (pageChange)="loadLogs($event)" />
      }
    </div>
  `
})
export class NotificationsPage {
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly loadingSettings = signal(true);
  readonly savingSettings = signal(false);
  readonly settingsError = signal('');
  readonly logs = signal<NotificationLog[]>([]);
  readonly loadingLogs = signal(false);
  readonly logsPage = signal(0);
  readonly logsPages = signal(0);
  readonly logsTotal = signal(0);
  readonly sendingReminders = signal(false);
  readonly reminderResult = signal<number | null>(null);
  form: UpdateSettingsRequest = { emailEnabled: true, pushEnabled: false, whatsappEnabled: false, senderDisplayName: '' };

  constructor() {
    void this.loadSettings();
    void this.loadLogs(0);
  }

  async loadSettings(): Promise<void> {
    this.loadingSettings.set(true);
    this.settingsError.set('');
    try {
      const settings = await firstValueFrom(this.api.get<TenantNotificationSettings>('/notifications/settings'));
      this.form = {
        emailEnabled: settings.emailEnabled ?? true,
        pushEnabled: settings.pushEnabled ?? false,
        whatsappEnabled: settings.whatsappEnabled ?? false,
        senderDisplayName: settings.senderDisplayName ?? ''
      };
    } catch (error) {
      this.settingsError.set(describeError(error));
    } finally {
      this.loadingSettings.set(false);
    }
  }

  async saveSettings(): Promise<void> {
    this.settingsError.set('');
    if (!this.form.senderDisplayName.trim()) {
      this.settingsError.set('El nombre del remitente es obligatorio.');
      return;
    }
    this.savingSettings.set(true);
    try {
      const updated = await firstValueFrom(this.api.put<TenantNotificationSettings>('/notifications/settings', {
        ...this.form,
        senderDisplayName: this.form.senderDisplayName.trim()
      }));
      this.form = {
        emailEnabled: updated.emailEnabled ?? this.form.emailEnabled,
        pushEnabled: updated.pushEnabled ?? this.form.pushEnabled,
        whatsappEnabled: updated.whatsappEnabled ?? this.form.whatsappEnabled,
        senderDisplayName: updated.senderDisplayName ?? this.form.senderDisplayName
      };
      this.toast.success('Configuración de notificaciones guardada.');
    } catch (error) {
      this.settingsError.set(describeError(error));
    } finally {
      this.savingSettings.set(false);
    }
  }

  async loadLogs(page = 0): Promise<void> {
    this.loadingLogs.set(true);
    try {
      const result = await firstValueFrom(this.api.get<PageResultNotificationLog>('/notifications/logs', { page, size: 20 }));
      this.logs.set(result.content ?? []);
      this.logsPage.set(result.page ?? 0);
      this.logsPages.set(result.totalPages ?? 0);
      this.logsTotal.set(result.totalElements ?? 0);
    } finally {
      this.loadingLogs.set(false);
    }
  }

  async sendReminders(): Promise<void> {
    const accepted = await this.confirm.confirm({
      title: 'Enviar recordatorios',
      message: 'Se enviarán recordatorios a los propietarios con cartera vencida por los canales habilitados. ¿Deseas continuar?',
      confirmLabel: 'Enviar'
    });
    if (!accepted) return;
    this.sendingReminders.set(true);
    try {
      const result = await firstValueFrom(this.api.post<{ status?: string; remindersDispatched?: number }>('/notifications/portfolio-reminders'));
      this.reminderResult.set(result.remindersDispatched ?? 0);
      this.toast.success('Recordatorios despachados.');
      await this.loadLogs(0);
    } finally {
      this.sendingReminders.set(false);
    }
  }
}
