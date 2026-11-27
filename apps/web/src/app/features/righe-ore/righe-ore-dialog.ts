import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { HaPermessoDirective } from '../../core/profilo/ha-permesso.directive';
import { openConfirm } from '../../shared/dialogo-conferma';
import { RigaOreLocale } from '../../db/db-locale';
import { AssegnazioniService } from '../assegnazioni/assegnazioni.service';
import { RisorseService } from '../risorse/risorse.service';
import { LavorazioniService } from '../lavorazioni/lavorazioni.service';
import { RigheOreService } from './righe-ore.service';

/** Dati per aprire le righe di ore di un rapportino. */
export interface RigheOreDialogData {
  dailyReportId: string;
  commessaId: string;
  commessaNome: string;
  reportDate: string;
}

/**
 * Righe di ore di un rapportino giornaliero (#50): elenco, creazione, modifica, rimozione.
 * Apribile da un pulsante della riga del rapportino, come {@code RigheOreDialog} è apribile da
 * {@code RapportiniDialog} (niente pagina di dettaglio dedicata, come per le altre entità di
 * cantiere).
 *
 * <p>La risorsa selezionabile è filtrata alle assegnazioni valide sulla commessa alla data del
 * rapportino: lo stesso controllo che il backend applica in {@code WorkEntryService}, qui solo
 * per non proporre in UI una scelta che il server rifiuterebbe.
 */
@Component({
  selector: 'app-work-entries-dialog',
  imports: [
    FormsModule,
    HaPermessoDirective,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
  ],
  template: `
    <h2 mat-dialog-title>Ore — {{ data.commessaNome }} ({{ data.reportDate }})</h2>
    <mat-dialog-content>
      <form (ngSubmit)="save()" class="row" *appHasPermission="'work-entries:write'">
        <mat-form-field appearance="outline" subscriptSizing="dynamic" class="grow">
          <mat-label>Risorsa</mat-label>
          <mat-select name="resourceId" [(ngModel)]="resourceId" required>
            @for (r of assignableResources(); track r.id) {
              <mat-option [value]="r.id">{{ r.name }}</mat-option>
            }
          </mat-select>
          @if (assignableResources().length === 0) {
            <mat-hint>Nessuna risorsa assegnata a questa commessa in data {{ data.reportDate }}.</mat-hint>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic" class="grow">
          <mat-label>Lavorazione</mat-label>
          <mat-select name="workActivityId" [(ngModel)]="workActivityId" required>
            @for (a of activities.activities(); track a.id) {
              <mat-option [value]="a.id">{{ a.code }} — {{ a.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Ore</mat-label>
          <input matInput type="number" name="hours" min="0" step="0.5" [(ngModel)]="hours" required />
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Straordinario</mat-label>
          <input matInput type="number" name="overtimeHours" min="0" step="0.5" [(ngModel)]="overtimeHours" />
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic" class="grow">
          <mat-label>Note</mat-label>
          <input matInput name="notes" [(ngModel)]="notes" />
        </mat-form-field>
        <button mat-flat-button type="submit" [disabled]="!resourceId || !workActivityId || hours === null || saving()">
          <mat-icon>{{ editing() ? 'save' : 'add' }}</mat-icon>{{ editing() ? 'Salva' : 'Aggiungi' }}
        </button>
        @if (editing()) {
          <button mat-button type="button" (click)="cancelEdit()">Annulla</button>
        }
      </form>

      <table mat-table [dataSource]="service.entries()" data-testid="work-entries">
        <ng-container matColumnDef="resource">
          <th mat-header-cell *matHeaderCellDef>Risorsa</th>
          <td mat-cell *matCellDef="let e">{{ resourceName(e.resourceId) }}</td>
        </ng-container>
        <ng-container matColumnDef="activity">
          <th mat-header-cell *matHeaderCellDef>Lavorazione</th>
          <td mat-cell *matCellDef="let e">{{ activityLabel(e.workActivityId) }}</td>
        </ng-container>
        <ng-container matColumnDef="hours">
          <th mat-header-cell *matHeaderCellDef>Ore</th>
          <td mat-cell *matCellDef="let e">{{ e.hours }}</td>
        </ng-container>
        <ng-container matColumnDef="overtimeHours">
          <th mat-header-cell *matHeaderCellDef>Straordinario</th>
          <td mat-cell *matCellDef="let e">{{ e.overtimeHours || '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="notes">
          <th mat-header-cell *matHeaderCellDef>Note</th>
          <td mat-cell *matCellDef="let e">{{ e.notes || '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef aria-label="Azioni"></th>
          <td mat-cell *matCellDef="let e" class="actions">
            <button
              mat-icon-button
              type="button"
              *appHasPermission="'work-entries:write'"
              aria-label="Modifica la riga"
              (click)="startEdit(e)"
            >
              <mat-icon>edit</mat-icon>
            </button>
            <button
              mat-icon-button
              type="button"
              *appHasPermission="'work-entries:write'"
              aria-label="Rimuovi la riga"
              [disabled]="removing() === e.id"
              (click)="remove(e)"
            >
              <mat-icon>delete_outline</mat-icon>
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell empty" [attr.colspan]="columns.length">Nessuna riga di ore.</td>
        </tr>
      </table>
      @if (service.entries().length > 0) {
        <p class="total">
          Totale: {{ totalHours() }} ore
          @if (totalOvertimeHours() > 0) {
            (di cui {{ totalOvertimeHours() }} di straordinario)
          }
        </p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="true">Chiudi</button>
    </mat-dialog-actions>
  `,
  styles: `
    .row {
      display: flex;
      gap: 12px;
      align-items: flex-start;
      flex-wrap: wrap;
      margin-bottom: 16px;
    }
    .grow {
      flex: 1 1 180px;
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
    .total {
      margin: 12px 0 0;
      font-weight: 500;
    }
  `,
})
export class RigheOreDialog {
  readonly data = inject<RigheOreDialogData>(MAT_DIALOG_DATA);
  readonly service = inject(RigheOreService);
  readonly resources = inject(RisorseService);
  readonly assignments = inject(AssegnazioniService);
  readonly activities = inject(LavorazioniService);
  private readonly dialog = inject(MatDialog);
  readonly saving = signal(false);
  readonly removing = signal<string | null>(null);
  readonly editing = signal<RigaOreLocale | null>(null);
  readonly columns = ['resource', 'activity', 'hours', 'overtimeHours', 'notes', 'actions'];
  resourceId: string | null = null;
  workActivityId: string | null = null;
  hours: number | null = null;
  overtimeHours = 0;
  notes = '';

