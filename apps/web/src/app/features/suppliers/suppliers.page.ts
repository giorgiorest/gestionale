import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { HasPermissionDirective } from '../../core/profile/has-permission.directive';
import { ProfileService } from '../../core/profile/profile.service';
import { SyncService } from '../../core/sync/sync.service';
import { openConfirm } from '../../shared/confirm-dialog';
import { SupplierRow, SuppliersService } from './suppliers.service';

/** Debounce della ricerca lato server: non una richiesta per ogni tasto premuto. */
const SEARCH_DEBOUNCE_MS = 300;

/** Anagrafica fornitori: elenco con ricerca e inserimento rapido (funziona anche offline). */
@Component({
  selector: 'app-suppliers',
  imports: [
    FormsModule,
    HasPermissionDirective,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatTableModule,
    MatTooltipModule,
  ],
  template: `
    <div class="page">
      <div class="page-header">
        <h1>Fornitori</h1>
        <span class="subtitle">{{ service.totalElements() }} in anagrafica</span>
        <span class="spacer"></span>
        @if (sync.pending() > 0) {
          <span class="status todo"><mat-icon inline>sync</mat-icon>{{ sync.pending() }} da sincronizzare</span>
        }
      </div>

      @if (service.offline()) {
        <div class="banner warn"><mat-icon>cloud_off</mat-icon>Server non raggiungibile: stai vedendo la copia salvata sul dispositivo.</div>
      }

      <mat-card appearance="outlined" *appHasPermission="'suppliers:write'" class="new">
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
            <mat-form-field appearance="outline" subscriptSizing="dynamic" class="grow">
              <mat-label>Partita IVA</mat-label>
              <input matInput name="partitaIva" [(ngModel)]="partitaIva" maxlength="11" />
            </mat-form-field>
            <mat-form-field appearance="outline" subscriptSizing="dynamic" class="grow">
              <mat-label>Codice fiscale</mat-label>
              <input matInput name="codiceFiscale" [(ngModel)]="codiceFiscale" maxlength="16" />
            </mat-form-field>
            <mat-form-field appearance="outline" subscriptSizing="dynamic" class="grow">
              <mat-label>Indirizzo</mat-label>
              <input matInput name="indirizzo" [(ngModel)]="indirizzo" />
            </mat-form-field>
            <mat-form-field appearance="outline" subscriptSizing="dynamic" class="grow">
              <mat-label>PEC</mat-label>
              <input matInput type="email" name="pec" [(ngModel)]="pec" />
            </mat-form-field>
            <mat-form-field appearance="outline" subscriptSizing="dynamic" class="grow">
              <mat-label>Codice destinatario SDI</mat-label>
              <input matInput name="codiceSdi" [(ngModel)]="codiceSdi" maxlength="7" />
            </mat-form-field>
            <mat-form-field appearance="outline" subscriptSizing="dynamic" class="grow">
              <mat-label>Condizioni di pagamento</mat-label>
              <input matInput name="condizioniPagamento" [(ngModel)]="condizioniPagamento" />
            </mat-form-field>
            <button mat-flat-button type="submit" [disabled]="!name.trim() || saving()">
              <mat-icon>add</mat-icon>Aggiungi fornitore
            </button>
          </form>
        </mat-card-content>
      </mat-card>

      <mat-card appearance="outlined">
        <mat-card-content>
          <mat-form-field appearance="outline" subscriptSizing="dynamic" class="search">
            <mat-icon matPrefix>search</mat-icon>
            <mat-label>Cerca</mat-label>
            <input matInput name="filter" [ngModel]="filterText()" (ngModelChange)="onFilterChange($event)" />
          </mat-form-field>
          <table mat-table [dataSource]="service.suppliers()" data-testid="suppliers">
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>Ragione sociale</th>
              <td mat-cell *matCellDef="let s" class="name">{{ s.name }}</td>
            </ng-container>
            <ng-container matColumnDef="email">
              <th mat-header-cell *matHeaderCellDef>Email</th>
              <td mat-cell *matCellDef="let s">
                @if (s.email) {
                  <a [href]="'mailto:' + s.email">{{ s.email }}</a>
                } @else {
                  <span class="muted">Non indicata</span>
                }
              </td>
            </ng-container>
            <ng-container matColumnDef="partitaIva">
              <th mat-header-cell *matHeaderCellDef>Partita IVA</th>
              <td mat-cell *matCellDef="let s">{{ s.partitaIva || s.codiceFiscale || '—' }}</td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef aria-label="Azioni"></th>
              <td mat-cell *matCellDef="let s" class="actions">
                <button
                  mat-icon-button
                  type="button"
                  aria-label="Cancella il fornitore"
                  matTooltip="Cancella"
                  [disabled]="removing() === s.id"
                  (click)="remove(s)"
                >
                  <mat-icon>delete_outline</mat-icon>
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns()"></tr>
            <tr mat-row *matRowDef="let row; columns: columns()"></tr>
            <tr class="mat-row" *matNoDataRow>
              <td class="mat-cell empty" [attr.colspan]="columns().length">
                {{ filterText() ? 'Nessun fornitore corrisponde alla ricerca.' : 'Nessun fornitore.' }}
              </td>
            </tr>
          </table>
          <mat-paginator
            [length]="service.totalElements()"
            [pageIndex]="service.page()"
            [pageSize]="service.size()"
            [pageSizeOptions]="[50, 100, 200]"
            (page)="onPage($event)"
          ></mat-paginator>
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
export class SuppliersPage {
  readonly service = inject(SuppliersService);
  readonly sync = inject(SyncService);
  private readonly profile = inject(ProfileService);
  private readonly dialog = inject(MatDialog);
  readonly saving = signal(false);
  readonly removing = signal<string | null>(null);
  /** Testo digitato nel campo di ricerca: la ricerca server-side parte dopo un breve debounce. */
  readonly filterText = signal('');
  readonly columns = computed(() =>
    this.profile.hasPermission('suppliers:write')
      ? ['name', 'email', 'partitaIva', 'actions']
      : ['name', 'email', 'partitaIva'],
  );
  name = '';
  email = '';
  partitaIva = '';
  codiceFiscale = '';
  indirizzo = '';
  pec = '';
  codiceSdi = '';
  condizioniPagamento = '';

  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    void this.service.load();
  }

  onFilterChange(value: string): void {
    this.filterText.set(value);
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    this.searchTimer = setTimeout(() => {
      this.service.q.set(value.trim());
      this.service.page.set(0);
      void this.service.load();
    }, SEARCH_DEBOUNCE_MS);
  }

  onPage(event: PageEvent): void {
    this.service.page.set(event.pageIndex);
    this.service.size.set(event.pageSize);
    void this.service.load();
  }

  async add(): Promise<void> {
    if (!this.name.trim()) {
      return;
    }
    this.saving.set(true);
    try {
      await this.service.create(this.name, this.email || null, {
        partitaIva: this.partitaIva || null,
        codiceFiscale: this.codiceFiscale || null,
        indirizzo: this.indirizzo || null,
        pec: this.pec || null,
        codiceSdi: this.codiceSdi || null,
        condizioniPagamento: this.condizioniPagamento || null,
      });
      this.name = '';
      this.email = '';
      this.partitaIva = '';
      this.codiceFiscale = '';
      this.indirizzo = '';
      this.pec = '';
      this.codiceSdi = '';
      this.condizioniPagamento = '';
    } finally {
      this.saving.set(false);
    }
  }

  /** Cancella il fornitore, previa conferma: funziona anche offline (la modifica resta in coda). */
  async remove(supplier: SupplierRow): Promise<void> {
    const ok = await openConfirm(this.dialog, {
      title: 'Cancellare il fornitore?',
      message: `${supplier.name} non comparirà più in anagrafica. La cancellazione viene inviata al server appena è raggiungibile.`,
      confirm: 'Cancella',
    });
    if (!ok) {
      return;
    }
    this.removing.set(supplier.id);
    try {
      await this.service.remove(supplier.id);
    } finally {
      this.removing.set(null);
    }
  }
}
