import { Component, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { ResourcesService } from '../resources/resources.service';
import { WorkActivitiesService } from '../work-activities/work-activities.service';
import { HoursReportService } from './hours-report.service';

/** Dati per aprire il cruscotto ore di una commessa. */
export interface HoursReportDialogData {
  commessaId: string;
  commessaNome: string;
}

/** Primo giorno del mese corrente, in formato ISO (yyyy-MM-dd), per il valore di default di "Da". */
function startOfMonth(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`;
}

/** Oggi, in formato ISO (yyyy-MM-dd), per il valore di default di "A". */
function today(): string {
  return new Date().toISOString().slice(0, 10);
}

/**
 * Cruscotto ore di una commessa (#52): le tre viste dell'issue (settimanale, mensile per
 * risorsa, per lavorazione) nel periodo scelto. Apribile da un pulsante della riga della
 * commessa, come {@code WorkActivitiesDialog}/{@code DailyReportsDialog}.
 *
 * <p>Sola lettura: nessun form di scrittura, nessun permesso oltre a quello che apre il dialog
 * stesso ({@code work-entries:read}, controllato dal pulsante in {@code CommessePage}). I nomi di
 * risorsa e lavorazione sono risolti dalla copia locale già sincronizzata (come in
 * {@code WorkEntriesDialog}): l'API del report restituisce solo gli id.
 */
@Component({
  selector: 'app-hours-report-dialog',
  imports: [
    FormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatTableModule,
  ],
  template: `
    <h2 mat-dialog-title>Cruscotto ore — {{ data.commessaNome }}</h2>
    <mat-dialog-content>
      <form (ngSubmit)="reload()" class="row">
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Da</mat-label>
          <input matInput type="date" name="from" [(ngModel)]="from" required />
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>A</mat-label>
          <input matInput type="date" name="to" [(ngModel)]="to" required />
        </mat-form-field>
        <button mat-flat-button type="submit" [disabled]="!from || !to || service.loading()">
          <mat-icon>search</mat-icon>Aggiorna
        </button>
      </form>

      @if (service.offline()) {
        <div class="banner warn"><mat-icon>cloud_off</mat-icon>Report non disponibile: il server non è raggiungibile.</div>
      }

      @if (report(); as r) {
        <h3>Ore per settimana</h3>
        <table mat-table [dataSource]="r.weekly" data-testid="hours-report-weekly">
          <ng-container matColumnDef="week">
            <th mat-header-cell *matHeaderCellDef>Settimana</th>
            <td mat-cell *matCellDef="let w">{{ w.weekStart }}</td>
          </ng-container>
          <ng-container matColumnDef="hours">
            <th mat-header-cell *matHeaderCellDef>Ore</th>
            <td mat-cell *matCellDef="let w">{{ w.hours }}</td>
          </ng-container>
          <ng-container matColumnDef="overtimeHours">
            <th mat-header-cell *matHeaderCellDef>Straordinario</th>
            <td mat-cell *matCellDef="let w">{{ w.overtimeHours || '—' }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="weeklyColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: weeklyColumns"></tr>
          <tr class="mat-row" *matNoDataRow>
            <td class="mat-cell empty" [attr.colspan]="weeklyColumns.length">Nessuna ora nel periodo.</td>
          </tr>
        </table>

        <h3>Ore per risorsa (mensile)</h3>
        <table mat-table [dataSource]="r.byResource" data-testid="hours-report-by-resource">
          <ng-container matColumnDef="resource">
            <th mat-header-cell *matHeaderCellDef>Risorsa</th>
            <td mat-cell *matCellDef="let row">{{ resourceName(row.resourceId) }}</td>
          </ng-container>
          <ng-container matColumnDef="month">
            <th mat-header-cell *matHeaderCellDef>Mese</th>
            <td mat-cell *matCellDef="let row">{{ row.month }}</td>
          </ng-container>
          <ng-container matColumnDef="hours">
            <th mat-header-cell *matHeaderCellDef>Ore</th>
            <td mat-cell *matCellDef="let row">{{ row.hours }}</td>
          </ng-container>
          <ng-container matColumnDef="overtimeHours">
            <th mat-header-cell *matHeaderCellDef>Straordinario</th>
            <td mat-cell *matCellDef="let row">{{ row.overtimeHours || '—' }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="byResourceColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: byResourceColumns"></tr>
          <tr class="mat-row" *matNoDataRow>
            <td class="mat-cell empty" [attr.colspan]="byResourceColumns.length">Nessuna ora nel periodo.</td>
          </tr>
        </table>

        <h3>Ore per lavorazione</h3>
        <table mat-table [dataSource]="r.byWorkActivity" data-testid="hours-report-by-activity">
          <ng-container matColumnDef="activity">
            <th mat-header-cell *matHeaderCellDef>Lavorazione</th>
            <td mat-cell *matCellDef="let row">{{ activityLabel(row.workActivityId) }}</td>
          </ng-container>
          <ng-container matColumnDef="hours">
            <th mat-header-cell *matHeaderCellDef>Ore</th>
            <td mat-cell *matCellDef="let row">{{ row.hours }}</td>
          </ng-container>
          <ng-container matColumnDef="overtimeHours">
            <th mat-header-cell *matHeaderCellDef>Straordinario</th>
            <td mat-cell *matCellDef="let row">{{ row.overtimeHours || '—' }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="byActivityColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: byActivityColumns"></tr>
          <tr class="mat-row" *matNoDataRow>
            <td class="mat-cell empty" [attr.colspan]="byActivityColumns.length">Nessuna ora nel periodo.</td>
          </tr>
        </table>
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
    h3 {
      margin: 20px 0 8px;
    }
    .empty {
      padding: 16px;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class HoursReportDialog {
  readonly data = inject<HoursReportDialogData>(MAT_DIALOG_DATA);
  readonly service = inject(HoursReportService);
  private readonly resources = inject(ResourcesService);
  private readonly activities = inject(WorkActivitiesService);
  readonly report = this.service.report;

  readonly weeklyColumns = ['week', 'hours', 'overtimeHours'];
  readonly byResourceColumns = ['resource', 'month', 'hours', 'overtimeHours'];
  readonly byActivityColumns = ['activity', 'hours', 'overtimeHours'];

  from = startOfMonth();
  to = today();

  private readonly resourcesById = computed(() => new Map(this.resources.resources().map((r) => [r.id, r])));
  private readonly activitiesById = computed(() => new Map(this.activities.activities().map((a) => [a.id, a])));

  constructor() {
    void this.resources.load();
    void this.activities.load(this.data.commessaId);
    void this.reload();
  }

  resourceName(resourceId: string): string {
    const r = this.resourcesById().get(resourceId);
    return r ? `${r.firstName} ${r.lastName}` : 'Risorsa non trovata';
  }

  activityLabel(workActivityId: string): string {
    const a = this.activitiesById().get(workActivityId);
    return a ? `${a.code} — ${a.name}` : 'Lavorazione non trovata';
  }

  async reload(): Promise<void> {
    await this.service.load(this.data.commessaId, this.from, this.to);
  }
}
