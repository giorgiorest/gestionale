import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Catalog, ConsoleApiService, ConsoleTenant } from './console-api.service';
import { FEATURE_LABELS, STATUS_LABELS } from './console-labels';
import { NuovoTenantDialog } from './dialogs';

/** Elenco delle aziende con licenza e stato. */
@Component({
  selector: 'app-console-tenants',
  imports: [
    DatePipe,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    RouterLink,
  ],
  template: `
    <div class="page">
      <div class="page-header">
        <h1>Aziende</h1>
        <span class="spacer"></span>
        <button mat-flat-button (click)="create()" [disabled]="!catalog()" data-testid="new-tenant">
          <mat-icon>add_business</mat-icon>Nuova azienda
        </button>
      </div>
      @if (error()) {
        <div class="banner error" role="alert">{{ error() }}</div>
      }
      @if (loading()) {
        <mat-spinner diameter="32" />
      } @else {
        <mat-card appearance="outlined">
          <table mat-table [dataSource]="tenants()" class="full-width">
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>Azienda</th>
              <td mat-cell *matCellDef="let t">
                <a
                  [routerLink]="['/console/aziende', t.id]"
                  class="link"
                  data-testid="tenant-link"
                  >{{ t.name }}</a
                >
                <div class="muted">
                  {{ t.slug }}
                  @if (t.kind === 'demo') {
                    · demo
                  }
                </div>
              </td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Stato</th>
              <td mat-cell *matCellDef="let t">
                <span class="chip" [class.off]="t.status !== 'active'">{{
                  statusLabel(t.status)
                }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="license">
              <th mat-header-cell *matHeaderCellDef>Licenza</th>
              <td mat-cell *matCellDef="let t">
                @if (t.license; as l) {
                  {{ l.plan }} · fino a {{ l.maxUsers }} utenti
                  <div class="muted">{{ features(l.features) }}</div>
                } @else {
                  <span class="muted">Nessuna licenza</span>
                }
              </td>
            </ng-container>
            <ng-container matColumnDef="validUntil">
              <th mat-header-cell *matHeaderCellDef>Scadenza</th>
              <td mat-cell *matCellDef="let t">
                @if (t.license?.validUntil; as until) {
                  <span [class.expired]="isExpired(until)">{{ until | date: 'd MMM y' }}</span>
                } @else {
                  <span class="muted">Nessuna</span>
                }
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
            <tr class="mat-row" *matNoDataRow>
              <td class="mat-cell muted" [attr.colspan]="columns.length">
                Nessuna azienda: creane una.
              </td>
            </tr>
          </table>
        </mat-card>
      }
    </div>
  `,
  styles: `
    .link {
      color: var(--mat-sys-primary);
      font: var(--mat-sys-title-small);
    }
    .chip {
      padding: 2px 10px;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
      font: var(--mat-sys-label-medium);
    }
    .chip.off {
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
    .expired {
      color: var(--mat-sys-error);
    }
  `,
})
export class ElencoTenantPage {
  private readonly api = inject(ConsoleApiService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);

  readonly columns = ['name', 'status', 'license', 'validUntil'];
  readonly tenants = signal<ConsoleTenant[]>([]);
  readonly catalog = signal<Catalog | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  constructor() {
    void this.load();
  }

  async load(): Promise<void> {
    try {
      const [tenants, catalog] = await Promise.all([this.api.tenants(), this.api.catalog()]);
      this.tenants.set(tenants);
      this.catalog.set(catalog);
    } catch (e) {
      this.error.set((e as Error).message);
    } finally {
      this.loading.set(false);
    }
  }

  async create(): Promise<void> {
    const ref = this.dialog.open(NuovoTenantDialog, { data: { catalog: this.catalog() } });
    const tenant = await firstValueFrom(ref.afterClosed());
    if (tenant) {
      await this.router.navigate(['/console/aziende', tenant.id]);
    }
  }

  statusLabel(status: string): string {
    return STATUS_LABELS[status] ?? status;
  }

  features(features: string[]): string {
    return features.length
      ? features.map((f) => FEATURE_LABELS[f] ?? f).join(', ')
      : 'Funzioni di base';
  }

  isExpired(until: string): boolean {
    return new Date(until).getTime() < Date.now();
  }
}
