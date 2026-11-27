import { DatePipe } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { Clipboard } from '@angular/cdk/clipboard';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import {
  VoceAudit,
  Catalog,
  ConsoleApiService,
  ConsoleTenant,
  ConsoleUser,
} from './console-api.service';
import {
  ACTION_LABELS,
  FEATURE_LABELS,
  STATUS_LABELS,
  endOfDayIso,
  roleLabel,
} from './console-labels';
import { openConfirm } from '../../shared/dialogo-conferma';
import { NuovoUtenteDialog, PasswordDialog } from './dialogs';

/** Dettaglio di un'azienda: dati, licenza, utenti e registro delle operazioni. */
@Component({
  selector: 'app-console-tenant',
  imports: [
    DatePipe,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatTableModule,
    MatTooltipModule,
    RouterLink,
  ],
  providers: [provideNativeDateAdapter()],
  template: `
    <div class="page">
      <a mat-button routerLink="/console"><mat-icon>arrow_back</mat-icon>Aziende</a>
      @if (error()) {
        <div class="banner error" role="alert">{{ error() }}</div>
      }
      @if (tenant(); as t) {
        <div class="page-header">
          <h1 data-testid="tenant-name">{{ t.name }}</h1>
          <span class="chip" [class.off]="t.status !== 'active'" data-testid="tenant-status">{{
            statusLabel(t.status)
          }}</span>
          <span class="spacer"></span>
          @if (t.status === 'active') {
            <button mat-stroked-button (click)="setStatus('suspended')" data-testid="suspend">
              <mat-icon>block</mat-icon>Sospendi
            </button>
          } @else {
            <button mat-flat-button (click)="setStatus('active')" data-testid="activate">
              <mat-icon>check_circle</mat-icon>Riattiva
            </button>
          }
        </div>

        <div class="grid-2">
          <mat-card appearance="outlined">
            <mat-card-header><mat-card-title>Dati dell'azienda</mat-card-title></mat-card-header>
            <mat-card-content class="form">
              <mat-form-field>
                <mat-label>Ragione sociale</mat-label>
                <input matInput [(ngModel)]="name" maxlength="200" />
              </mat-form-field>
              <div class="field">
                <span class="muted">Sigla</span><span>{{ t.slug }}</span>
              </div>
              <div class="field">
                <span class="muted">Identificativo (tenant_id)</span>
                <span class="id">
                  <code>{{ t.id }}</code>
                  <button
                    mat-icon-button
                    (click)="copy(t.id)"
                    matTooltip="Copia"
                    aria-label="Copia identificativo"
                  >
                    <mat-icon>content_copy</mat-icon>
                  </button>
                </span>
              </div>
              <div class="field">
                <span class="muted">Creata il</span
                ><span>{{ t.createdAt | date: 'd MMMM y, HH:mm' }}</span>
              </div>
            </mat-card-content>
            <mat-card-actions align="end">
              <button
                mat-button
                (click)="saveName()"
                [disabled]="!name.trim() || name.trim() === t.name"
              >
                Salva
              </button>
            </mat-card-actions>
          </mat-card>

          <mat-card appearance="outlined">
            <mat-card-header><mat-card-title>Licenza</mat-card-title></mat-card-header>
            <mat-card-content class="form">
              <div class="row">
                <mat-form-field>
                  <mat-label>Piano</mat-label>
                  <input matInput [(ngModel)]="plan" maxlength="40" />
                </mat-form-field>
                <mat-form-field>
                  <mat-label>Utenti massimi</mat-label>
                  <input
                    matInput
                    type="number"
                    [(ngModel)]="maxUsers"
                    min="1"
                    data-testid="max-users"
                  />
                </mat-form-field>
              </div>
              <mat-form-field>
                <mat-label>Scadenza</mat-label>
                <input matInput [matDatepicker]="picker" [(ngModel)]="validUntil" />
                <mat-datepicker-toggle matIconSuffix [for]="picker" />
                <mat-datepicker #picker />
                <mat-hint>Vuoto = senza scadenza</mat-hint>
              </mat-form-field>
              @for (feature of catalog()?.features ?? []; track feature) {
                <mat-checkbox
                  [checked]="features.has(feature)"
                  (change)="toggle(feature, $event.checked)"
                >
                  {{ featureLabel(feature) }}
                </mat-checkbox>
              }
              @if (features.has('aiAssistant')) {
                <mat-form-field>
                  <mat-label>Richieste AI al mese</mat-label>
                  <input matInput type="number" [(ngModel)]="aiQuotaMonthly" min="0" />
                </mat-form-field>
              }
            </mat-card-content>
            <mat-card-actions align="end">
              <button
                mat-flat-button
                (click)="saveLicense()"
                [disabled]="!plan.trim() || maxUsers < 1"
                data-testid="save-license"
              >
                Salva licenza
              </button>
            </mat-card-actions>
          </mat-card>
        </div>

        <mat-card appearance="outlined" class="section">
          <mat-card-header>
            <mat-card-title>Utenti</mat-card-title>
            <mat-card-subtitle
              >{{ users().length }} su {{ t.license?.maxUsers ?? 0 }} consentiti dalla
              licenza</mat-card-subtitle
            >
          </mat-card-header>
          <mat-card-content>
            <div class="actions">
              <button mat-flat-button (click)="newUser()" data-testid="new-user">
                <mat-icon>person_add</mat-icon>Nuovo utente
              </button>
            </div>
            @if (usersError()) {
              <div class="banner error" role="alert">{{ usersError() }}</div>
            }
            <table mat-table [dataSource]="users()" class="full-width">
              <ng-container matColumnDef="user">
                <th mat-header-cell *matHeaderCellDef>Utente</th>
                <td mat-cell *matCellDef="let u">
                  <strong>{{ u.firstName }} {{ u.lastName }}</strong>
                  <div class="muted">{{ u.username }} · {{ u.email }}</div>
                </td>
              </ng-container>
              <ng-container matColumnDef="role">
                <th mat-header-cell *matHeaderCellDef>Ruolo</th>
                <td mat-cell *matCellDef="let u">
                  <mat-select
                    [value]="u.role"
                    (selectionChange)="setRole(u, $event.value)"
                    class="role"
                    aria-label="Ruolo"
                  >
                    @for (role of catalog()?.roles ?? []; track role) {
                      <mat-option [value]="role">{{ roleLabel(role) }}</mat-option>
                    }
                  </mat-select>
                </td>
              </ng-container>
              <ng-container matColumnDef="enabled">
                <th mat-header-cell *matHeaderCellDef>Attivo</th>
                <td mat-cell *matCellDef="let u">
                  <mat-slide-toggle
                    [checked]="u.enabled"
                    (change)="setEnabled(u, $event.checked)"
                    aria-label="Attivo"
                  />
                </td>
              </ng-container>
              <ng-container matColumnDef="actions">
                <th mat-header-cell *matHeaderCellDef></th>
                <td mat-cell *matCellDef="let u">
                  <button
                    mat-icon-button
                    (click)="resetPassword(u)"
                    matTooltip="Nuova password provvisoria"
                    aria-label="Nuova password provvisoria"
                  >
                    <mat-icon>key</mat-icon>
                  </button>
                </td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="userColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: userColumns"></tr>
              <tr class="mat-row" *matNoDataRow>
                <td class="mat-cell muted" [attr.colspan]="userColumns.length">Nessun utente.</td>
              </tr>
            </table>
          </mat-card-content>
        </mat-card>

        <mat-card appearance="outlined" class="section">
          <mat-card-header
            ><mat-card-title>Registro delle operazioni</mat-card-title></mat-card-header
          >
          <mat-card-content>
            @for (entry of audit(); track entry.id) {
              <div class="audit">
                <span class="muted">{{ entry.createdAt | date: 'd MMM y, HH:mm' }}</span>
                <strong>{{ actionLabel(entry.action) }}</strong>
                <span>{{ summary(entry) }}</span>
                <span class="muted">· {{ entry.actorName }}</span>
              </div>
            } @empty {
              <p class="muted">Nessuna operazione registrata.</p>
            }
          </mat-card-content>
        </mat-card>
      } @else if (!error()) {
        <mat-spinner diameter="32" />
      }
    </div>
  `,
  styles: `
    .chip {
      padding: 2px 10px;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
      font: var(--mat-sys-label-large);
    }
    .chip.off {
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
    .form {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .row {
      display: flex;
      gap: 12px;
    }
    .row mat-form-field {
      flex: 1;
    }
    .field {
      display: flex;
      flex-direction: column;
      margin-bottom: 12px;
    }
    .id {
      display: flex;
      align-items: center;
      gap: 4px;
    }
    code {
      font: var(--mat-sys-body-medium);
      overflow-wrap: anywhere;
    }
    .section {
      margin-top: 16px;
    }
    .actions {
      display: flex;
      justify-content: flex-end;
      margin-bottom: 8px;
    }
    .role {
      width: 160px;
    }
    .audit {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      padding: 6px 0;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
  `,
})
export class DettaglioTenantPage {
  /** Id dell'azienda (parametro di rotta). */
  readonly id = input.required<string>();

