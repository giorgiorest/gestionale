import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ClientiService } from '../clienti/clienti.service';
import { HaPermessoDirective } from '../../core/profilo/ha-permesso.directive';
import { ProfiloService } from '../../core/profilo/profilo.service';
import { SincronizzazioneService } from '../../core/sincronizzazione/sincronizzazione.service';
import { CommessaLocale } from '../../db/db-locale';
import { openConfirm } from '../../shared/dialogo-conferma';
import { RapportiniDialog } from '../rapportini/rapportini-dialog';
import { RiepilogoOreDialog } from '../riepilogo-ore/riepilogo-ore-dialog';
import { AssegnazioniDialog } from '../assegnazioni/assegnazioni-dialog';
import { LavorazioniDialog } from '../lavorazioni/lavorazioni-dialog';
import { COMMESSA_STATI, CommesseService } from './commesse.service';

/** Permessi che rendono visibile almeno un'azione sulla riga di una commessa. */
const ROW_ACTION_PERMISSIONS = [
  'commesse:write',
  'resource-assignments:read',
  'work-activities:read',
  'daily-reports:read',
  'work-entries:read',
] as const;

/** Commesse (cantieri): elenco con stato e cliente, inserimento rapido. */
@Component({
  selector: 'app-commesse',
  imports: [
    FormsModule,
    HaPermessoDirective,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
    MatTooltipModule,
  ],
  template: `
    <div class="page">
      <div class="page-header">
        <h1>Commesse</h1>
        <span class="subtitle">{{ service.commesse().length }} in elenco</span>
        <span class="spacer"></span>
        @if (sync.pending() > 0) {
          <span class="status todo"><mat-icon inline>sync</mat-icon>{{ sync.pending() }} da sincronizzare</span>
        }
      </div>

      @if (service.offline()) {
        <div class="banner warn"><mat-icon>cloud_off</mat-icon>Server non raggiungibile: stai vedendo la copia salvata sul dispositivo.</div>
      }

      <mat-card appearance="outlined" *appHasPermission="'commesse:write'" class="new">
        <mat-card-content>
          <form (ngSubmit)="add()" class="row">
            <mat-form-field appearance="outline" subscriptSizing="dynamic" class="grow">
              <mat-label>Cliente</mat-label>
              <mat-select name="customerId" [(ngModel)]="customerId" required>
                @for (c of customers.customers(); track c.id) {
                  <mat-option [value]="c.id">{{ c.name }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline" subscriptSizing="dynamic" class="grow">
              <mat-label>Nome commessa</mat-label>
              <input matInput name="nome" [(ngModel)]="nome" />
            </mat-form-field>
            <mat-form-field appearance="outline" subscriptSizing="dynamic" class="grow">
              <mat-label>Indirizzo del cantiere</mat-label>
              <input matInput name="indirizzo" [(ngModel)]="indirizzo" />
            </mat-form-field>
            <button mat-flat-button type="submit" [disabled]="!customerId || !nome.trim() || saving()">
              <mat-icon>add</mat-icon>Aggiungi commessa
            </button>
          </form>
        </mat-card-content>
      </mat-card>

      <mat-card appearance="outlined">
        <mat-card-content>
          <mat-form-field appearance="outline" subscriptSizing="dynamic" class="search">
            <mat-icon matPrefix>search</mat-icon>
            <mat-label>Cerca</mat-label>
            <input matInput name="filter" [ngModel]="filter()" (ngModelChange)="filter.set($event)" />
          </mat-form-field>
          <table mat-table [dataSource]="rows()" data-testid="commesse">
            <ng-container matColumnDef="nome">
              <th mat-header-cell *matHeaderCellDef>Commessa</th>
              <td mat-cell *matCellDef="let c" class="name">{{ c.nome }}</td>
            </ng-container>
            <ng-container matColumnDef="customer">
              <th mat-header-cell *matHeaderCellDef>Cliente</th>
              <td mat-cell *matCellDef="let c">{{ customerName(c.customerId) }}</td>
            </ng-container>
            <ng-container matColumnDef="stato">
              <th mat-header-cell *matHeaderCellDef>Stato</th>
              <td mat-cell *matCellDef="let c">{{ statoLabel(c.stato) }}</td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef aria-label="Azioni"></th>
              <td mat-cell *matCellDef="let c" class="actions">
                <button
                  mat-icon-button
                  type="button"
                  *appHasPermission="'resource-assignments:read'"
                  aria-label="Risorse della commessa"
                  matTooltip="Risorse"
                  (click)="openResources(c)"
                >
                  <mat-icon>groups</mat-icon>
                </button>
                <button
                  mat-icon-button
                  type="button"
                  *appHasPermission="'work-activities:read'"
                  aria-label="Lavorazioni della commessa"
                  matTooltip="Lavorazioni"
                  (click)="openWorkActivities(c)"
                >
                  <mat-icon>checklist</mat-icon>
                </button>
                <button
                  mat-icon-button
                  type="button"
                  *appHasPermission="'daily-reports:read'"
                  aria-label="Rapportini della commessa"
                  matTooltip="Rapportini"
                  (click)="openDailyReports(c)"
                >
                  <mat-icon>assignment</mat-icon>
                </button>
                <button
                  mat-icon-button
                  type="button"
                  *appHasPermission="'work-entries:read'"
                  aria-label="Cruscotto ore della commessa"
                  matTooltip="Cruscotto ore"
                  (click)="openHoursReport(c)"
                >
                  <mat-icon>query_stats</mat-icon>
                </button>
                <button
                  mat-icon-button
                  type="button"
                  *appHasPermission="'commesse:write'"
                  aria-label="Cancella la commessa"
                  matTooltip="Cancella"
                  [disabled]="removing() === c.id"
                  (click)="remove(c)"
                >
                  <mat-icon>delete_outline</mat-icon>
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns()"></tr>
            <tr mat-row *matRowDef="let row; columns: columns()"></tr>
            <tr class="mat-row" *matNoDataRow>
              <td class="mat-cell empty" [attr.colspan]="columns().length">
                {{ filter() ? 'Nessuna commessa corrisponde alla ricerca.' : 'Nessuna commessa.' }}
              </td>
            </tr>
          </table>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: `
    .new {
      margin-bottom: 16px;
    }
    .row {
      display: flex;
      gap: 12px;
      align-items: center;
      flex-wrap: wrap;
    }
    .grow {
      flex: 1 1 220px;
    }
    .search {
      width: 320px;
      max-width: 100%;
      margin-bottom: 8px;
    }
    .name {
      font-weight: 500;
    }
    .empty {
      padding: 16px;
      color: var(--mat-sys-on-surface-variant);
    }
    .actions {
      text-align: right;
      width: 1%;
      white-space: nowrap;
    }
  `,
})
export class CommessePage {
  readonly service = inject(CommesseService);
  readonly customers = inject(ClientiService);
  readonly sync = inject(SincronizzazioneService);
  private readonly profile = inject(ProfiloService);
  private readonly dialog = inject(MatDialog);
  readonly saving = signal(false);
  readonly removing = signal<string | null>(null);
  readonly filter = signal('');
  readonly columns = computed(() =>
    ROW_ACTION_PERMISSIONS.some((p) => this.profile.hasPermission(p))
      ? ['nome', 'customer', 'stato', 'actions']
      : ['nome', 'customer', 'stato'],
  );
  readonly rows = computed(() => {
    const q = this.filter().trim().toLowerCase();
    const all = this.service.commesse();
    return q ? all.filter((c) => c.nome.toLowerCase().includes(q)) : all;
  });
  /** Nome del cliente per id, ricalcolato solo quando cambia l'elenco: evita una scansione per riga. */
  private readonly customersById = computed(() => new Map(this.customers.customers().map((c) => [c.id, c])));
  customerId: string | null = null;
  nome = '';
  indirizzo = '';

  constructor() {
    void this.customers.load();
    void this.service.load();
  }

  customerName(customerId: string): string {
    return this.customersById().get(customerId)?.name ?? 'Cliente non trovato';
  }

  openResources(commessa: CommessaLocale): void {
    this.dialog.open(AssegnazioniDialog, {
      data: { commessaId: commessa.id, commessaNome: commessa.nome },
      width: '720px',
    });
  }

  openWorkActivities(commessa: CommessaLocale): void {
    this.dialog.open(LavorazioniDialog, {
      data: { commessaId: commessa.id, commessaNome: commessa.nome },
      width: '720px',
    });
  }

  openDailyReports(commessa: CommessaLocale): void {
    this.dialog.open(RapportiniDialog, {
      data: { commessaId: commessa.id, commessaNome: commessa.nome },
      width: '820px',
    });
  }

  openHoursReport(commessa: CommessaLocale): void {
    this.dialog.open(RiepilogoOreDialog, {
      data: { commessaId: commessa.id, commessaNome: commessa.nome },
      width: '820px',
    });
  }

  statoLabel(stato: string): string {
    return COMMESSA_STATI.find((s) => s.value === stato)?.label ?? stato;
  }

  async add(): Promise<void> {
    if (!this.customerId || !this.nome.trim()) {
      return;
    }
    this.saving.set(true);
    try {
      await this.service.create(this.customerId, this.nome, this.indirizzo || null, 'pianificata');
      this.customerId = null;
      this.nome = '';
      this.indirizzo = '';
    } finally {
      this.saving.set(false);
    }
  }

  /** Cancella la commessa, previa conferma: funziona anche offline (la modifica resta in coda). */
  async remove(commessa: CommessaLocale): Promise<void> {
    const ok = await openConfirm(this.dialog, {
      title: 'Cancellare la commessa?',
      message: `${commessa.nome} non comparirà più in elenco. La cancellazione viene inviata al server appena è raggiungibile.`,
      confirm: 'Cancella',
    });
    if (!ok) {
      return;
    }
    this.removing.set(commessa.id);
    try {
      await this.service.remove(commessa.id);
    } finally {
      this.removing.set(null);
    }
  }
}
