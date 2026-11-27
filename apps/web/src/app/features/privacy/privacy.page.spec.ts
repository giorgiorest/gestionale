import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ConfigurazioneAppService } from '../../core/configurazione/configurazione-app';
import { PrivacyPage } from './privacy.page';

function render(legal?: { privacyUrl?: string; termsUrl?: string }): HTMLElement {
  TestBed.configureTestingModule({ imports: [PrivacyPage], providers: [provideRouter([])] });
  TestBed.inject(ConfigurazioneAppService).set({
    apiUrl: '/api',
    auth: { authority: 'https://kc', clientId: 'web' },
    legal,
  });
  const fixture = TestBed.createComponent(PrivacyPage);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('PrivacyPage', () => {
  it('elenca solo cookie tecnici e i dati salvati sul dispositivo', () => {
    const text = render().textContent ?? '';
    expect(text).toContain('cookie tecnici');
    expect(text).toContain('KEYCLOAK_SESSION');
    expect(text).toContain('IndexedDB');
  });

  it('collega informativa e condizioni del titolare se configurate', () => {
    const page = render({
      privacyUrl: 'https://rossi.example/privacy',
      termsUrl: 'https://rossi.example/termini',
    });
    const links = Array.from(page.querySelectorAll('a')).map((a) => a.getAttribute('href'));
    expect(links).toContain('https://rossi.example/privacy');
    expect(links).toContain('https://rossi.example/termini');
  });

  it("senza informativa configurata rimanda all'azienda", () => {
    expect(render().textContent).toContain('rivolgiti alla tua azienda');
  });
});
