import { ConfigurazioneAppService, normalizeServerUrl } from './configurazione-app';

describe('normalizeServerUrl', () => {
  it('aggiunge https e toglie la barra finale', () => {
    expect(normalizeServerUrl(' gestionale.cliente.it/ ')).toBe('https://gestionale.cliente.it');
    expect(normalizeServerUrl('http://localhost:4200')).toBe('http://localhost:4200');
    expect(normalizeServerUrl('https://srv.example.com/gestionale/')).toBe('https://srv.example.com/gestionale');
  });
});

describe('AppConfigService (web)', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('legge config.json dallo stesso sito', async () => {
    const config = { apiUrl: '/api', auth: { authority: 'http://kc/realms/gestionale', clientId: 'gestionale-web' } };
    const fetchMock = vi.fn(async () => new Response(JSON.stringify(config)));
    vi.stubGlobal('fetch', fetchMock);

    const service = new ConfigurazioneAppService();
    expect(service.desktop).toBe(false);
    expect(await service.load()).toEqual(config);
    expect(fetchMock).toHaveBeenCalledWith('config.json', { cache: 'no-cache' });
  });

  it('rifiuta un file che non è una configurazione del gestionale', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('{"foo":1}')));
    await expect(new ConfigurazioneAppService().load()).rejects.toThrow('non è una configurazione');
  });
});
