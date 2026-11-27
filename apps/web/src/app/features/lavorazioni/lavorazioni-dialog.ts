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
import { LavorazioniService } from './lavorazioni.service';

/** Dati per aprire la gestione delle lavorazioni di una commessa. */
export interface LavorazioniDialogData {
  commessaId: string;
  commessaNome: string;
}

/**
 * Lavorazioni di una commessa (tab "Lavorazioni"): elenco, creazione e rimozione. Riutilizzate
 * dal rapportino per classificare le ore. Apribile da un pulsante della riga della commessa
 * (niente pagina di dettaglio dedicata, come per {@code AssegnazioniDialog}).
 */
@Component({
  selector: 'app-work-activities-dialog',
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
    <h2 mat-dialog-title>Lavorazioni — {{ data.commessaNome }}</h2>
    <mat-dialog-content>
      <form (ngSubmit)="add()" class="row" *appHasPermission="'work-activities:write'">
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Codice</mat-label>
          <input matInput name="code" [(ngModel)]="code" placeholder="es. ARM-01" />
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic" class="grow">
          <mat-label>Nome</mat-label>
          <input matInput name="name" [(ngModel)]="name" placeholder="es. Armatura pilastri" />
        </mat-form-field>
        <button mat-flat-button type="submit" [disabled]="!code.trim() || !name.trim() || saving()">
          <mat-icon>add</mat-icon>Aggiungi
        </button>
      </form>

      <table mat-table [dataSource]="service.activities()" data-testid="work-activities">
        <ng-container matColumnDef="code">
          <th mat-header-cell *matHeaderCellDef>Codice</th>
          <td mat-cell *matCellDef="let a">{{ a.code }}</td>
        </ng-container>
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Nome</th>
          <td mat-cell *matCellDef="let a">{{ a.name }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Stato</th>
          <td mat-cell *matCellDef="let a">{{ statusLabel(a.status) }} — {{ a.progressPercentage }}%</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef aria-label="Azioni"></th>
          <td mat-cell *matCellDef="let a" class="actions">
            <button
              mat-icon-button
              type="button"
              *appHasPermission="'work-activities:write'"
              aria-label="Rimuovi la lavorazione"
              [disabled]="removing() === a.id"
              (click)="remove(a.id)"
            >
              <mat-icon>delete_outline</mat-icon>
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell empty" [attr.colspan]="columns.length">Nessuna lavorazione.</td>
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
      flex: 1 1 220px;
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
export class LavorazioniDialog {
  readonly data = inject<LavorazioniDialogData>(MAT_DIALOG_DATA);
  readonly service = inject(LavorazioniService);
  private readonly dialog = inject(MatDialog);
  readonly saving = signal(false);
  readonly removing = signal<string | null>(null);
  readonly columns = ['code', 'name', 'status', 'actions'];
  code = '';
  name = '';

  constructor() {
    void this.service.load(this.data.commessaId);
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'in_corso':
        return 'In corso';
      case 'completata':
        return 'Completata';
      default:
        return 'Pianificata';
    }
  }

  async add(): Promise<void> {
    if (!this.code.trim() || !this.name.trim()) {
      return;
    }
    this.saving.set(true);
    try {
      await this.service.create(this.data.commessaId, this.code, this.name);
      this.code = '';
      this.name = '';
    } finally {
      this.saving.set(false);
    }
  }

  async remove(id: string): Promise<void> {
    const ok = await openConfirm(this.dialog, {
      title: 'Rimuovere la lavorazione?',
      message: 'La lavorazione non sarà più disponibile per questa commessa. La rimozione viene inviata al server appena è raggiungibile.',
      confirm: 'Rimuovi',
    });
    if (!ok) {
      return;
    }
    this.removing.set(id);
    try {
      await this.service.remove(id, this.data.commessaId);
    } finally {
      this.removing.set(null);
    }
  }
}
