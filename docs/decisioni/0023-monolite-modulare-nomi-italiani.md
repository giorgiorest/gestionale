# ADR 0023 — Monolite modulare e nomi in italiano

Stato: accettata

## Contesto

Il backend era un unico albero di 24 pacchetti piatti in inglese. Nessuna regola impediva a un
modulo di dipendere da qualsiasi altro: c'erano già due cicli (`auth` ↔ `notifications`,
`dailyreports` ↔ `workentries`) e `web` conosceva tutto. Il progetto è in italiano nel dominio, nella
documentazione e nei commenti, ma non nei nomi di pacchetti e classi, e chi lavora al codice (anche
con Claude) deve tradurre ogni volta tra i due mondi.

## Decisione

1. **Monolite modulare.** Resta un solo servizio e un solo deploy. Ogni sotto-pacchetto diretto di
   `it.gestionale.api` è un modulo; `piattaforma.identita` appartiene a `piattaforma`.
2. **Dipendenze solo verso il basso, senza cicli**, in cinque strati:

   | Strato | Moduli |
   |--------|--------|
   | Orchestrazione | `sincronizzazione`, `piattaforma`, `demo`, `errori`, `configurazione`, `salute` |
   | Dominio | `clienti`, `fornitori`, `risorse`, `commesse`, `assegnazioni`, `lavorazioni`, `rapportini`, `riepilogoore`, `costimanodopera` |
   | Trasversali | `notifiche`, `allegati`, `ia`, `osservabilita` |
   | Sicurezza | `accesso`, `licenze` |
   | Fondamenta | `tenant`, `condiviso` |

   Un modulo può usare quelli dei propri strati inferiori (e dello stesso strato, purché senza
   cicli), mai quelli superiori. Lo verifica `ArchitetturaModulareTest` (ArchUnit, licenza Apache 2.0,
   solo in test, nessun database: gira in pochi secondi insieme agli altri test).
3. **Reagire a un modulo superiore = evento.** `accesso` non chiama più `notifiche` al primo accesso
   di un utente: pubblica `UtenteRegistrato` (evento di Spring, sincrono e nella stessa transazione)
   e `NotificaService` lo ascolta. Il comportamento è identico.
4. **Rapportino, righe ore e materiali sono un solo modulo (`rapportini`).** Il rapportino blocca la
   scrittura delle righe quando non è più in bozza e le righe alimentano il suo workflow: erano già
   un ciclo, e separarli avrebbe richiesto un'interfaccia senza altro scopo.
5. **Nomi in italiano per pacchetti e classi nuovi.** Restano in inglese solo i suffissi di ruolo
   tecnico (`Controller`, `Service`, `Repository`, `Dto`, `Config`, `Properties`, `Converter`,
   `Filter`, `Exception`, `Test`, `Watchdog`), i nomi di framework (`Keycloak`) e il nome tecnico
   `gestionale` (`GestionaleApiApplication`). `tenant` resta tale: è il termine tecnico già usato
   nei claim, nel database e nella documentazione.
6. **Fuori dal perimetro (contratti invariati):** URL REST, tabelle e colonne, campi JSON, nomi dei
   permessi e dei ruoli, proprietà di configurazione, nomi dei campi e dei metodi Java. Cambiarli
   romperebbe i client installati (desktop, Android, PWA in cache) o le installazioni esistenti.

## Alternative scartate

- **Spring Modulith**: verifica i moduli ma impone la convenzione «solo il pacchetto radice è
  pubblico», che qui significherebbe introdurre una facciata per modulo (oggi i moduli usano
  direttamente servizi e repository degli strati inferiori). Costo alto, beneficio piccolo adesso.
- **Moduli Maven separati**: isolamento più forte, ma build più lente, più file `pom.xml` e un
  refactoring molto più grande. Si può fare più avanti sopra questa stessa divisione, se serve.

## Conseguenze

- Una nuova entità di dominio si copia da `clienti/` (vedi ricettario in `CLAUDE.md`) e va nello
  strato «Dominio»; `ArchitetturaModulareTest` va aggiornato solo se si crea un modulo nuovo.
- Gli ADR precedenti citano ancora i vecchi nomi: la tabella qui sotto serve a tradurli.
- Il frontend non è toccato da questa decisione.

## Tabella di corrispondenza (classi principali)

I test seguono le stesse regole (`CustomerControllerTest` → `ClienteControllerTest`, …); le classi di
supporto sono `AbstractIntegrationTest` → `TestIntegrazioneBase`, `TestData` → `DatiDiTest`,
`TestJwt` → `JwtDiTest`.

