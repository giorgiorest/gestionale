import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import { Branding } from '../config/app-config';

/** Marchio di prodotto, usato quando l'installazione non ne indica uno proprio. */
export const DEFAULT_BRANDING: Required<Pick<Branding, 'name' | 'logoUrl'>> = {
  name: 'Civil Beefy',
  logoUrl: 'branding/logo.svg',
};

const HEX_COLOR = /^#[0-9a-fA-F]{6}$/;

/**
 * Applica il marchio dell'installazione (da config.json): nome, logo e colore principale.
 *
 * Il colore principale sostituisce `--mat-sys-primary*`, l'accento `--mat-sys-tertiary*`; le tinte derivate
 * (container, testo sopra il colore) si calcolano con color-mix e il contrasto.
 * Il resto della palette resta quello di prodotto (src/styles/_theme-colors.scss).
 */
@Injectable({ providedIn: 'root' })
export class BrandingService {
  private readonly document = inject(DOCUMENT);

  readonly name = signal(DEFAULT_BRANDING.name);
  readonly logoUrl = signal(DEFAULT_BRANDING.logoUrl);

  apply(branding: Branding | undefined): void {
    const name = branding?.name?.trim() || DEFAULT_BRANDING.name;
    this.name.set(name);
    this.logoUrl.set(branding?.logoUrl || DEFAULT_BRANDING.logoUrl);
    this.document.title = name;

    const color = branding?.primaryColor;
    if (color && HEX_COLOR.test(color)) {
      const style = this.document.documentElement.style;
      const onColor = isDark(color) ? '#ffffff' : '#000000';
      style.setProperty('--mat-sys-primary', color);
      style.setProperty('--mat-sys-on-primary', onColor);
      style.setProperty('--mat-sys-primary-container', `color-mix(in srgb, ${color} 18%, white)`);
      style.setProperty('--mat-sys-on-primary-container', `color-mix(in srgb, ${color} 70%, black)`);
      style.setProperty('--mat-sys-inverse-primary', `color-mix(in srgb, ${color} 45%, white)`);
      style.setProperty('--mat-sys-surface-tint', color);
      // Voce attiva del menu e chip selezionati (Material 3 usa il secondary container).
      style.setProperty('--mat-sys-secondary-container', `color-mix(in srgb, ${color} 14%, white)`);
      style.setProperty('--mat-sys-on-secondary-container', `color-mix(in srgb, ${color} 70%, black)`);
    }

    const accent = branding?.accentColor;
    if (accent && HEX_COLOR.test(accent)) {
      const style = this.document.documentElement.style;
      style.setProperty('--mat-sys-tertiary', accent);
      style.setProperty('--mat-sys-on-tertiary', isDark(accent) ? '#ffffff' : '#1d1d1b');
      style.setProperty('--mat-sys-tertiary-container', `color-mix(in srgb, ${accent} 22%, white)`);
      style.setProperty('--mat-sys-on-tertiary-container', `color-mix(in srgb, ${accent} 35%, black)`);
    }
  }
}

/** Luminanza relativa (WCAG) sotto 0.4: testo bianco sopra il colore. */
function isDark(hex: string): boolean {
  const channel = (i: number) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  const luminance = 0.2126 * channel(1) + 0.7152 * channel(3) + 0.0722 * channel(5);
  return luminance < 0.4;
}