  private readonly resourcesById = computed(() => new Map(this.resources.resources().map((r) => [r.id, r])));
  private readonly activitiesById = computed(() => new Map(this.activities.activities().map((a) => [a.id, a])));

  /** Risorse con un'assegnazione valida sulla commessa alla data del rapportino. */
  readonly assignableResources = computed(() => {
    const date = this.data.reportDate;
    const byId = this.resourcesById();
    return this.assignments
      .assignments()
      .filter((a) => a.validFrom <= date && (!a.validTo || a.validTo >= date) && a.active)
      .map((a) => byId.get(a.resourceId))
      .filter((r): r is NonNullable<typeof r> => !!r)
      .map((r) => ({ id: r.id, name: `${r.firstName} ${r.lastName}` }));
  });

  readonly totalHours = computed(() => this.service.entries().reduce((sum, e) => sum + e.hours, 0));
  readonly totalOvertimeHours = computed(() => this.service.entries().reduce((sum, e) => sum + e.overtimeHours, 0));

  constructor() {
    void this.resources.load();
    void this.assignments.load(this.data.commessaId);
    void this.activities.load(this.data.commessaId);
    void this.service.load(this.data.dailyReportId);
  }

  resourceName(resourceId: string): string {
    const r = this.resourcesById().get(resourceId);
    return r ? `${r.firstName} ${r.lastName}` : 'Risorsa non trovata';
  }

  activityLabel(workActivityId: string): string {
    const a = this.activitiesById().get(workActivityId);
    return a ? `${a.code} — ${a.name}` : 'Lavorazione non trovata';
  }

  startEdit(entry: RigaOreLocale): void {
    this.editing.set(entry);
    this.resourceId = entry.resourceId;
    this.workActivityId = entry.workActivityId;
    this.hours = entry.hours;
    this.overtimeHours = entry.overtimeHours;
    this.notes = entry.notes ?? '';
  }

  cancelEdit(): void {
    this.editing.set(null);
    this.resourceId = null;
    this.workActivityId = null;
    this.hours = null;
    this.overtimeHours = 0;
    this.notes = '';
  }

  async save(): Promise<void> {
    if (!this.resourceId || !this.workActivityId || this.hours === null) {
      return;
    }
    this.saving.set(true);
    try {
      const editing = this.editing();
      if (editing) {
        await this.service.update(editing, this.resourceId, this.workActivityId, this.hours, this.overtimeHours,
            this.notes);
      } else {
        await this.service.create(this.data.dailyReportId, this.resourceId, this.workActivityId, this.hours,
            this.overtimeHours, this.notes);
      }
      this.cancelEdit();
    } finally {
      this.saving.set(false);
    }
  }

  async remove(entry: RigaOreLocale): Promise<void> {
    const ok = await openConfirm(this.dialog, {
      title: 'Rimuovere la riga?',
      message: 'La riga non comparirà più in elenco. La rimozione viene inviata al server appena è raggiungibile.',
      confirm: 'Rimuovi',
    });
    if (!ok) {
      return;
    }
    this.removing.set(entry.id);
    try {
      await this.service.remove(entry.id, this.data.dailyReportId);
      if (this.editing()?.id === entry.id) {
        this.cancelEdit();
      }
    } finally {
      this.removing.set(null);
    }
  }
}
