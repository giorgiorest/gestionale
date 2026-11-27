import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { AutenticazioneService } from '../../core/autenticazione/autenticazione.service';
import { LogoutService } from '../../core/autenticazione/logout.service';
import { DatiLocaliService } from '../../core/privacy/dati-locali.service';
import { ConfigurazioneAppService } from '../../core/configurazione/configurazione-app';
import { PERMISSION_LABELS, ProfiloService, ROLE_LABELS } from '../../core/profilo/profilo.service';

/** Profilo utente: dati personali, azienda, ruoli e permessi. */
@Component({
  selector: 'app-profile',
  imports: [MatButtonModule, MatCardModule, MatChipsModule, MatIconModule, MatListModule],
  template: `
    <div class="page">
      <div class="page-header"><h1>Profilo</h1></div>
      @if (profile.me(); as me) {
        <div class="grid-2">
          <mat-card appearance="outlined">
            <mat-card-header>
              <mat-icon mat-card-avatar>account_circle</mat-icon>
              <mat-card-title data-testid="profile-name">{{ me.name || '—' }}</mat-card-title>
              <mat-card-subtitle>{{ me.username }}</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <mat-list>
                <mat-list-item>
                  <mat-icon matListItemIcon>mail</mat-icon>
                  <span matListItemTitle>Email</span>
                  <span matListItemLine>{{ me.email || '—' }}</span>
                </mat-list-item>
                <mat-list-item>
                  <mat-icon matListItemIcon>apartment</mat-icon>
                  <span matListItemTitle>Azienda</span>
                  <span matListItemLine>{{ me.tenant?.name ?? 'Nessuna azienda associata' }}</span>
                </mat-list-item>
                @if (appConfig.desktop) {
                  <mat-list-item>
                    <mat-icon matListItemIcon>dns</mat-icon>
                    <span matListItemTitle>Server</span>
                    <span matListItemLine>{{ appConfig.serverUrl }}</span>
                  </mat-list-item>
                }
              </mat-list>
            </mat-card-content>
            <mat-card-actions align="end">
              @if (appConfig.desktop) {
                <button mat-button (click)="changeServer()">Cambia server</button>
              }
              <a mat-button [href]="auth.accountUrl()" target="_blank" rel="noopener">Account e password</a>
              <button mat-flat-button (click)="logout.logout()">Esci</button>
            </mat-card-actions>
          </mat-card>

          <mat-card appearance="outlined">
            <mat-card-header><mat-card-title>Ruoli e permessi</mat-card-title></mat-card-header>
            <mat-card-content>
              <mat-chip-set aria-label="Ruoli">
                @for (role of me.roles; track role) {
                  <mat-chip data-testid="role">{{ roleLabel(role) }}</mat-chip>
                } @empty {
                  <span class="muted">Nessun ruolo assegnato</span>
                }
              </mat-chip-set>
              <h3>Cosa puoi fare</h3>
              <mat-list>
                @for (permission of me.permissions; track permission) {
                  <mat-list-item>
                    <mat-icon matListItemIcon>check_circle</mat-icon>
                    <span matListItemTitle>{{ permissionLabel(permission) }}</span>
                  </mat-list-item>
                }
              </mat-list>
            </mat-card-content>
          </mat-card>
        </div>
      } @else {
        <p class="muted">Profilo non disponibile.</p>
      }
    </div>
  `,
  styles: `
    h3 {
      font: var(--mat-sys-title-small);
      margin: 24px 0 0;
    }
    mat-list-item mat-icon {
      color: var(--mat-sys-primary);
    }
  `,
})
export class ProfiloPage {
  readonly profile = inject(ProfiloService);
  readonly auth = inject(AutenticazioneService);
  readonly appConfig = inject(ConfigurazioneAppService);
  readonly logout = inject(LogoutService);
  private readonly localData = inject(DatiLocaliService);

  constructor() {
    void this.profile.load(true);
  }

  /** App desktop: dimentica server, sessione e dati locali, poi torna alla scelta del server. */
  async changeServer(): Promise<void> {
    await this.localData.clear();
    this.appConfig.disconnect();
    sessionStorage.clear();
    window.location.replace(new URL('server', document.baseURI).href);
  }

  roleLabel(role: string): string {
    return ROLE_LABELS[role] ?? role;
  }

  permissionLabel(permission: string): string {
    return PERMISSION_LABELS[permission] ?? permission;
  }
}
