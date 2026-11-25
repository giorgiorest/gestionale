import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { HasPermissionDirective } from '../../core/profile/has-permission.directive';
import { LocalResource } from '../../db/local-db';
import { ResourceAssignmentsService } from './resource-assignments.service';

/** Dati per aprire la gestione delle risorse di una commessa. */
export interface ResourceAssignmentsDialogData {
  commessaId: string;
  commessaNome: string;
  resources: LocalResource[];
}

/**
 * Risorse assegnate a una commessa (tab "Risorse"): elenco, assegnazione e rimozione.
 * Apribile da un pulsante della riga della commessa (niente pagina di dettaglio dedicata).
 */
@Component({
  selector: 'app-resource-assignments-dialog',
  imports: [
    FormsModule,
    HasPermissionDirective,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
  ],
  template: `
    <h2 mat-dialog-title>Risorse — {{ data.commessaNome }}</h2>
    <mat-dialog-content>
      <form (ngSubmit)="add()" class="row" *appHasPermission="'resource-assignments:write'">
        <mat-form-field appearance="outline" subscriptSizing="dynamic" class="grow">
          <mat-label>Risorsa</mat-label>
          <mat-select name="resourceId" [(ngModel)]="resourceId" required>
            @for (r of data.resources; track r.id) {
              <mat-option [value]="r.id">{{ r.lastName }} {{ r.firstName }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic" class="grow">
          <mat-label>Ruolo su questa commessa</mat-label>
          <input matInput name="role" [(ngModel)]="role" placeholder="es. capocantiere" />
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Da</mat-label>
          <input matInput type="date" name="validFrom" [(ngModel)]="validFrom" required />
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>A (vuoto = in corso)</mat-label>
          <input matInput type="date" name="validTo" [(ngModel)]="validTo" />
        </mat-form-field>
        <button mat-flat-button type="submit" [disabled]="!resourceId || !role.trim() || !validFrom || saving()">
          <mat-icon>add</mat-icon>Assegna
        </button>
      </form>

      <table mat-table [dataSource]="service.assignments()" data-testid="resource-assignments">
        <ng-container matColumnDef="resource">
          <th mat-header-cell *matHeaderCellDef>Risorsa</th>
          <td mat-cell *matCellDef="let a">{{ resourceName(a.resourceId) }}</td>
        </ng-container>
        <ng-container matColumnDef="role">
          <th mat-header-cell *matHeaderCellDef>Ruolo</th>
          <td mat-cell *matCellDef="let a">{{ a.role }}</td>
        </ng-container>
        <ng-container matColumnDef="period">
          <th mat-header-cell *matHeaderCellDef>Periodo</th>
          <td mat-cell *matCellDef="let a">{{ a.validFrom }} — {{ a.validTo || 'in corso' }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef aria-label="Azioni"></th>
          <td mat-cell *matCellDef="let a" class="actions">
            <button
              mat-icon-button
              type="button"
              *appHasPermission="'resource-assignments:write'"
              aria-label="Rimuovi l'assegnazione"
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
          <td class="mat-cell empty" [attr.colspan]="columns.length">Nessuna risorsa assegnata.</td>
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
export class ResourceAssignmentsDialog {
  readonly data = inject<ResourceAssignmentsDialogData>(MAT_DIALOG_DATA);
  readonly service = inject(ResourceAssignmentsService);
  readonly saving = signal(false);
  readonly removing = signal<string | null>(null);
  readonly columns = ['resource', 'role', 'period', 'actions'];
  resourceId: string | null = null;
  role = '';
  validFrom = '';
  validTo = '';

  constructor() {
    void this.service.load(this.data.commessaId);
  }

  resourceName(resourceId: string): string {
    const r = this.data.resources.find((x) => x.id === resourceId);
    return r ? `${r.lastName} ${r.firstName}` : 'Risorsa non trovata';
  }

  async add(): Promise<void> {
    if (!this.resourceId || !this.role.trim() || !this.validFrom) {
      return;
    }
    this.saving.set(true);
    try {
      await this.service.create(this.data.commessaId, this.resourceId, this.role, this.validFrom, this.validTo || null);
      this.resourceId = null;
      this.role = '';
      this.validFrom = '';
      this.validTo = '';
    } finally {
      this.saving.set(false);
    }
  }

  async remove(id: string): Promise<void> {
    this.removing.set(id);
    try {
      await this.service.remove(id, this.data.commessaId);
    } finally {
      this.removing.set(null);
    }
  }
}