#### `accesso` (era `auth`, `notifications`)

| Prima | Dopo |
|-------|------|
| `DomainEntityPermissions` | `PermessiEntitaDominio` |
| `Permission` | `Permesso` |
| `MeController` | `ProfiloController` |
| `UserDirectoryService` | `RubricaUtentiService` |
| `Role` | `Ruolo` |
| `RoleAuthoritiesConverter` | `RuoloAutoritaConverter` |
| `SecurityProperties` | `SicurezzaProperties` |
| `CurrentUser` | `UtenteCorrente` |

#### `allegati` (era `attachments`)

| Prima | Dopo |
|-------|------|
| `AttachmentsConfig` | `AllegatiConfig` |
| `AttachmentsProperties` | `AllegatiProperties` |
| `Attachment` | `Allegato` |
| `AttachmentController` | `AllegatoController` |
| `AttachmentDto` | `AllegatoDto` |
| `AttachmentNotFoundException` | `AllegatoNonTrovatoException` |
| `InvalidAttachmentException` | `AllegatoNonValidoException` |
| `AttachmentRepository` | `AllegatoRepository` |
| `AttachmentService` | `AllegatoService` |

#### `assegnazioni` (era `resourceassignments`)

| Prima | Dopo |
|-------|------|
| `ResourceAssignment` | `AssegnazioneRisorsa` |
| `ResourceAssignmentController` | `AssegnazioneRisorsaController` |
| `ResourceAssignmentDto` | `AssegnazioneRisorsaDto` |
| `ResourceAssignmentRepository` | `AssegnazioneRisorsaRepository` |
| `ResourceAssignmentService` | `AssegnazioneRisorsaService` |
| `UnknownAssignmentReferenceException` | `RiferimentoAssegnazioneSconosciutoException` |

#### `clienti` (era `customers`)

| Prima | Dopo |
|-------|------|
| `Customer` | `Cliente` |
| `CustomerController` | `ClienteController` |
| `CustomerDto` | `ClienteDto` |
| `CustomerRepository` | `ClienteRepository` |
| `CustomerService` | `ClienteService` |

#### `commesse` (era `commesse`)

| Prima | Dopo |
|-------|------|
| `UnknownCustomerException` | `ClienteSconosciutoException` |
| `CommessaStato` | `StatoCommessa` |
| `CommessaStatoConverter` | `StatoCommessaConverter` |

#### `condiviso` (era `web`)

| Prima | Dopo |
|-------|------|
| `PageDto` | `PaginaDto` |

#### `configurazione` (era `config`)

| Prima | Dopo |
|-------|------|
| `CorsProperties` | `CorsProperties` (spostata da `config`) |
| `ClockConfig` | `OrologioConfig` |
| `SecurityConfig` | `SicurezzaConfig` |

#### `costimanodopera` (era `laborcostreport`)

| Prima | Dopo |
|-------|------|
| `WorkActivityLaborCostAggregate` | `AggregatoCostoManodoperaLavorazione` |
| `LaborCostReportController` | `CostiManodoperaController` |
| `LaborCostReportDto` | `CostiManodoperaDto` |
| `LaborCostReportRepository` | `CostiManodoperaRepository` |
| `LaborCostReportService` | `CostiManodoperaService` |
| `WorkActivityLaborCostDto` | `CostoManodoperaLavorazioneDto` |
| `InvalidLaborCostReportRangeException` | `IntervalloCostiManodoperaNonValidoException` |

#### `demo` (era `demo`)

| Prima | Dopo |
|-------|------|
| `DemoTenantSeeder` | `PopolatoreTenantDemo` |

#### `errori` (era `web`)

| Prima | Dopo |
|-------|------|
| `ApiExceptionHandler` | `GestoreErroriApi` |

#### `fornitori` (era `suppliers`)

| Prima | Dopo |
|-------|------|
| `Supplier` | `Fornitore` |
| `SupplierController` | `FornitoreController` |
| `SupplierDto` | `FornitoreDto` |
| `SupplierRepository` | `FornitoreRepository` |
| `SupplierService` | `FornitoreService` |

#### `ia` (era `ai`)

