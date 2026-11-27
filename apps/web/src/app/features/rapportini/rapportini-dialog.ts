import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { HaPermessoDirective } from '../../core/profilo/ha-permesso.directive';
import { openConfirm } from '../../shared/dialogo-conferma';
import { RapportinoLocale } from '../../db/db-locale';
import { RigheOreDialog } from '../righe-ore/righe-ore-dialog';
import { RapportiniService } from './rapportini.service';

/** Dati per aprire la gestione dei rapportini di una commessa. */
export interface RapportiniDialogData {
  commessaId: string;
  commessaNome: string;
}

/**
 * Rapportini giornalieri di una commessa (testata, sempre DRAFT in questa storia): elenco,
 * creazione, apertura per modificare meteo/note, rimozione. Apribile da un pulsante della riga
 * della commessa (niente pagina di dettaglio dedicata, come per {@code LavorazioniDialog}).
 */
@Component({
  selector: 'app-daily-reports-dialog',
  imports: [
    FormsModule,
    HaPermessoDirective,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatTableModule,
  ],
  template: `
    <h2 mat-dialog-title>Rapportini — {{ data.commessaNome }}</h2>
    <mat-dialog-content>
      <form (ngSubmit)="save()" class="row" *appHasPermission="'daily-reports:write'">
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Data</mat-label>
          <input matInput type="date" name="date" [(ngModel)]="date" required [disabled]="!!editing()" />
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic" class="grow">
          <mat-label>Meteo</mat-label>
          <input matInput name="weather" [(ngModel)]="weather" placeholder="es. Sereno" />
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic" class="grow">
          <mat-label>Note</mat-label>
          <input matInput name="notes" [(ngModel)]="notes" />
        </mat-form-field>
        <button mat-flat-button type="submit" [disabled]="!date || saving()">
          <mat-icon>{{ editing() ? 'save' : 'add' }}</mat-icon>{{ editing() ? 'Salva' : 'Apri rapportino' }}
        </button>
        @if (editing()) {
          <button mat-button type="button" (click)="cancelEdit()">Annulla</button>
        }
      </form>

      <table mat-table [dataSource]="service.reports()" data-testid="daily-reports">
        <ng-container matColumnDef="date">
          <th mat-header-cell *matHeaderCellDef>Data</th>
          <td mat-cell *matCellDef="let r">{{ r.date }}</td>
        </ng-container>
        <ng-container matColumnDef="weather">
          <th mat-header-cell *matHeaderCellDef>Meteo</th>
          <td mat-cell *matCellDef="let r">{{ r.weather || '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Stato</th>
          <td mat-cell *matCellDef="let r">{{ statusLabel(r.status) }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef aria-label="Azioni"></th>
          <td mat-cell *matCellDef="let r" class="actions">
            <button
              mat-icon-button
              type="button"
              *appHasPermission="'work-entries:read'"
              aria-label="Ore del rapportino"
              (click)="openWorkEntries(r)"
            >
              <mat-icon>schedule</mat-icon>
            </button>
            <button
              mat-icon-button
              type="button"
              *appHasPermission="'daily-reports:write'"
              aria-label="Apri per modificare"
              [disabled]="r.status !== 'draft'"
              (click)="startEdit(r)"
            >
              <mat-icon>edit</mat-icon>
            </button>
            <button
              mat-icon-button
              type="button"
              *appHasPermission="'daily-reports:write'"
              aria-label="Rimuovi il rapportino"
              [disabled]="removing() === r.id"
              (click)="remove(r)"
            >
              <mat-icon>delete_outline</mat-icon>
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell empty" [attr.colspan]="columns.length">Nessun rapportino.</td>
        </tr>
      </table>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="true">Chiudi</button>
    </mat-dialog-actions>
  `,
  styles: `
    .row {
      display: flex;
      gap: 12px;
      align-items: center;
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
  `,
})
export class RapportiniDialog {
  readonly data = inject<RapportiniDialogData>(MAT_DIALOG_DATA);
  readonly service = inject(RapportiniService);
  private readonly dialog = inject(MatDialog);
  readonly saving = signal(false);
  readonly removing = signal<string | null>(null);
  readonly editing = signal<RapportinoLocale | null>(null);
  readonly columns = ['date', 'weather', 'status', 'actions'];
  date = '';
  weather = '';
  notes = '';

  constructor() {
    void this.service.load(this.data.commessaId);
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'submitted':
        return 'Inviato';
      case 'closed':
        return 'Chiuso';
      default:
        return 'Bozza';
    }
  }

  startEdit(report: RapportinoLocale): void {
    this.editing.set(report);
    this.date = report.date;
    this.weather = report.weather ?? '';
    this.notes = report.notes ?? '';
  }

  openWorkEntries(report: RapportinoLocale): void {
    this.dialog.open(RigheOreDialog, {
      data: {
        dailyReportId: report.id,
        commessaId: this.data.commessaId,
        commessaNome: this.data.commessaNome,
        reportDate: report.date,
      },
      width: '900px',
    });
  }

  cancelEdit(): void {
    this.editing.set(null);
    this.date = '';
    this.weather = '';
    this.notes = '';
  }

  async save(): Promise<void> {
    if (!this.date) {
      return;
    }
    this.saving.set(true);
    try {
      const editing = this.editing();
      if (editing) {
        await this.service.update(editing, this.weather, this.notes);
      } else {
        await this.service.create(this.data.commessaId, this.date, this.weather, this.notes);
      }
      this.cancelEdit();
    } finally {
      this.saving.set(false);
    }
  }

  async remove(report: RapportinoLocale): Promise<void> {
    const ok = await openConfirm(this.dialog, {
      title: 'Rimuovere il rapportino?',
      message: `Il rapportino del ${report.date} non comparirà più in elenco. La rimozione viene inviata al server appena è raggiungibile.`,
      confirm: 'Rimuovi',
    });
    if (!ok) {
      return;
    }
    this.removing.set(report.id);
    try {
      await this.service.remove(report.id, this.data.commessaId);
      if (this.editing()?.id === report.id) {
        this.cancelEdit();
      }
    } finally {
      this.removing.set(null);
    }
  }
}
