import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ConfirmService } from '../../core/confirm.service';
import { label } from '../../core/labels';
import { BoardMember, BoardMemberRequest, BoardPosition, PageResultPerson, Person } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError, today } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-board-page',
  standalone: true,
  imports: [FormsModule, DatePipe, PageHeaderComponent, ModalComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Junta directiva" subtitle="Integrantes vigentes e historial de la junta directiva de la copropiedad.">
      @if (auth.hasPermission('BOARD_MANAGE')) {
        <button type="button" class="btn btn-primary" (click)="openCreate()">Registrar miembro</button>
      }
    </app-page-header>

    <div class="card">
      <div class="card-header">
        <div><h2>Miembros de la junta</h2><p>{{ items().length }} registro(s).</p></div>
        <button type="button" class="btn btn-secondary btn-sm" (click)="load()">Actualizar</button>
      </div>
      @if (loading()) {
        <div class="loading"><span class="spinner"></span> Cargando junta directiva.</div>
      } @else if (!items().length) {
        <app-empty-state symbol="JD" title="Sin integrantes" message="Aún no se ha registrado la junta directiva." />
      } @else {
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr><th>Cargo</th><th>Persona</th><th>Contacto</th><th>Periodo</th><th>Estado</th><th>Notas</th><th style="width:150px"></th></tr>
            </thead>
            <tbody>
              @for (item of items(); track item.id) {
                <tr>
                  <td class="main-cell">{{ label(item.position) }}</td>
                  <td>
                    <div>{{ item.personName }}</div>
                    <div class="muted">{{ item.documentNumber || 'Sin documento' }}</div>
                  </td>
                  <td>
                    <div>{{ item.email || 'Sin correo' }}</div>
                    <div class="muted">{{ item.phone || 'Sin teléfono' }}</div>
                  </td>
                  <td>
                    <div>{{ item.startDate | date:'dd/MM/yyyy' }}</div>
                    <div class="muted">{{ item.endDate ? ('hasta ' + (item.endDate | date:'dd/MM/yyyy')) : 'Vigente' }}</div>
                  </td>
                  <td><span class="badge" [class]="'badge ' + (item.status === 'ACTIVE' ? 'success' : 'neutral')">{{ item.status === 'ACTIVE' ? 'Activo' : 'Inactivo' }}</span></td>
                  <td class="muted">{{ item.notes || '—' }}</td>
                  <td>
                    @if (auth.hasPermission('BOARD_MANAGE')) {
                      <div class="table-actions">
                        <button type="button" class="btn btn-secondary btn-sm" (click)="openEdit(item)">Editar</button>
                        <button type="button" class="btn btn-danger btn-sm" (click)="remove(item)">Eliminar</button>
                      </div>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>

    <app-modal [open]="modalOpen()" [title]="editing() ? 'Editar miembro' : 'Registrar miembro'" subtitle="Cada persona puede tener un solo cargo activo; los vocales son la excepción." (closed)="modalOpen.set(false)">
      @if (error()) { <div class="alert alert-error" style="margin-bottom:14px">{{ error() }}</div> }
      <form (ngSubmit)="save()" novalidate>
        <div class="form-grid">
          <div class="field span-2">
            <label for="boardPerson">Persona *</label>
            <select id="boardPerson" name="boardPerson" class="select" required [(ngModel)]="form.personId">
              <option value="">Selecciona una persona</option>
              @for (person of people(); track person.id) {
                <option [value]="person.id">{{ person.fullName }} — {{ person.documentNumber || 'sin documento' }}</option>
              }
            </select>
          </div>
          <div class="field">
            <label for="boardPosition">Cargo *</label>
            <select id="boardPosition" name="boardPosition" class="select" required [(ngModel)]="form.position">
              @for (option of positions; track option) { <option [value]="option">{{ label(option) }}</option> }
            </select>
          </div>
          <div class="field">
            <label for="boardStatus">Estado</label>
            <select id="boardStatus" name="boardStatus" class="select" [(ngModel)]="form.status">
              <option value="ACTIVE">Activo</option>
              <option value="INACTIVE">Inactivo</option>
            </select>
          </div>
          <div class="field"><label for="boardStart">Inicio *</label><input id="boardStart" name="boardStart" type="date" class="input" required [(ngModel)]="form.startDate"></div>
          <div class="field"><label for="boardEnd">Fin</label><input id="boardEnd" name="boardEnd" type="date" class="input" [(ngModel)]="form.endDate"></div>
          <div class="field span-2"><label for="boardNotes">Notas</label><textarea id="boardNotes" name="boardNotes" class="textarea" rows="3" maxlength="500" [(ngModel)]="form.notes"></textarea></div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="modalOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="saving()">{{ saving() ? 'Guardando…' : 'Guardar' }}</button>
        </div>
      </form>
    </app-modal>
  `
})
export class BoardPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly positions: BoardPosition[] = ['PRESIDENTE', 'VICEPRESIDENTE', 'SECRETARIO', 'TESORERO', 'VOCAL', 'REVISOR_FISCAL'];
  readonly items = signal<BoardMember[]>([]);
  readonly people = signal<Person[]>([]);
  readonly loading = signal(false);
  readonly modalOpen = signal(false);
  readonly saving = signal(false);
  readonly editing = signal<BoardMember | null>(null);
  readonly error = signal('');
  form: BoardMemberRequest = { personId: '', position: 'PRESIDENTE', startDate: today(), status: 'ACTIVE' };

  constructor() {
    void this.load();
    void this.loadPeople();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.items.set(await firstValueFrom(this.api.get<BoardMember[]>('/board')));
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  async loadPeople(): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.get<PageResultPerson>('/residents', { size: 200 }));
      this.people.set(result.content ?? []);
    } catch {
      this.people.set([]);
    }
  }

  openCreate(): void {
    this.editing.set(null);
    this.error.set('');
    this.form = { personId: this.people()[0]?.id ?? '', position: 'PRESIDENTE', startDate: today(), status: 'ACTIVE' };
    this.modalOpen.set(true);
  }

  openEdit(item: BoardMember): void {
    this.editing.set(item);
    this.error.set('');
    this.form = {
      personId: item.personId ?? '',
      position: item.position ?? 'VOCAL',
      startDate: item.startDate ?? today(),
      endDate: item.endDate ?? undefined,
      status: item.status ?? 'ACTIVE',
      notes: item.notes ?? ''
    };
    this.modalOpen.set(true);
  }

  async save(): Promise<void> {
    this.error.set('');
    if (!this.form.personId || !this.form.position || !this.form.startDate) {
      this.error.set('Selecciona la persona, el cargo y la fecha de inicio.');
      return;
    }
    this.saving.set(true);
    try {
      const editing = this.editing();
      if (editing?.id) {
        await firstValueFrom(this.api.put<BoardMember>(`/board/${editing.id}`, this.form));
        this.toast.success('Miembro actualizado.');
      } else {
        await firstValueFrom(this.api.post<BoardMember>('/board', this.form));
        this.toast.success('Miembro registrado.');
      }
      this.modalOpen.set(false);
      await this.load();
    } catch (error) {
      this.error.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async remove(item: BoardMember): Promise<void> {
    if (!item.id) return;
    const ok = await this.confirm.confirm({
      title: 'Eliminar miembro',
      message: `¿Eliminar a ${item.personName} de la junta directiva?`,
      confirmLabel: 'Eliminar',
      danger: true
    });
    if (!ok) return;
    try {
      await firstValueFrom(this.api.delete<void>(`/board/${item.id}`));
      this.toast.success('Miembro eliminado.');
      await this.load();
    } catch (error) {
      this.toast.error(describeError(error));
    }
  }
}