| Prima | Dopo |
|-------|------|
| `AssistantController` | `AssistenteController` |
| `AssistantService` | `AssistenteService` |
| `AiToolCall` | `ChiamataStrumentoIa` |
| `AiUnavailableException` | `IaNonDisponibileException` |
| `AiProperties` | `IaProperties` |
| `AiMessage` | `MessaggioIa` |
| `AiProvider` | `ProviderIa` |
| `NoopAiProvider` | `ProviderIaNullo` |
| `AiQuotaService` | `QuotaIaService` |
| `AiQuotaExceededException` | `QuotaIaSuperataException` |
| `AiRequest` | `RichiestaIa` |
| `AiResponse` | `RispostaIa` |
| `AiTool` | `StrumentoIa` |
| `AiUsage` | `UtilizzoIa` |
| `AiUsageRepository` | `UtilizzoIaRepository` |

#### `lavorazioni` (era `workactivities`)

| Prima | Dopo |
|-------|------|
| `WorkActivity` | `Lavorazione` |
| `WorkActivityController` | `LavorazioneController` |
| `WorkActivityDto` | `LavorazioneDto` |
| `WorkActivityRepository` | `LavorazioneRepository` |
| `WorkActivityService` | `LavorazioneService` |
| `UnknownWorkActivityReferenceException` | `RiferimentoLavorazioneSconosciutoException` |
| `WorkActivityStatus` | `StatoLavorazione` |
| `WorkActivityStatusConverter` | `StatoLavorazioneConverter` |

#### `licenze` (era `licensing`)

| Prima | Dopo |
|-------|------|
| `Entitlements` | `Abilitazioni` |
| `EntitlementsController` | `AbilitazioniController` |
| `EntitlementService` | `AbilitazioniService` |
| `Features` | `Funzionalita` |
| `FeatureNotEnabledException` | `FunzionalitaNonAbilitataException` |
| `License` | `Licenza` |
| `InvalidLicenseException` | `LicenzaNonValidaException` |
| `LicenseRepository` | `LicenzaRepository` |
| `LicensingProperties` | `LicenzeProperties` |
| `LicenseVerifier` | `VerificatoreLicenze` |

#### `notifiche` (era `notifications`)

| Prima | Dopo |
|-------|------|
| `AnnouncementController` | `ComunicazioneController` |
| `UnknownRecipientException` | `DestinatarioSconosciutoException` |
| `EmailDispatcher` | `InviatoreEmail` |
| `NotificationController` | `NotificaController` |
| `NotificationDtos` | `NotificaDtos` |
| `NotificationNotFoundException` | `NotificaNonTrovataException` |
| `NotificationService` | `NotificaService` |
| `NotificationConfig` | `NotificheConfig` |
| `NotificationProperties` | `NotificheProperties` |
| `NewNotification` | `NuovaNotifica` |
| `Audience` | `Pubblico` |

#### `osservabilita` (era `observability`)

| Prima | Dopo |
|-------|------|
| `HealthAlertWatchdog` | `AllarmiSaluteWatchdog` |
| `RequestLoggingContextFilter` | `ContestoLogRichiestaFilter` |
| `HealthAlertNotifier` | `NotificatoreAllarmiSalute` |
| `ObservabilityProperties` | `OsservabilitaProperties` |

#### `piattaforma` (era `platform`)

| Prima | Dopo |
|-------|------|
| `PlatformAdmin` | `AmministratorePiattaforma` |
| `TemporaryPasswords` | `PasswordTemporanee` |
| `PlatformController` | `PiattaformaController` |
| `PlatformDtos` | `PiattaformaDtos` |
| `PlatformException` | `PiattaformaException` |
| `PlatformProperties` | `PiattaformaProperties` |
| `PlatformService` | `PiattaformaService` |
| `PlatformSecurityConfig` | `PiattaformaSicurezzaConfig` |

#### `piattaforma.identita` (era `platform.identity`)

| Prima | Dopo |
|-------|------|
| `IdentityAdmin` | `AmministrazioneIdentita` |
| `IdentityAdminException` | `AmministrazioneIdentitaException` |
| `KeycloakIdentityAdmin` | `KeycloakAmministrazioneIdentita` |
| `NewTenantUser` | `NuovoUtenteTenant` |
| `TenantUser` | `UtenteTenant` |

#### `rapportini` (era `dailyreportmaterials`, `dailyreports`, `workentries`)

