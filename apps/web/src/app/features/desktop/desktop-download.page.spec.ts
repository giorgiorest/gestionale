import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DesktopDownloadPage, DesktopRelease } from './desktop-download.page';

const release: DesktopRelease = {
  version: '0.1.0',
  commit: 'abc',
  date: '2026-11-23T10:00:00Z',
  setup: { file: 'civilbeefy-setup.exe', size: 5_242_880, sha256: 'aa'.repeat(32) },
  msi: { file: 'civilbeefy.msi', size: 6_291_456, sha256: 'bb'.repeat(32) },
  android: { file: 'civilbeefy.apk', size: 7_340_032, sha256: 'cc'.repeat(32) },
};

async function render(response: () => Promise<Response>): Promise<HTMLElement> {
  vi.stubGlobal('fetch', vi.fn(response));
  TestBed.configureTestingModule({
    imports: [DesktopDownloadPage],
    providers: [provideRouter([])],
  });
  const fixture = TestBed.createComponent(DesktopDownloadPage);
  await new Promise((resolve) => setTimeout(resolve));
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('DesktopDownloadPage', () => {
  afterEach(() => vi.unstubAllGlobals());

  it("offre l'installer, il pacchetto MSI, l'APK e l'indirizzo del server da inserire", async () => {
    const page = await render(async () => new Response(JSON.stringify(release)));

    expect(page.querySelector('[data-testid=download-setup]')?.getAttribute('href')).toBe(
      'downloads/civilbeefy-setup.exe',
    );
    expect(page.querySelector('[data-testid=download-msi]')?.getAttribute('href')).toBe(
      'downloads/civilbeefy.msi',
    );
    expect(page.querySelector('[data-testid=download-apk]')?.getAttribute('href')).toBe(
      'downloads/civilbeefy.apk',
    );
    expect(page.querySelector('[data-testid=android-not-available]')).toBeNull();
    expect(page.querySelector('[data-testid=server-url]')?.textContent).toBe(
      new URL('.', document.baseURI).href.replace(/\/$/, ''),
    );
    expect(page.textContent).toContain('5 MB');
    expect(page.textContent).toContain('7 MB');
    expect(page.textContent).toContain('cc'.repeat(32));
  });

  it("senza APK offre comunque Windows e spiega che l'app Android non c'è", async () => {
    const { android: _android, ...senzaApk } = release;
    const page = await render(async () => new Response(JSON.stringify(senzaApk)));

    expect(page.querySelector('[data-testid=download-setup]')).not.toBeNull();
    expect(page.querySelector('[data-testid=download-apk]')).toBeNull();
    expect(page.querySelector('[data-testid=android-not-available]')).not.toBeNull();
  });

  it('senza installer pubblicati spiega che il download non è disponibile', async () => {
    const page = await render(async () => new Response('', { status: 404 }));
    expect(page.querySelector('[data-testid=not-available]')).not.toBeNull();
    expect(page.querySelector('[data-testid=download-setup]')).toBeNull();
  });

  it('ignora un file che non è un elenco di installer (es. la pagina della SPA)', async () => {
    const page = await render(async () => new Response('<!doctype html>'));
    expect(page.querySelector('[data-testid=not-available]')).not.toBeNull();
  });
});
