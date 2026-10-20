import { Component, inject, signal } from '@angular/core';
import { DatePipe, KeyValuePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { label, statusTone } from '../../core/labels';
import {
  Assembly,
  AssemblyAttendance,
  AssemblyTopic,
  CreateAssemblyRequest,
  CreateTopicRequest,
  PageResultPropertyUnit,
  PropertyUnit,
  QuorumSummary,
  TopicResults
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError, toIsoDateTime } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent } from '../../shared/ui.components';

type AssemblyStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'ADJOURNED' | 'CLOSED';

@Component({
  selector: 'app-assemblies-page',
  standalone: true,
  imports: [FormsModule, DatePipe, KeyValuePipe, PageHeaderComponent, ModalComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Asambleas" subtitle="Convocatorias, quórum, temas, votaciones y resultados de la copropiedad.">
      @if (auth.hasPermission('ASSEMBLIES_MANAGE')) { <button type="button" class="btn btn-primary" (click)="openCreate()">Programar asamblea</button> }
    </app-page-header>

    <div class="grid grid-2">
      @for (assembly of assemblies(); track assembly.id) {
        <div class="card">
          <div class="card-header">
            <div><h2>{{ assembly.title }}</h2><p>{{ label(assembly.type) }} · {{ assembly.scheduledAt | date:'dd/MM/yyyy HH:mm' }}</p></div>
            <span class="badge" [class]="'badge ' + tone(assembly.status)">{{ label(assembly.status) }}</span>
          </div>
          <div class="card-body">
            <dl class="detail-list">
              <dt>Modalidad</dt><dd>{{ assembly.isVirtual ? 'Virtual' : 'Presencial' }}{{ assembly.location ? ' · ' + assembly.location : '' }}</dd>
              <dt>Quórum requerido</dt><dd>{{ assembly.requiredQuorumPercentage ?? 0 }}%</dd>
              @if (assembly.meetingLink) { <dt>Enlace</dt><dd><a [href]="assembly.meetingLink" target="_blank" rel="noopener">Abrir reunión</a></dd> }
            </dl>
            <div class="form-actions">
              <button type="button" class="btn btn-secondary btn-sm" (click)="openDetail(assembly)">Gestionar</button>
            </div>
          </div>
        </div>
      } @empty {
        <div class="card"><app-empty-state symbol="◫" title="Sin asambleas" message="Programa la primera asamblea para iniciar el proceso de participación." /></div>
      }
    </div>

    <app-modal [open]="createOpen()" title="Programar asamblea" subtitle="Define fecha, modalidad y quórum requerido." (closed)="createOpen.set(false)">
      @if (createError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ createError() }}</div> }
      <form (ngSubmit)="create()" novalidate>
        <div class="form-grid">
          <div class="field span-2"><label for="title">Título *</label><input id="title" name="title" class="input" required [(ngModel)]="createForm.title" maxlength="200"></div>
          <div class="field">
            <label for="type">Tipo *</label>
            <select id="type" name="type" class="select" [(ngModel)]="createForm.type"><option value="ORDINARIA">Ordinaria</option><option value="EXTRAORDINARIA">Extraordinaria</option></select>
          </div>
          <div class="field"><label for="scheduledAt">Fecha y hora *</label><input id="scheduledAt" name="scheduledAt" type="datetime-local" class="input" required [(ngModel)]="scheduledAt"></div>
          <div class="field"><label for="location">Lugar</label><input id="location" name="location" class="input" [(ngModel)]="createForm.location" maxlength="200"></div>
          <div class="field"><label for="quorum">Quórum requerido (%)</label><input id="quorum" name="quorum" type="number" min="0.01" max="100" step="0.01" class="input" [(ngModel)]="createForm.requiredQuorumPercentage"></div>
          <div class="field"><label class="check"><input type="checkbox" [(ngModel)]="createForm.isVirtual"> Asamblea virtual</label></div>
          <div class="field"><label for="link">Enlace de la reunión</label><input id="link" name="link" class="input" [(ngModel)]="createForm.meetingLink" maxlength="500"></div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="createOpen.set(false)">Cancelar</button>
          <button type="submit" class="btn btn-primary" [disabled]="saving()">Programar asamblea</button>
        </div>
      </form>
    </app-modal>

    <app-modal [open]="detailOpen()" [title]="selected()?.title || 'Asamblea'" subtitle="Quórum, asistentes, temas y votaciones." [wide]="true" (closed)="detailOpen.set(false)">
      @if (selected(); as assembly) {
        <div class="row between" style="margin-bottom:14px">
          <div class="row" style="gap:8px">
            <span class="badge" [class]="'badge ' + tone(assembly.status)">{{ label(assembly.status) }}</span>
            <span class="muted small">{{ assembly.scheduledAt | date:'dd/MM/yyyy HH:mm' }}</span>
          </div>
          @if (auth.hasPermission('ASSEMBLIES_MANAGE')) {
            <div class="row">
              <select name="assemblyStatus" class="select" style="width:170px" [(ngModel)]="assemblyStatus">
                <option value="SCHEDULED">Programada</option><option value="IN_PROGRESS">En curso</option><option value="ADJOURNED">Aplazada</option><option value="CLOSED">Cerrada</option>
              </select>
              <button type="button" class="btn btn-secondary btn-sm" [disabled]="saving()" (click)="changeStatus()">Cambiar estado</button>
            </div>
          }
        </div>

        @if (quorum(); as q) {
          <div class="grid grid-4" style="margin-bottom:16px">
            <div class="stat"><div class="stat-label">Quórum actual</div><div class="stat-value">{{ q.currentQuorumPercentage ?? 0 }}%</div></div>
            <div class="stat"><div class="stat-label">Requerido</div><div class="stat-value">{{ q.requiredQuorumPercentage ?? 0 }}%</div></div>
            <div class="stat"><div class="stat-label">Inmuebles registrados</div><div class="stat-value">{{ q.registeredUnits ?? 0 }}</div></div>
            <div class="stat"><div class="stat-label">Estado del quórum</div><div class="stat-value" style="font-size:18px">{{ q.hasQuorum ? 'Constituido' : 'Pendiente' }}</div></div>
          </div>
        }

        <div class="grid grid-2">
          <div>
            <h3 class="section-title">Registrar asistencia</h3>
            @if (auth.hasPermission('ASSEMBLIES_MANAGE')) {
              <div class="form-grid">
                <div class="field">
                  <label for="checkUnit">Inmueble *</label>
                  <select id="checkUnit" name="checkUnit" class="select" [(ngModel)]="checkUnitId">
                    <option value="">Selecciona</option>
                    @for (unit of units(); track unit.id) { <option [value]="unit.id">{{ unit.identifier }} · {{ unit.tower || 'Sin torre' }}</option> }
                  </select>
                </div>
                <div class="field"><label for="proxyName">Representante / poder</label><input id="proxyName" name="proxyName" class="input" [(ngModel)]="proxyName"></div>
                <div class="field"><label class="check"><input type="checkbox" [(ngModel)]="isProxy"> Asistencia por poder</label></div>
                <div class="field" style="justify-content:flex-end"><button type="button" class="btn btn-secondary" [disabled]="saving() || !checkUnitId" (click)="checkIn()">Registrar asistencia</button></div>
              </div>
            }
            <h3 class="section-title" style="margin-top:18px">Asistentes</h3>
            <div class="timeline">
              @for (attendance of attendances(); track attendance.unitId + ':' + attendance.checkedInAt) {
                <div class="timeline-item"><span class="timeline-dot"></span><div class="timeline-content"><div style="font-size:13px;font-weight:600">{{ unitName(attendance.unitId) }}</div><div class="muted small">{{ attendance.isProxy ? 'Por poder: ' + (attendance.proxyName || '—') : 'Presencial' }} · {{ attendance.checkedInAt | date:'dd/MM HH:mm' }}</div></div></div>
              } @empty { <p class="muted small">Aún no hay asistentes registrados.</p> }
            </div>
          </div>
          <div>
            <div class="row between"><h3 class="section-title">Temas y votaciones</h3><button type="button" class="btn btn-secondary btn-sm" (click)="openTopic()">Nuevo tema</button></div>
            <div class="stack" style="gap:10px">
              @for (topic of topics(); track topic.id) {
                <div style="border:1px solid var(--line);border-radius:9px;padding:11px">
                  <div class="row between">
                    <div style="font-weight:650;font-size:13px">{{ topic.orderNum }}. {{ topic.title }}</div>
                    <span class="badge" [class]="'badge ' + tone(topic.status)">{{ label(topic.status) }}</span>
                  </div>
                  <div class="muted small" style="margin-top:3px">{{ label(topic.votingType) }}{{ topic.requiresQualifiedMajority ? ' · mayoría calificada' : '' }}</div>
                  @if (topic.description) { <p style="font-size:12.5px;margin-top:6px;white-space:pre-wrap">{{ topic.description }}</p> }
                  <div class="row" style="margin-top:8px;gap:6px">
                    @if (topic.status === 'PENDING' && auth.hasPermission('ASSEMBLIES_MANAGE')) { <button type="button" class="btn btn-primary btn-sm" (click)="openVote(topic)">Abrir votación</button> }
                    @if (topic.status === 'VOTING_OPEN' && auth.hasPermission('ASSEMBLIES_MANAGE')) { <button type="button" class="btn btn-secondary btn-sm" (click)="closeVote(topic)">Cerrar votación</button> }
                    @if (topic.status === 'VOTING_CLOSED') { <button type="button" class="btn btn-secondary btn-sm" (click)="loadResults(topic)">Ver resultados</button> }
                  </div>
                </div>
              } @empty { <p class="muted small">No hay temas registrados.</p> }
            </div>
          </div>
        </div>
      }
    </app-modal>

    <app-modal [open]="topicOpen()" title="Nuevo tema de asamblea" subtitle="Define el punto y las opciones de votación." [narrow]="true" (closed)="topicOpen.set(false)">
      @if (topicError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ topicError() }}</div> }
      <div class="field"><label for="topicTitle">Título *</label><input id="topicTitle" name="topicTitle" class="input" [(ngModel)]="topicForm.title"></div>
      <div class="field" style="margin-top:10px"><label for="topicDescription">Descripción</label><textarea id="topicDescription" name="topicDescription" class="textarea" [(ngModel)]="topicForm.description"></textarea></div>
      <div class="form-grid" style="margin-top:10px">
        <div class="field"><label for="order">Orden</label><input id="order" name="order" type="number" min="1" class="input" [(ngModel)]="topicForm.orderNum"></div>
        <div class="field"><label for="votingType">Tipo de votación</label><select id="votingType" name="votingType" class="select" [(ngModel)]="topicForm.votingType"><option value="NOMINAL">Nominal</option><option value="COEFFICIENT">Por coeficiente</option></select></div>
        <div class="field span-2"><label for="options">Opciones (separadas por coma)</label><input id="options" name="options" class="input" [(ngModel)]="topicOptions"></div>
        <div class="field span-2"><label class="check"><input type="checkbox" [(ngModel)]="topicForm.requiresQualifiedMajority"> Requiere mayoría calificada</label></div>
      </div>
      <div class="form-actions">
        <button type="button" class="btn btn-secondary" (click)="topicOpen.set(false)">Cancelar</button>
        <button type="button" class="btn btn-primary" [disabled]="saving()" (click)="createTopic()">Crear tema</button>
      </div>
    </app-modal>

    <app-modal [open]="resultsOpen()" title="Resultados de votación" [subtitle]="results()?.title || ''" (closed)="resultsOpen.set(false)">
      @if (results(); as result) {
        <div class="grid grid-2">
          <div class="stat"><div class="stat-label">Votos</div><div class="stat-value">{{ result.totalVotes ?? 0 }}</div></div>
          <div class="stat"><div class="stat-label">Peso total</div><div class="stat-value">{{ result.totalWeight ?? 0 }}</div></div>
        </div>
        <div class="table-wrap" style="margin-top:14px">
          <table class="table">
            <thead><tr><th>Opción</th><th class="num">Votos</th><th class="num">Porcentaje</th></tr></thead>
            <tbody>
              @for (entry of result.voteTallies | keyvalue; track entry.key) {
                <tr><td>{{ entry.key }}</td><td class="num">{{ entry.value }}</td><td class="num">{{ result.percentageTallies?.[entry.key] ?? 0 }}%</td></tr>
              }
            </tbody>
          </table>
        </div>
        @if (result.winningOption) { <div class="notice" style="margin-top:14px">Opción ganadora: <strong>{{ result.winningOption }}</strong></div> }
      }
    </app-modal>
  `
})
export class AssembliesPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly assemblies = signal<Assembly[]>([]);
  readonly units = signal<PropertyUnit[]>([]);
  readonly selected = signal<Assembly | null>(null);
  readonly topics = signal<AssemblyTopic[]>([]);
  readonly attendances = signal<AssemblyAttendance[]>([]);
  readonly quorum = signal<QuorumSummary | null>(null);
  readonly results = signal<TopicResults | null>(null);
  readonly detailOpen = signal(false);
  readonly createOpen = signal(false);
  readonly topicOpen = signal(false);
  readonly resultsOpen = signal(false);
  readonly saving = signal(false);
  readonly createError = signal('');
  readonly topicError = signal('');
  createForm: CreateAssemblyRequest = { title: '', type: 'ORDINARIA', scheduledAt: '', requiredQuorumPercentage: 50 };
  scheduledAt = '';
  assemblyStatus: AssemblyStatus = 'SCHEDULED';
  checkUnitId = '';
  proxyName = '';
  isProxy = false;
  topicForm: CreateTopicRequest = { orderNum: 1, title: '', description: '', votingType: 'NOMINAL', requiresQualifiedMajority: false, options: [] };
  topicOptions = 'A favor, En contra, Abstención';

  constructor() {
    void this.load();
    void this.loadUnits();
  }

  async load(): Promise<void> {
    try {
      this.assemblies.set(await firstValueFrom(this.api.get<Assembly[]>('/assemblies')));
    } catch {
      // El interceptor global muestra el error.
    }
  }

  async loadUnits(): Promise<void> {
    try {
      const result = await firstValueFrom(this.api.get<PageResultPropertyUnit>('/properties', { size: 500 }));
      this.units.set(result.content ?? []);
    } catch {
      // El selector se reintenta al abrir el detalle.
    }
  }

  unitName(id: string | undefined): string {
    return this.units().find((unit) => unit.id === id)?.identifier ?? '—';
  }

  openCreate(): void {
    this.createForm = { title: '', type: 'ORDINARIA', scheduledAt: '', requiredQuorumPercentage: 50 };
    this.scheduledAt = '';
    this.createError.set('');
    this.createOpen.set(true);
  }

  async create(): Promise<void> {
    this.createError.set('');
    if (!this.createForm.title.trim() || !this.scheduledAt) {
      this.createError.set('El título y la fecha de la asamblea son obligatorios.');
      return;
    }
    this.saving.set(true);
    try {
      await firstValueFrom(this.api.post<Assembly>('/assemblies', {
        ...this.createForm,
        scheduledAt: toIsoDateTime(this.scheduledAt)
      }));
      this.toast.success('Asamblea programada.');
      this.createOpen.set(false);
      await this.load();
    } catch (error) {
      this.createError.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async openDetail(assembly: Assembly): Promise<void> {
    this.selected.set(assembly);
    this.assemblyStatus = (assembly.status as AssemblyStatus) || 'SCHEDULED';
    this.checkUnitId = '';
    this.proxyName = '';
    this.isProxy = false;
    this.detailOpen.set(true);
    await Promise.all([this.loadTopics(), this.loadQuorum(), this.loadAttendances()]);
  }

  async loadTopics(): Promise<void> {
    const id = this.selected()?.id;
    if (!id) return;
    this.topics.set(await firstValueFrom(this.api.get<AssemblyTopic[]>(`/assemblies/${id}/topics`)));
  }

  async loadQuorum(): Promise<void> {
    const id = this.selected()?.id;
    if (!id) return;
    this.quorum.set(await firstValueFrom(this.api.get<QuorumSummary>(`/assemblies/${id}/quorum`)));
  }

  async loadAttendances(): Promise<void> {
    const id = this.selected()?.id;
    if (!id) return;
    this.attendances.set(await firstValueFrom(this.api.get<AssemblyAttendance[]>(`/assemblies/${id}/attendances`)));
  }

  async changeStatus(): Promise<void> {
    const id = this.selected()?.id;
    if (!id) return;
    const updated = await firstValueFrom(this.api.patch<Assembly>(`/assemblies/${id}/status`, { status: this.assemblyStatus }));
    this.selected.set(updated);
    this.toast.success('Estado de la asamblea actualizado.');
    await this.load();
  }

  async checkIn(): Promise<void> {
    const id = this.selected()?.id;
    if (!id || !this.checkUnitId) return;
    this.saving.set(true);
    try {
      this.quorum.set(await firstValueFrom(this.api.post<QuorumSummary>(`/assemblies/${id}/check-in`, {
        unitId: this.checkUnitId,
        isProxy: this.isProxy,
        proxyName: this.proxyName.trim() || undefined
      })));
      this.checkUnitId = '';
      this.proxyName = '';
      this.isProxy = false;
      this.toast.success('Asistencia registrada.');
      await this.loadAttendances();
    } finally {
      this.saving.set(false);
    }
  }

  openTopic(): void {
    this.topicForm = { orderNum: this.topics().length + 1, title: '', description: '', votingType: 'NOMINAL', requiresQualifiedMajority: false, options: [] };
    this.topicOptions = 'A favor, En contra, Abstención';
    this.topicError.set('');
    this.topicOpen.set(true);
  }

  async createTopic(): Promise<void> {
    const id = this.selected()?.id;
    this.topicError.set('');
    if (!id || !this.topicForm.title.trim()) {
      this.topicError.set('Indica el título del tema.');
      return;
    }
    this.saving.set(true);
    try {
      await firstValueFrom(this.api.post<AssemblyTopic>(`/assemblies/${id}/topics`, {
        ...this.topicForm,
        options: this.topicOptions.split(',').map((option) => option.trim()).filter(Boolean)
      }));
      this.toast.success('Tema creado.');
      this.topicOpen.set(false);
      await this.loadTopics();
    } catch (error) {
      this.topicError.set(describeError(error));
    } finally {
      this.saving.set(false);
    }
  }

  async openVote(topic: AssemblyTopic): Promise<void> {
    if (!topic.id) return;
    await firstValueFrom(this.api.post<AssemblyTopic>(`/assemblies/topics/${topic.id}/open-vote`));
    this.toast.success('Votación abierta.');
    await this.loadTopics();
  }

  async closeVote(topic: AssemblyTopic): Promise<void> {
    if (!topic.id) return;
    await firstValueFrom(this.api.post<AssemblyTopic>(`/assemblies/topics/${topic.id}/close-vote`));
    this.toast.success('Votación cerrada.');
    await this.loadTopics();
  }

  async loadResults(topic: AssemblyTopic): Promise<void> {
    if (!topic.id) return;
    this.results.set(await firstValueFrom(this.api.get<TopicResults>(`/assemblies/topics/${topic.id}/results`)));
    this.resultsOpen.set(true);
  }
}
