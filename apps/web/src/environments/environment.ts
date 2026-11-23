// Ambiente di sviluppo (ng serve, test). In produzione viene sostituito da environment.prod.ts.
// URL dell'API e del provider di identità NON stanno qui: arrivano a runtime da config.json,
// così la stessa build si installa presso clienti diversi senza ricompilare.
export const environment = {
  production: false,
};
