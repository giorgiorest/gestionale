import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { LogoutService } from '../auth/logout.service';
import { BrandingService } from '../branding/branding.service';
import { EntitlementsService } from '../entitlements/entitlements.service';
import { NotificationBellComponent } from '../notifications/notification-bell.component';
import { NotificationsService } from '../notifications/notifications.service';
import { ProfileService, ROLE_LABELS } from '../profile/profile.service';

/** Voce del menu laterale, mostrata solo se l'utente ha il permesso e il piano la include. */
interface NavItem {
  label: string;
  icon: string;
  link: string;
  permission?: string;
  feature?: string;
  exact?: boolean;
}

const NAV: NavItem[] = [
  { label: 'Home', icon: 'home', link: '/', exact: true },
  { label: 'Clienti', icon: 'groups', link: '/clienti', permission: 'customers:read' },
  { label: 'Notifiche', icon: 'notifications', link: '/notifiche' },
  { label: 'Comunicazioni', icon: 'campaign', link: '/comunicazioni', permission: 'notifications:send' },
  { label: 'Assistente', icon: 'smart_toy', link: '/assistente', permission: 'assistant:use', feature: 'aiAssistant' },
];

/**
 * Layout principale (pensato per il PC): barra superiore con logo, azienda, notifiche e menu
 * utente; menu laterale fisso. Su schermi stretti il menu diventa a scomparsa.
 */
@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    MatDividerModule,
    MatIconModule,
    MatListModule,
    MatMenuModule,
    MatSidenavModule,
    MatToolbarModule,
    MatTooltipModule,
    NotificationBellComponent,
  ],
  template: `
    <mat-toolbar class="topbar">
      @if (handset()) {
        <button mat-icon-button (click)="drawer.toggle()" aria-label="Menu"><mat-icon>menu</mat-icon></button>
      }
      <a routerLink="/" class="brand">
        <img [src]="branding.logoUrl()" alt="" class="logo" />
        <span class="name">{{ branding.name() }}</span>
      </a>
      <span class="spacer"></span>
      @if (profile.me()?.tenant; as tenant) {
        <span class="tenant" data-testid="tenant">{{ tenant.name }}</span>
      }
      <app-notification-bell />
      <button mat-button [matMenuTriggerFor]="userMenu" class="user" data-testid="user-menu">
        <mat-icon>account_circle</mat-icon>
        <span class="user-name">{{ displayName() }}</span>
      </button>
      <mat-menu #userMenu="matMenu" xPosition="before">
        <div class="menu-header">
          <strong>{{ displayName() }}</strong>
          <span class="muted">{{ roles() }}</span>
        </div>
        <mat-divider />
        <a mat-menu-item routerLink="/profilo"><mat-icon>person</mat-icon>Profilo</a>
        <a mat-menu-item [href]="auth.accountUrl()" target="_blank" rel="noopener">
          <mat-icon>manage_accounts</mat-icon>Account e password
        </a>
        <a mat-menu-item routerLink="/privacy"><mat-icon>policy</mat-icon>Privacy e cookie</a>
        <button mat-menu-item (click)="logout.logout()" data-testid="logout"><mat-icon>logout</mat-icon>Esci</button>
      </mat-menu>
    </mat-toolbar>

    <mat-sidenav-container class="container">
      <mat-sidenav #drawer [mode]="handset() ? 'over' : 'side'" [opened]="!handset()" class="sidenav">
        <mat-nav-list>
          @for (item of nav(); track item.link) {
            <a
              mat-list-item
              [routerLink]="item.link"
              routerLinkActive
              #rla="routerLinkActive"
              [routerLinkActiveOptions]="{ exact: !!item.exact }"
              [activated]="rla.isActive"
              (click)="handset() && drawer.close()"
            >
              <mat-icon matListItemIcon>{{ item.icon }}</mat-icon>
              <span matListItemTitle>{{ item.label }}</span>
              @if (item.link === '/notifiche' && notifications.badge() > 0) {
                <span matListItemMeta><span class="count">{{ notifications.badge() }}</span></span>
              }
            </a>
          }
        </mat-nav-list>
      </mat-sidenav>
      <mat-sidenav-content>
        <router-outlet />
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      height: 100vh;
    }
    .topbar {
      position: relative;
      z-index: 2;
      gap: 8px;
      background: var(--mat-sys-surface);
      /* Linea di accento del marchio (giallo). */
      border-bottom: 3px solid var(--mat-sys-tertiary);
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 12px;
      color: inherit;
      text-decoration: none;
    }
    .logo {
      height: 36px;
      width: auto;
    }
    .name {
      font: var(--mat-sys-title-large);
    }
    .spacer {
      flex: 1;
    }
    .tenant {
      font: var(--mat-sys-label-large);
      color: var(--mat-sys-on-surface-variant);
      margin-right: 8px;
    }
    .user-name {
      margin-left: 4px;
    }
    .menu-header {
      display: flex;
      flex-direction: column;
      padding: 8px 16px 12px;
    }
    .container {
      flex: 1;
    }
    .sidenav {
      width: 240px;
      border-right: 1px solid var(--mat-sys-outline-variant);
      background: var(--mat-sys-surface);
    }
    .sidenav mat-nav-list {
      padding: 8px;
    }
    .count {
      display: inline-block;
      min-width: 20px;
      padding: 2px 6px;
      box-sizing: border-box;
      text-align: center;
      background: var(--mat-sys-tertiary);
      color: var(--mat-sys-on-tertiary);
      font-weight: 600;
      border-radius: var(--mat-sys-corner-full);
      font: var(--mat-sys-label-medium);
      line-height: 16px;
    }
    @media (max-width: 700px) {
      .tenant,
      .user-name {
        display: none;
      }
    }
  `,
})
export class ShellComponent {
  readonly auth = inject(AuthService);
  readonly logout = inject(LogoutService);
  readonly branding = inject(BrandingService);
  readonly profile = inject(ProfileService);
  readonly notifications = inject(NotificationsService);
  private readonly entitlements = inject(EntitlementsService);

  readonly handset = toSignal(
    inject(BreakpointObserver)
      .observe('(max-width: 900px)')
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  readonly nav = computed(() =>
    NAV.filter(
      (item) =>
        (!item.permission || this.profile.permissions().has(item.permission)) &&
        (!item.feature || this.entitlements.features().has(item.feature)),
    ),
  );

  readonly displayName = computed(() => this.profile.me()?.name ?? this.profile.me()?.username ?? '');
  readonly roles = computed(() => (this.profile.me()?.roles ?? []).map((r) => ROLE_LABELS[r] ?? r).join(', '));

  constructor() {
    void this.profile.load();
    void this.entitlements.load();
  }
}
