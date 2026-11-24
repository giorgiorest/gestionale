import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../../core/api/api-url';
import { authInterceptor } from '../../core/auth/auth.interceptor';
import { AuthService } from '../../core/auth/auth.service';
import { ConsoleApiService } from './console-api.service';
import { ConsoleAuthService } from './console-auth.service';
import { endOfDayIso, slugify } from './console-labels';

describe('Console di piattaforma', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: '/api' },
        { provide: AuthService, useValue: { accessToken: () => 'token-dipendente' } },
        { provide: ConsoleAuthService, useValue: { accessToken: () => 'token-amministratore' } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it("il token di un dipendente non viene mai inviato all'API della piattaforma", async () => {
    const api = TestBed.inject(ConsoleApiService);
    const pending = api.tenants();
    const req = http.expectOne('/api/platform/tenants');
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-amministratore');
    req.flush([]);
    expect(await pending).toEqual([]);
  });

  it("le chiamate dell'app continuano a usare il token del dipendente", async () => {
    const pending = firstValueFrom(TestBed.inject(HttpClient).get('/api/me'));
    const req = http.expectOne('/api/me');
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-dipendente');
    req.flush({});
    await pending;
  });

  it("riporta il dettaglio dell'errore dell'API", async () => {
    const pending = TestBed.inject(ConsoleApiService).tenant('x');
    http
      .expectOne('/api/platform/tenants/x')
      .flush({ detail: 'Azienda non trovata' }, { status: 404, statusText: 'Not Found' });
    await expect(pending).rejects.toThrow('Azienda non trovata');
  });
});

describe('Etichette della console', () => {
  it('genera la sigla dal nome', () => {
    expect(slugify('Rossi S.r.l.')).toBe('rossi-s-r-l');
    expect(slugify('  Società Bianchi & Figli  ')).toBe('societa-bianchi-figli');
  });

  it('la scadenza vale fino a fine giornata', () => {
    expect(endOfDayIso(null)).toBeNull();
    const iso = endOfDayIso(new Date(2027, 0, 31))!;
    expect(new Date(iso).getHours()).toBe(23);
    expect(new Date(iso).getDate()).toBe(31);
  });
});
