import { Component, inject, signal } from '@angular/core';
import { Clipboard } from '@angular/cdk/clipboard';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { uuidv7 } from '../../shared/uuid';
import { Catalog, ConsoleApiService, ConsoleTenant, NuovoUtenteInput } from './console-api.service';
import { FEATURE_LABELS, endOfDayIso, roleLabel, slugify } from './console-labels';

/** Password provvisoria: mostrata una sola volta, da consegnare all'utente. */
@Component({
  selector: 'app-console-password',
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>Password provvisoria</h2>
    <mat-dialog-content>
      <p>
        Consegna a <strong>{{ data.username }}</strong> questa password in modo riservato (non per
        email insieme al nome utente). Al primo accesso dovrà sceglierne una nuova.
      </p>
      <div class="secret">
        <code data-testid="temporary-password">{{ data.password }}</code>
        <button mat-stroked-button (click)="copy()">
          <mat-icon>{{ copied() ? 'check' : 'content_copy' }}</mat-icon>{{
            copied() ? 'Copiata' : 'Copia'
          }}
        </button>
      </div>
      <p class="muted">Indirizzo per accedere: {{ loginUrl }}</p>
      <p class="muted">
        Non viene salvata da nessuna parte: chiudendo questa finestra non sarà più visibile.
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-flat-button mat-dialog-close>Fatto</button>
    </mat-dialog-actions>
  `,
  styles: `
    .secret {
      display: flex;
      align-items: center;
      gap: 12px;
      margin: 16px 0;
    }
    code {
      font: var(--mat-sys-title-medium);
      letter-spacing: 1px;
      padding: 8px 12px;
      border-radius: var(--mat-sys-corner-small);
      background: var(--mat-sys-surface-container-high);
    }
  `,
})
export class PasswordDialog {
  readonly data = inject<{ username: string; password: string }>(MAT_DIALOG_DATA);
  private readonly clipboard = inject(Clipboard);
  readonly copied = signal(false);
  readonly loginUrl = new URL('.', document.baseURI).href;

  copy(): void {
    this.copied.set(this.clipboard.copy(this.data.password));
  }
}

/** Nuova azienda con la sua licenza. */
@Component({
  selector: 'app-console-new-tenant',
  imports: [
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatCheckboxModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  providers: [provideNativeDateAdapter()],
  template: `
    <h2 mat-dialog-title>Nuova azienda</h2>
    <form (ngSubmit)="save()" #form="ngForm">
      <mat-dialog-content class="form">
        <mat-form-field>
          <mat-label>Ragione sociale</mat-label>
          <input
            matInput
            name="name"
            [(ngModel)]="name"
            (ngModelChange)="nameChanged()"
            required
            maxlength="200"
          />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Sigla</mat-label>
          <input
            matInput
            name="slug"
            [(ngModel)]="slug"
            (ngModelChange)="slugTouched = true"
            required
            pattern="[a-z0-9]([a-z0-9-]{0,38}[a-z0-9])?"
          />
          <mat-hint>Minuscole, cifre e trattini; unica</mat-hint>
        </mat-form-field>
        <div class="row">
          <mat-form-field>
            <mat-label>Piano</mat-label>
            <input matInput name="plan" [(ngModel)]="plan" required maxlength="40" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Utenti massimi</mat-label>
            <input matInput type="number" name="maxUsers" [(ngModel)]="maxUsers" required min="1" />
          </mat-form-field>
        </div>
        <mat-form-field>
          <mat-label>Scadenza della licenza</mat-label>
          <input
            matInput
            name="validUntil"
            [matDatepicker]="picker"
            [(ngModel)]="validUntil"
            [min]="tomorrow"
          />
          <mat-datepicker-toggle matIconSuffix [for]="picker" />
          <mat-datepicker #picker />
          <mat-hint>Vuoto = senza scadenza</mat-hint>
        </mat-form-field>
        @for (feature of data.catalog.features; track feature) {
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
            <input matInput type="number" name="aiQuota" [(ngModel)]="aiQuotaMonthly" min="0" />
          </mat-form-field>
        }
        @if (error()) {
          <div class="banner error" role="alert">{{ error() }}</div>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Annulla</button>
        <button
          mat-flat-button
          type="submit"
          [disabled]="form.invalid || saving()"
          data-testid="create-tenant"
        >
          Crea azienda
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .form {
      display: flex;
      flex-direction: column;
      gap: 4px;
      min-width: min(480px, 80vw);
    }
    .row {
      display: flex;
      gap: 12px;
    }
    .row mat-form-field {
      flex: 1;
    }
  `,
})
export class NuovoTenantDialog {
  readonly data = inject<{ catalog: Catalog }>(MAT_DIALOG_DATA);
  private readonly api = inject(ConsoleApiService);
  private readonly ref = inject(MatDialogRef<NuovoTenantDialog, ConsoleTenant>);

  name = '';
  slug = '';
  slugTouched = false;
  plan = 'standard';
  maxUsers = 50;
  validUntil: Date | null = null;
  aiQuotaMonthly = 0;
  readonly features = new Set<string>();
  readonly tomorrow = new Date(Date.now() + 86_400_000);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  nameChanged(): void {
    if (!this.slugTouched) {
      this.slug = slugify(this.name);
    }
  }

  toggle(feature: string, on: boolean): void {
    if (on) {
      this.features.add(feature);
    } else {
      this.features.delete(feature);
    }
  }

  featureLabel(feature: string): string {
    return FEATURE_LABELS[feature] ?? feature;
  }

  async save(): Promise<void> {
    this.saving.set(true);
    this.error.set(null);
    try {
      const tenant = await this.api.createTenant({
        id: uuidv7(),
        name: this.name.trim(),
        slug: this.slug,
        license: {
          plan: this.plan.trim(),
          maxUsers: Number(this.maxUsers),
          features: [...this.features],
          aiQuotaMonthly: this.features.has('aiAssistant') ? Number(this.aiQuotaMonthly) : 0,
          validUntil: endOfDayIso(this.validUntil),
        },
      });
      this.ref.close(tenant);
    } catch (e) {
      this.error.set((e as Error).message);
      this.saving.set(false);
    }
  }
}

/** Nuovo utente di un'azienda. */
@Component({
  selector: 'app-console-new-user',
  imports: [
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  template: `
    <h2 mat-dialog-title>Nuovo utente · {{ data.tenantName }}</h2>
    <form (ngSubmit)="save()" #form="ngForm">
      <mat-dialog-content class="form">
        <div class="row">
          <mat-form-field>
            <mat-label>Nome</mat-label>
            <input
              matInput
              name="firstName"
              [(ngModel)]="user.firstName"
              required
              maxlength="100"
            />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Cognome</mat-label>
            <input matInput name="lastName" [(ngModel)]="user.lastName" required maxlength="100" />
          </mat-form-field>
        </div>
        <mat-form-field>
          <mat-label>Email</mat-label>
          <input matInput type="email" name="email" [(ngModel)]="user.email" required email />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Nome utente</mat-label>
          <input
            matInput
            name="username"
            [(ngModel)]="user.username"
            required
            pattern="[a-zA-Z0-9._@-]{3,60}"
          />
          <mat-hint>Es. mario.rossi: senza spazi</mat-hint>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Ruolo</mat-label>
          <mat-select name="role" [(ngModel)]="user.role" required>
            @for (role of data.roles; track role) {
              <mat-option [value]="role">{{ roleLabel(role) }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        @if (error()) {
          <div class="banner error" role="alert">{{ error() }}</div>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Annulla</button>
        <button
          mat-flat-button
          type="submit"
          [disabled]="form.invalid || saving()"
          data-testid="create-user"
        >
          Crea utente
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .form {
      display: flex;
      flex-direction: column;
      gap: 4px;
      min-width: min(480px, 80vw);
    }
    .row {
      display: flex;
      gap: 12px;
    }
    .row mat-form-field {
      flex: 1;
    }
  `,
})
export class NuovoUtenteDialog {
  readonly data = inject<{ tenantId: string; tenantName: string; roles: string[] }>(
    MAT_DIALOG_DATA,
  );
  private readonly api = inject(ConsoleApiService);
  private readonly ref = inject(
    MatDialogRef<NuovoUtenteDialog, { username: string; password: string }>,
  );
  readonly user: NuovoUtenteInput = {
    username: '',
    email: '',
    firstName: '',
    lastName: '',
    role: 'operator',
  };
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly roleLabel = roleLabel;

  async save(): Promise<void> {
    this.saving.set(true);
    this.error.set(null);
    try {
      const created = await this.api.createUser(this.data.tenantId, { ...this.user });
      this.ref.close({ username: created.user.username, password: created.temporaryPassword });
    } catch (e) {
      this.error.set((e as Error).message);
      this.saving.set(false);
    }
  }
}
