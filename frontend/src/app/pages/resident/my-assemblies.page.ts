import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, KeyValuePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { label, statusTone } from '../../core/labels';
import {
  Assembly,
  AssemblyTopic,
  AssemblyVote,
  MyUnit,
  QuorumSummary,
  TopicResults,
  VoteRequest
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { describeError } from '../../core/utils';
import { EmptyStateComponent, ModalComponent, PageHeaderComponent } from '../../shared/ui.components';

@Component({
  selector: 'app-my-assemblies-page',
  standalone: true,
  imports: [FormsModule, DatePipe, KeyValuePipe, PageHeaderComponent, ModalComponent, EmptyStateComponent],
  template: `
    <app-page-header title="Asambleas" subtitle="Convocatorias, puntos de votación y resultados de las asambleas de la copropiedad." />

    <div class="grid grid-4">
      <div class="stat accent"><div class="stat-label">Asambleas</div><div class="stat-value">{{ assemblies().length }}</div><div class="stat-hint">Programadas y realizadas</div></div>
      <div class="stat"><div class="stat-label">Programadas</div><div class="stat-value">{{ scheduledCount() }}</div><div class="stat-hint">Próximas convocatorias</div></div>
      <div class="stat warn"><div class="stat-label">En curso</div><div class="stat-value">{{ inProgressCount() }}</div><div class="stat-hint">Con participación activa</div></div>
      <div class="stat"><div class="stat-label">Cerradas</div><div class="stat-value">{{ closedCount() }}</div><div class="stat-hint">Con decisiones registradas</div></div>
    </div>

    @if (loading()) {
      <div class="loading"><span class="spinner"></span> Cargando asambleas…</div>
    } @else if (!assemblies().length) {
      <div class="card"><app-empty-state symbol="◫" title="Sin asambleas" message="Cuando la administración programe una asamblea aparecerá en este espacio." /></div>
    } @else {
      <div class="grid grid-2">
        @for (assembly of assemblies(); track assembly.id) {
          <article class="card">
            <div class="card-header">
              <div>
                <h2>{{ assembly.title }}</h2>
                <p>{{ label(assembly.type) }} · {{ assembly.scheduledAt | date:'dd/MM/yyyy HH:mm' }}</p>
              </div>
              <span class="badge" [class]="'badge ' + tone(assembly.status)">{{ label(assembly.status) }}</span>
            </div>
            <div class="card-body">
              <dl class="detail-list">
                <dt>Modalidad</dt><dd>{{ assembly.isVirtual ? 'Virtual' : 'Presencial' }}{{ assembly.location ? ' · ' + assembly.location : '' }}</dd>
                <dt>Quórum requerido</dt><dd>{{ assembly.requiredQuorumPercentage ?? 0 }}%</dd>
                <dt>Convocatoria</dt><dd>{{ assembly.createdAt | date:'dd/MM/yyyy' }}</dd>
              </dl>
              <div class="form-actions">
                @if (assembly.meetingLink) {
                  <a class="btn btn-secondary btn-sm" [href]="assembly.meetingLink" target="_blank" rel="noopener">Abrir reunión</a>
                }
                <button type="button" class="btn btn-primary btn-sm" (click)="openDetail(assembly)">Ver agenda y votaciones</button>
              </div>
            </div>
          </article>
        }
      </div>
    }

    <app-modal [open]="detailOpen()" [title]="selected()?.title || 'Asamblea'" [subtitle]="selected() ? ((selected()?.scheduledAt | date:'dd/MM/yyyy HH:mm') + ' · ' + label(selected()?.type)) : ''" [wide]="true" (closed)="detailOpen.set(false)">
      @if (selected(); as assembly) {
        <div class="row between" style="margin-bottom:14px;flex-wrap:wrap;gap:8px">
          <div class="row" style="gap:8px">
            <span class="badge" [class]="'badge ' + tone(assembly.status)">{{ label(assembly.status) }}</span>
            <span class="muted small">{{ assembly.isVirtual ? 'Virtual' : (assembly.location || 'Presencial') }}</span>
          </div>
          @if (assembly.meetingLink) { <a class="btn btn-secondary btn-sm" [href]="assembly.meetingLink" target="_blank" rel="noopener">Abrir reunión</a> }
        </div>

        @if (quorum(); as summary) {
          <div class="grid grid-4" style="margin-bottom:18px">
            <div class="stat"><div class="stat-label">Quórum actual</div><div class="stat-value">{{ summary.currentQuorumPercentage ?? 0 }}%</div></div>
            <div class="stat"><div class="stat-label">Quórum requerido</div><div class="stat-value">{{ summary.requiredQuorumPercentage ?? 0 }}%</div></div>
            <div class="stat"><div class="stat-label">Inmuebles registrados</div><div class="stat-value">{{ summary.registeredUnits ?? 0 }}</div></div>
            <div class="stat"><div class="stat-label">Estado</div><div class="stat-value" style="font-size:18px">{{ summary.hasQuorum ? 'Constituido' : 'Pendiente' }}</div></div>
          </div>
        }

        <h3 class="section-title">Puntos de la asamblea</h3>
        @if (loadingTopics()) {
          <div class="loading"><span class="spinner"></span> Cargando agenda…</div>
        } @else if (!topics().length) {
          <app-empty-state symbol="≡" title="Sin puntos registrados" message="La administración aún no ha publicado los puntos de esta asamblea." />
        } @else {
          <div class="stack" style="gap:10px">
            @for (topic of topics(); track topic.id) {
              <div style="border:1px solid var(--line);border-radius:9px;padding:12px">
                <div class="row between" style="gap:10px;align-items:flex-start">
                  <div>
                    <div style="font-weight:650;font-size:13.5px">{{ topic.orderNum }}. {{ topic.title }}</div>
                    <div class="muted small" style="margin-top:3px">{{ label(topic.votingType) }}{{ topic.requiresQualifiedMajority ? ' · mayoría calificada' : '' }}</div>
                    @if (topic.description) { <p style="font-size:12.5px;margin-top:6px;white-space:pre-wrap">{{ topic.description }}</p> }
                  </div>
                  <span class="badge" [class]="'badge ' + tone(topic.status)">{{ label(topic.status) }}</span>
                </div>
                <div class="row" style="margin-top:9px;gap:6px;flex-wrap:wrap">
                  @if (topic.status === 'VOTING_OPEN') {
                    @if (auth.hasPermission('ASSEMBLIES_VOTE')) {
                      @if (voted()[topic.id || '']) {
                        <span class="badge success">Voto registrado · {{ voted()[topic.id || ''] }}</span>
                      } @else {
                        <button type="button" class="btn btn-primary btn-sm" (click)="openVote(topic)">Emitir voto</button>
                      }
                    } @else {
                      <span class="muted small">Tu rol puede consultar la votación, pero no emitir votos.</span>
                    }
                  }
                  @if (topic.status === 'VOTING_CLOSED') {
                    <button type="button" class="btn btn-secondary btn-sm" (click)="openResults(topic)">Ver resultados</button>
                  }
                  @if (topic.status === 'PENDING') {
                    <span class="muted small">La votación aún no ha sido abierta por la administración.</span>
                  }
                </div>
              </div>
            }
          </div>
        }

        <div class="notice" style="margin-top:16px">
          Para votar, el inmueble debe tener registrada su asistencia a la asamblea. Si no aparece habilitado, contacta a la administración o al comité organizador.
        </div>
      }
    </app-modal>

    <app-modal [open]="voteOpen()" title="Emitir voto" [subtitle]="voteTopic()?.title || ''" [narrow]="true" (closed)="voteOpen.set(false)">
      @if (voteError()) { <div class="alert alert-error" style="margin-bottom:14px">{{ voteError() }}</div> }
      @if (voteTopic(); as topic) {
        <div class="notice" style="margin-bottom:14px">Cada inmueble puede votar una sola vez en este punto. El voto es definitivo.</div>
        <div class="field">
          <label for="voteUnit">Inmueble que representas *</label>
          <select id="voteUnit" name="voteUnit" class="select" [(ngModel)]="voteUnitId">
            <option value="">Selecciona el inmueble</option>
            @for (unit of units(); track unit.unitId) { <option [value]="unit.unitId">{{ unit.identifier }} · {{ unit.tower || 'Sin torre' }}</option> }
          </select>
        </div>
        <div class="field" style="margin-top:12px">
          <label for="voteChoice">Opción *</label>
          <select id="voteChoice" name="voteChoice" class="select" [(ngModel)]="voteChoice">
            <option value="">Selecciona una opción</option>
            @for (option of topic.options; track option) { <option [value]="option">{{ option }}</option> }
          </select>
        </div>
        <div class="form-actions">
          <button type="button" class="btn btn-secondary" (click)="voteOpen.set(false)">Cancelar</button>
          <button type="button" class="btn btn-primary" [disabled]="voting() || !voteUnitId || !voteChoice" (click)="submitVote()">{{ voting() ? 'Registrando…' : 'Confirmar voto' }}</button>
        </div>
      }
    </app-modal>

    <app-modal [open]="resultsOpen()" title="Resultados de la votación" [subtitle]="results()?.title || ''" (closed)="resultsOpen.set(false)">
      @if (results(); as result) {
        <div class="grid grid-2">
          <div class="stat"><div class="stat-label">Votos emitidos</div><div class="stat-value">{{ result.totalVotes ?? 0 }}</div></div>
          <div class="stat"><div class="stat-label">Peso total</div><div class="stat-value">{{ result.totalWeight ?? 0 }}</div></div>
        </div>
        <div class="table-wrap" style="margin-top:14px">
          <table class="table">
            <thead><tr><th>Opción</th><th class="num">Peso</th><th class="num">Porcentaje</th></tr></thead>
            <tbody>
              @for (entry of result.voteTallies | keyvalue; track entry.key) {
                <tr>
                  <td>{{ entry.key }}</td>
                  <td class="num">{{ entry.value }}</td>
                  <td class="num">{{ result.percentageTallies?.[entry.key] ?? 0 }}%</td>
                </tr>
              } @empty {
                <tr><td colspan="3" class="table-empty">Aún no se registraron votos.</td></tr>
              }
            </tbody>
          </table>
        </div>
        @if (result.winningOption) { <div class="notice" style="margin-top:14px">Opción ganadora: <strong>{{ result.winningOption }}</strong>.</div> }
      }
    </app-modal>
  `
})
export class MyAssembliesPage {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  readonly label = label;
  readonly tone = statusTone;
  readonly assemblies = signal<Assembly[]>([]);
  readonly units = signal<MyUnit[]>([]);
  readonly topics = signal<AssemblyTopic[]>([]);
  readonly quorum = signal<QuorumSummary | null>(null);
  readonly results = signal<TopicResults | null>(null);
  readonly selected = signal<Assembly | null>(null);
  readonly voteTopic = signal<AssemblyTopic | null>(null);
  readonly voted = signal<Record<string, string>>({});
  readonly loading = signal(false);
  readonly loadingTopics = signal(false);
  readonly detailOpen = signal(false);
  readonly voteOpen = signal(false);
  readonly resultsOpen = signal(false);
  readonly voting = signal(false);
  readonly voteError = signal('');
  readonly scheduledCount = computed(() => this.assemblies().filter((item) => item.status === 'SCHEDULED').length);
  readonly inProgressCount = computed(() => this.assemblies().filter((item) => item.status === 'IN_PROGRESS').length);
  readonly closedCount = computed(() => this.assemblies().filter((item) => item.status === 'CLOSED').length);
  voteUnitId = '';
  voteChoice = '';

  constructor() {
    void this.load();
    void this.loadUnits();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    try {
      const items = await firstValueFrom(this.api.get<Assembly[]>('/my/assemblies'));
      this.assemblies.set([...items].sort((left, right) =>
        new Date(right.scheduledAt ?? 0).getTime() - new Date(left.scheduledAt ?? 0).getTime()
      ));
    } catch (error) {
      this.assemblies.set([]);
      this.toast.error(describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  async loadUnits(): Promise<void> {
    try {
      this.units.set(await firstValueFrom(this.api.get<MyUnit[]>('/my/units')));
    } catch {
      this.units.set([]);
    }
  }

  async openDetail(assembly: Assembly): Promise<void> {
    this.selected.set(assembly);
    this.topics.set([]);
    this.quorum.set(null);
    this.detailOpen.set(true);
    this.loadingTopics.set(true);
    try {
      const requests: Promise<unknown>[] = [
        firstValueFrom(this.api.get<AssemblyTopic[]>(`/assemblies/${assembly.id}/topics`)).then((topics) => this.topics.set(topics))
      ];
      if (assembly.id) {
        requests.push(firstValueFrom(this.api.get<QuorumSummary>(`/assemblies/${assembly.id}/quorum`)).then((summary) => this.quorum.set(summary)));
      }
      await Promise.all(requests);
    } catch (error) {
      this.toast.error(describeError(error));
    } finally {
      this.loadingTopics.set(false);
    }
  }

  openVote(topic: AssemblyTopic): void {
    this.voteTopic.set(topic);
    this.voteUnitId = this.units().length === 1 ? this.units()[0].unitId ?? '' : '';
    this.voteChoice = '';
    this.voteError.set('');
    this.voteOpen.set(true);
  }

  async submitVote(): Promise<void> {
    const topic = this.voteTopic();
    if (!topic?.id) return;
    this.voteError.set('');
    if (!this.voteUnitId || !this.voteChoice) {
      this.voteError.set('Selecciona el inmueble y la opción de voto.');
      return;
    }
    this.voting.set(true);
    try {
      const body: VoteRequest = { unitId: this.voteUnitId, choice: this.voteChoice };
      const vote = await firstValueFrom(this.api.post<AssemblyVote>(`/my/assemblies/topics/${topic.id}/vote`, body));
      const unitName = this.units().find((unit) => unit.unitId === body.unitId)?.identifier ?? body.unitId;
      this.voted.update((current) => ({ ...current, [topic.id as string]: `${unitName} · ${vote.choice ?? body.choice}` }));
      this.toast.success('Voto registrado.');
      this.voteOpen.set(false);
    } catch (error) {
      this.voteError.set(describeError(error));
    } finally {
      this.voting.set(false);
    }
  }

  async openResults(topic: AssemblyTopic): Promise<void> {
    if (!topic.id) return;
    try {
      this.results.set(await firstValueFrom(this.api.get<TopicResults>(`/assemblies/topics/${topic.id}/results`)));
      this.resultsOpen.set(true);
    } catch (error) {
      this.toast.error(describeError(error));
    }
  }
}
