import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { HasPermissionDirective } from '../../core/profile/has-permission.directive';
import { SyncService } from '../../core/sync/sync.service';
import { CustomersService } from './customers.service';

/** Anagrafica clienti: elenco con ricerca e inserimento rapido (funziona anche offline). */
@Component({
  selector: 'app-customers',
  imports: [
    FormsModule,
    HasPermissionDirective,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatTableModule,
  ],
  template: `
    <div class="page">
      <div class="page-header">
        <h1>Clienti</h1>
        <span class="subtitle">{{ service.customers().length }} in anagrafica</span>
        <span class="spacer"></span>
        @if (sync.pending() > 0) {
          <span class="status todo"><mat-icon inline>sync</mat-icon>{{ sync.pending() }} da sincronizzare</span>
        }
      </div>

      @if (service.offline()) {
        <div class="banner warn"><mat-icon>cloud_off</mat-icon>Server non raggiungibile: stai vedendo la copia salvata sul dispositivo.</div>
      }

      <mat-card appearance="outlined" *appHasPermission="'customers:write'" class="new">
        <mat-card-content>
          <form (ngSubmit)="add()" class="row">
            <mat-form-field appearance="outline" subscriptSizing="dynamic" class="grow">
              <mat-label>Ragione sociale</mat-label>
              <input matInput name="name" [(ngModel)]="name" />
            </mat-form-field>
            <mat-form-field appearance="outline" subscriptSizing="dynamic" class="grow">
              <mat-label>Email</mat-label>
              <input matInput type="email" name="email" [(ngModel)]="email" />
            </mat-form-field>
            <button mat-flat-button type="submit" [disabled]="!name.trim() || saving()">
              <mat-icon>add</mat-icon>Aggiungi cliente
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
          <table mat-table [dataSource]="rows()" data-testid="customers">
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>Ragione sociale</th>
              <td mat-cell *matCellDef="let c" class="name">{{ c.name }}</td>
            </ng-container>
            <ng-container matColumnDef="email">
              <th mat-header-cell *matHeaderCellDef>Email</th>
              <td mat-cell *matCellDef="let c">
                @if (c.email) {
                  <a [href]="'mailto:' + c.email">{{ c.email }}</a>
                } @else {
                  <span class="muted">Non indicata</span>
                }
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
            <tr class="mat-row" *matNoDataRow>
              <td class="mat-cell empty" [attr.colspan]="columns.length">
                {{ filter() ? 'Nessun cliente corrisponde alla ricerca.' : 'Nessun cliente.' }}
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
  `,
})
export class CustomersPage {
  readonly service = inject(CustomersService);
  readonly sync = inject(SyncService);
  readonly saving = signal(false);
  readonly filter = signal('');
  readonly columns = ['name', 'email'];
  readonly rows = computed(() => {
    const q = this.filter().trim().toLowerCase();
    const all = this.service.customers();
    return q ? all.filter((c) => c.name.toLowerCase().includes(q) || (c.email ?? '').toLowerCase().includes(q)) : all;
  });
  name = '';
  email = '';

  constructor() {
    void this.service.load();
  }

  async add(): Promise<void> {
    if (!this.name.trim()) {
      return;
    }
    this.saving.set(true);
    try {
      await this.service.create(this.name, this.email || null);
      this.name = '';
      this.email = '';
    } finally {
      this.saving.set(false);
    }
  }
}
