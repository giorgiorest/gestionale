/**
 * Risposta delle liste paginate lato server (ADR 0014), riusabile da ogni entità con una lista
 * (customers, commesse, risorse, …). Forma esplicita: mai la serializzazione di `Page` di Spring.
 */
export interface PaginaDto<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
