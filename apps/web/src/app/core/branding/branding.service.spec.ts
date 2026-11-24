import { TestBed } from '@angular/core/testing';
import { BrandingService, DEFAULT_BRANDING } from './branding.service';

describe('BrandingService', () => {
  let service: BrandingService;
  const root = () => document.documentElement.style;

  beforeEach(() => {
    root().cssText = '';
    service = TestBed.inject(BrandingService);
  });

  it('senza personalizzazione usa il marchio di prodotto', () => {
    service.apply(undefined);
    expect(service.name()).toBe(DEFAULT_BRANDING.name);
    expect(service.logoUrl()).toBe(DEFAULT_BRANDING.logoUrl);
    expect(document.title).toBe('Civil Beefy');
    expect(root().getPropertyValue('--mat-sys-primary')).toBe('');
  });

  it('applica nome, logo e colore del cliente', () => {
    service.apply({ name: 'Rossi Gestionale', logoUrl: 'https://cdn.rossi.example/logo.svg', primaryColor: '#8B1F3A' });
    expect(service.name()).toBe('Rossi Gestionale');
    expect(service.logoUrl()).toBe('https://cdn.rossi.example/logo.svg');
    expect(document.title).toBe('Rossi Gestionale');
    expect(root().getPropertyValue('--mat-sys-primary')).toBe('#8B1F3A');
    expect(root().getPropertyValue('--mat-sys-on-primary')).toBe('#ffffff');
    expect(root().getPropertyValue('--mat-sys-secondary-container')).toContain('#8B1F3A');
  });

  it('applica il colore di accento del cliente con testo leggibile', () => {
    service.apply({ accentColor: '#F2B300' });
    expect(root().getPropertyValue('--mat-sys-tertiary')).toBe('#F2B300');
    expect(root().getPropertyValue('--mat-sys-on-tertiary')).toBe('#1d1d1b');
    service.apply({ accentColor: 'giallo' });
    expect(root().getPropertyValue('--mat-sys-tertiary')).toBe('#F2B300');
  });

  it('testo scuro sopra un colore chiaro', () => {
    service.apply({ primaryColor: '#F5D90A' });
    expect(root().getPropertyValue('--mat-sys-on-primary')).toBe('#000000');
  });

  it('ignora colori non validi (niente iniezione di CSS)', () => {
    service.apply({ primaryColor: 'red; background: url(x)' });
    expect(root().getPropertyValue('--mat-sys-primary')).toBe('');
  });
});
