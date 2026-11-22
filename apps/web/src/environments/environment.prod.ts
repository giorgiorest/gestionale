// Ambiente di produzione: l'API è servita dallo stesso dominio (reverse proxy nginx su /api).
export const environment = {
  production: true,
  /** URL base dell'API Spring Boot. */
  apiUrl: '/api',
};