| Prima | Dopo |
|-------|------|
| `DailyReportMaterial` | `MaterialeRapportino` |
| `DailyReportMaterialController` | `MaterialeRapportinoController` |
| `DailyReportMaterialDto` | `MaterialeRapportinoDto` |
| `DailyReportMaterialRepository` | `MaterialeRapportinoRepository` |
| `DailyReportMaterialService` | `MaterialeRapportinoService` |
| `DailyReport` | `Rapportino` |
| `DailyReportController` | `RapportinoController` |
| `DailyReportDto` | `RapportinoDto` |
| `MissingDailyReportWatchdog` | `RapportinoMancanteWatchdog` |
| `DailyReportNotFoundException` | `RapportinoNonTrovatoException` |
| `DailyReportRepository` | `RapportinoRepository` |
| `DailyReportService` | `RapportinoService` |
| `UnknownCommessaReferenceException` | `RiferimentoCommessaSconosciutoException` |
| `UnknownDailyReportMaterialReferenceException` | `RiferimentoMaterialeSconosciutoException` |
| `UnknownWorkEntryReferenceException` | `RiferimentoRigaOreSconosciutoException` |
| `WorkEntry` | `RigaOre` |
| `WorkEntryController` | `RigaOreController` |
| `WorkEntryDto` | `RigaOreDto` |
| `WorkEntryRepository` | `RigaOreRepository` |
| `WorkEntryService` | `RigaOreService` |
| `WorkEntryCostSnapshot` | `SnapshotCostoRigaOre` |
| `WorkEntryCostSnapshotRepository` | `SnapshotCostoRigaOreRepository` |
| `DailyReportStatus` | `StatoRapportino` |
| `DailyReportStatusConverter` | `StatoRapportinoConverter` |
| `InvalidDailyReportStateException` | `StatoRapportinoNonValidoException` |

#### `riepilogoore` (era `hoursreport`)

| Prima | Dopo |
|-------|------|
| `DailyWorkEntryAggregate` | `AggregatoOreGiornaliere` |
| `InvalidHoursReportRangeException` | `IntervalloRiepilogoOreNonValidoException` |
| `WorkActivityHoursDto` | `OreLavorazioneDto` |
| `ResourceHoursDto` | `OreRisorsaDto` |
| `WeeklyHoursDto` | `OreSettimanaliDto` |
| `HoursReportController` | `RiepilogoOreController` |
| `HoursReportDto` | `RiepilogoOreDto` |
| `HoursReportRepository` | `RiepilogoOreRepository` |
| `HoursReportService` | `RiepilogoOreService` |

#### `risorse` (era `resources`)

| Prima | Dopo |
|-------|------|
| `UnknownTeamLeadException` | `CapoSquadraSconosciutoException` |
| `ResourceLaborCostDto` | `CostoManodoperaRisorsaDto` |
| `Resource` | `Risorsa` |
| `ResourceController` | `RisorsaController` |
| `ResourceDto` | `RisorsaDto` |
| `ResourceRepository` | `RisorsaRepository` |
| `UnknownResourceException` | `RisorsaSconosciutaException` |
| `ResourceService` | `RisorsaService` |

#### `salute` (era `health`)

| Prima | Dopo |
|-------|------|
| `HealthController` | `SaluteController` |

#### `sincronizzazione` (era `sync`)

| Prima | Dopo |
|-------|------|
| `InvalidSyncCursorException` | `CursoreNonValidoException` |
| `SyncChangeDto` | `ModificaDto` |
| `SyncChangesDto` | `ModificheDto` |
| `SyncController` | `SincronizzazioneController` |
| `SyncService` | `SincronizzazioneService` |
| `SyncCursorEntry` | `VoceCursore` |

#### `tenant` (era `tenancy`)

| Prima | Dopo |
|-------|------|
| `TenantAccessService` | `AccessoTenantService` |
| `TenantContext` | `ContestoTenant` |
| `WorkerContext` | `ContestoWorker` |
| `TenantAwareDataSource` | `DataSourceTenant` |
| `TenantDataSourceConfig` | `DataSourceTenantConfig` |
| `LowercaseEnumConverter` | `EnumMinuscoloConverter` |
| `LowercaseEnumConverters` | `EnumMinuscoloConverters` |
| `TenantStatus` | `StatoTenant` |
| `Tenant` | `Tenant` (spostata da `tenancy`) |
| `TenantFilter` | `TenantFilter` (spostata da `tenancy`) |
| `MissingTenantException` | `TenantMancanteException` |
| `TenancyProperties` | `TenantProperties` |
| `TenantRepository` | `TenantRepository` (spostata da `tenancy`) |
| `TenantKind` | `TipoTenant` |