  private readonly api = inject(ConsoleApiService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly clipboard = inject(Clipboard);

  readonly tenant = signal<ConsoleTenant | null>(null);
  readonly users = signal<ConsoleUser[]>([]);
  readonly audit = signal<VoceAudit[]>([]);
  readonly catalog = signal<Catalog | null>(null);
  readonly error = signal<string | null>(null);
  readonly usersError = signal<string | null>(null);
  readonly userColumns = ['user', 'role', 'enabled', 'actions'];
  readonly roleLabel = roleLabel;

  name = '';
  plan = '';
  maxUsers = 1;
  validUntil: Date | null = null;
  aiQuotaMonthly = 0;
  features = new Set<string>();

  constructor() {
    queueMicrotask(() => void this.load());
  }

  async load(): Promise<void> {
    try {
      const [tenant, catalog] = await Promise.all([this.api.tenant(this.id()), this.api.catalog()]);
      this.catalog.set(catalog);
      this.apply(tenant);
      await Promise.all([this.loadUsers(), this.loadAudit()]);
    } catch (e) {
      this.error.set((e as Error).message);
    }
  }

  async setStatus(status: 'active' | 'suspended'): Promise<void> {
    const t = this.tenant()!;
    if (status === 'suspended') {
      const ok = await openConfirm(this.dialog, {
        title: 'Sospendere l’azienda?',
        message: `Gli utenti di ${t.name} non potranno più usare l'applicazione finché non la riattivi. I dati restano.`,
        confirm: 'Sospendi',
      });
      if (!ok) {
        return;
      }
    }
    await this.run(
      async () => this.apply(await this.api.updateTenant(t.id, { name: t.name, status })),
      status === 'suspended' ? 'Azienda sospesa' : 'Azienda riattivata',
    );
  }

  async saveName(): Promise<void> {
    const t = this.tenant()!;
    await this.run(
      async () =>
        this.apply(
          await this.api.updateTenant(t.id, {
            name: this.name.trim(),
            status: t.status === 'suspended' ? 'suspended' : 'active',
          }),
        ),
      'Dati salvati',
    );
  }

  async saveLicense(): Promise<void> {
    await this.run(
      async () =>
        this.apply(
          await this.api.updateLicense(this.id(), {
            plan: this.plan.trim(),
            maxUsers: Number(this.maxUsers),
            features: [...this.features],
            aiQuotaMonthly: this.features.has('aiAssistant') ? Number(this.aiQuotaMonthly) : 0,
            validUntil: endOfDayIso(this.validUntil),
          }),
        ),
      'Licenza salvata',
    );
  }

  async newUser(): Promise<void> {
    const t = this.tenant()!;
    const ref = this.dialog.open(NuovoUtenteDialog, {
      data: { tenantId: t.id, tenantName: t.name, roles: this.catalog()?.roles ?? [] },
    });
    const created = await firstValueFrom(ref.afterClosed());
    if (created) {
      this.dialog.open(PasswordDialog, { data: created, disableClose: true });
      await Promise.all([this.loadUsers(), this.loadAudit()]);
    }
  }

  async setRole(user: ConsoleUser, role: string): Promise<void> {
    await this.run(
      () => this.api.updateUser(this.id(), user.id, { role }),
      `Ruolo di ${user.username}: ${roleLabel(role)}`,
    );
    await Promise.all([this.loadUsers(), this.loadAudit()]);
  }

  async setEnabled(user: ConsoleUser, enabled: boolean): Promise<void> {
    await this.run(
      () => this.api.updateUser(this.id(), user.id, { enabled }),
      enabled ? `${user.username} riattivato` : `${user.username} disattivato: sessioni chiuse`,
    );
    await Promise.all([this.loadUsers(), this.loadAudit()]);
  }

  async resetPassword(user: ConsoleUser): Promise<void> {
    const ok = await openConfirm(this.dialog, {
      title: 'Nuova password provvisoria?',
      message: `La password attuale di ${user.username} smette di valere e le sue sessioni vengono chiuse.`,
      confirm: 'Genera',
    });
    if (!ok) {
      return;
    }
    try {
      const { temporaryPassword } = await this.api.resetPassword(this.id(), user.id);
      this.dialog.open(PasswordDialog, {
        data: { username: user.username, password: temporaryPassword },
        disableClose: true,
      });
      await this.loadAudit();
    } catch (e) {
      this.snack.open((e as Error).message, 'OK');
    }
  }

  copy(text: string): void {
    this.clipboard.copy(text);
    this.snack.open('Copiato', undefined, { duration: 1500 });
  }

  toggle(feature: string, on: boolean): void {
    if (on) {
      this.features.add(feature);
    } else {
      this.features.delete(feature);
    }
  }

  statusLabel(status: string): string {
    return STATUS_LABELS[status] ?? status;
  }

  featureLabel(feature: string): string {
    return FEATURE_LABELS[feature] ?? feature;
  }

  actionLabel(action: string): string {
    return ACTION_LABELS[action] ?? action;
  }

  summary(entry: VoceAudit): string {
    const d = entry.details as Record<string, unknown>;
    switch (entry.action) {
      case 'user.create':
        return `${d['username']} (${roleLabel(d['role'] as string)})`;
      case 'user.role':
        return `${d['username']}: ${roleLabel(d['role'] as string)}`;
      case 'user.enable':
      case 'user.disable':
        return String(d['username'] ?? '');
      case 'license.update':
        return `${d['plan']} · ${d['maxUsers']} utenti`;
      default:
        return '';
    }
  }

  private apply(tenant: ConsoleTenant): void {
    this.tenant.set(tenant);
    this.name = tenant.name;
    const l = tenant.license;
    this.plan = l?.plan ?? 'standard';
    this.maxUsers = l?.maxUsers ?? 10;
    this.validUntil = l?.validUntil ? new Date(l.validUntil) : null;
    this.aiQuotaMonthly = l?.aiQuotaMonthly ?? 0;
    this.features = new Set(l?.features ?? []);
  }

  private async loadUsers(): Promise<void> {
    try {
      this.users.set(await this.api.users(this.id()));
      this.usersError.set(null);
    } catch (e) {
      this.usersError.set((e as Error).message);
    }
  }

  private async loadAudit(): Promise<void> {
    try {
      this.audit.set(await this.api.audit(this.id()));
    } catch {
      // Il registro è informativo: un errore qui non blocca la pagina.
    }
  }

  private async run(action: () => Promise<unknown>, done: string): Promise<void> {
    try {
      await action();
      this.snack.open(done, undefined, { duration: 2500 });
      await this.loadAudit();
    } catch (e) {
      this.snack.open((e as Error).message, 'OK');
    }
  }
}
