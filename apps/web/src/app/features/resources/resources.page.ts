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
import { HasPermissionDirective } from '../../core/profile/has-permission.directive';
import { ProfileService } from '../../core/profile/profile.service';
import { SyncService } from '../../core/sync/sync.service';
import { LocalResource } from '../../db/local-db';
import { openConfirm } from '../../shared/confirm-dialog';
import { ResourcesService } from './resources.service';

/** Risorse (dipendenti che compilano i rapportini) e squadre: elenco e inserimento rapido. */
@Component({
  selector: 'app-resources',
  imports: [
    FormsModule,
    HasPermissionDirective,
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
        <h1>Risorse</h1>
        <span class="subtitle">{{ service.resources().length }} in elenco</span>
        <span class="spacer"></span>
        @if (sync.pending() > 0) {
          <span class="status todo"><mat-icon inline>sync</mat-icon>{{ sync.pending() }} da sincronizzare</span>
        }
      </div>

      @if (service.offline()) {
        <div class="banner warn"><mat-icon>cloud_off</mat-icon>Server non raggiungibile: stai vedendo la copia salvata sul dispositivo.</div>
      }

      <mat-card appearance="outlined" *appHasPermission="'resources:write'" class="new">
        <mat-card-content>
          <form (ngSubmit)="add()" class="row">
            <mat-form-field appearance="outline" subscriptSizing="dynamic" class="grow">
              <mat-label>Nome</mat-label>
              <input matInput name="firstName" [(ngModel)]="firstName" />
            </mat-form-field>
            <mat-form-field appearance="outline" subscriptSizing="dynamic" class="grow">
              <mat-label>Cognome</mat-label>
              <input matInput name="lastName" [(ngModel)]="lastName" />
            </mat-form-field>
            <mat-form-field appearance="outline" subscriptSizing="dynamic" class="grow">
              <mat-label>Fa parte della squadra di</mat-label>
              <mat-select name="teamLeadId" [(ngModel)]="teamLeadId">
                <mat-option [value]="null">Nessuna squadra</mat-option>
                @for (r of service.resources(); track r.id) {
                  <mat-option [value]="r.id">{{ r.lastName }} {{ r.firstName }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <button mat-flat-button type="submit" [disabled]="!firstName.trim() || !lastName.trim() || saving()">
              <mat-icon>add</mat-icon>Aggiungi risorsa
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
          <table mat-table [dataSource]="rows()" data-testid="resources">
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>Nome</th>
              <td mat-cell *matCellDef="let r" class="name">{{ r.lastName }} {{ r.firstName }}</td>
            </ng-container>
            <ng-container matColumnDef="teamLead">
              <th mat-header-cell *matHeaderCellDef>Squadra di</th>
              <td mat-cell *matCellDef="let r">
                @if (teamLeadName(r.teamLeadId); as name) {
                  {{ name }}
                } @else {
                  <span class="muted">Nessuna</span>
                }
              </td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef aria-label="Azioni"></th>
              <td mat-cell *matCellDef="let r" class="actions">
                <button
                  mat-icon-button
                  type="button"
                  aria-label="Cancella la risorsa"
                  matTooltip="Cancella"
                  [disabled]="removing() === r.id"
                  (click)="remove(r)"
                >
                  <mat-icon>delete_outline</mat-icon>
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns()"></tr>
            <tr mat-row *matRowDef="let row; columns: columns()"></tr>
            <tr class="mat-row" *matNoDataRow>
              <td class="mat-cell empty" [attr.colspan]="columns().length">
                {{ filter() ? 'Nessuna risorsa corrisponde alla ricerca.' : 'Nessuna risorsa.' }}
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
      flex: 1 1 240px;
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
export class ResourcesPage {
  readonly service = inject(ResourcesService);
  readonly sync = inject(SyncService);
  private readonly profile = inject(ProfileService);
  private readonly dialog = inject(MatDialog);
  readonly saving = signal(false);
  readonly removing = signal<string | null>(null);
  readonly filter = signal('');
  readonly columns = computed(() =>
    this.profile.hasPermission('resources:write') ? ['name', 'teamLead', 'actions'] : ['name', 'teamLead'],
  );
  readonly rows = computed(() => {
    const q = this.filter().trim().toLowerCase();
    const all = this.service.resources();
    return q
      ? all.filter((r) => `${r.lastName} ${r.firstName}`.toLowerCase().includes(q))
      : all;
  });
  /** Per nome per id, ricalcolata solo quando cambia l'elenco: evita una scansione per riga. */
  private readonly byId = computed(() => new Map(this.service.resources().map((r) => [r.id, r])));
  firstName = '';
  lastName = '';
  teamLeadId: string | null = null;

  constructor() {
    void this.service.load();
  }

  teamLeadName(teamLeadId: string | null): string | null {
    if (!teamLeadId) {
      return null;
    }
    const lead = this.byId().get(teamLeadId);
    return lead ? `${lead.lastName} ${lead.firstName}` : null;
  }

  async add(): Promise<void> {
    if (!this.firstName.trim() || !this.lastName.trim()) {
      return;
    }
    this.saving.set(true);
    try {
      await this.service.create(this.firstName, this.lastName, this.teamLeadId);
      this.firstName = '';
      this.lastName = '';
      this.teamLeadId = null;
    } finally {
      this.saving.set(false);
    }
  }

  /** Cancella la risorsa, previa conferma: funziona anche offline (la modifica resta in coda). */
  async remove(resource: LocalResource): Promise<void> {
    const ok = await openConfirm(this.dialog, {
      title: 'Cancellare la risorsa?',
      message: `${resource.lastName} ${resource.firstName} non comparirà più in elenco. La cancellazione viene inviata al server appena è raggiungibile.`,
      confirm: 'Cancella',
    });
    if (!ok) {
      return;
    }
    this.removing.set(resource.id);
    try {
      await this.service.remove(resource.id);
    } finally {
      this.removing.set(null);
    }
  }
}
