import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RuntimeConfigService } from './runtime-config.service';

export type QueryValue = string | number | boolean | null | undefined;

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly runtimeConfig = inject(RuntimeConfigService);

  get<T>(path: string, params?: Record<string, QueryValue>): Observable<T> {
    return this.http.get<T>(this.url(path), { params: this.params(params) });
  }

  post<T>(path: string, body: unknown = {}, params?: Record<string, QueryValue>, headers?: Record<string, string>): Observable<T> {
    return this.http.post<T>(this.url(path), body, {
      params: this.params(params),
      headers: this.headers(headers)
    });
  }

  put<T>(path: string, body: unknown = {}, params?: Record<string, QueryValue>): Observable<T> {
    return this.http.put<T>(this.url(path), body, { params: this.params(params) });
  }

  patch<T>(path: string, body: unknown = {}, params?: Record<string, QueryValue>): Observable<T> {
    return this.http.patch<T>(this.url(path), body, { params: this.params(params) });
  }

  delete<T>(path: string, params?: Record<string, QueryValue>): Observable<T> {
    return this.http.delete<T>(this.url(path), { params: this.params(params) });
  }

  upload<T>(path: string, file: File, params?: Record<string, QueryValue>): Observable<T> {
    const data = new FormData();
    data.append('file', file, file.name);
    return this.http.post<T>(this.url(path), data, { params: this.params(params) });
  }

  download(path: string, params?: Record<string, QueryValue>, fallbackName = 'documento'): Observable<HttpResponse<Blob>> {
    return this.http.get(this.url(path), {
      params: this.params(params),
      observe: 'response',
      responseType: 'blob'
    });
  }

  private url(path: string): string {
    return `${this.runtimeConfig.apiBase}/${path.replace(/^\//, '')}`;
  }

  private params(values?: Record<string, QueryValue>): HttpParams {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(values ?? {})) {
      if (value === null || value === undefined || value === '') continue;
      params = params.set(key, String(value));
    }
    return params;
  }

  private headers(values?: Record<string, string>): HttpHeaders {
    let headers = new HttpHeaders();
    for (const [key, value] of Object.entries(values ?? {})) {
      headers = headers.set(key, value);
    }
    return headers;
  }
}

export function saveBlob(response: HttpResponse<Blob>, fallbackName: string): void {
  const disposition = response.headers.get('content-disposition') ?? '';
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  const plainMatch = disposition.match(/filename="?([^";]+)"?/i);
  const rawName = utf8Match?.[1] ?? plainMatch?.[1] ?? fallbackName;
  const fileName = decodeURIComponent(rawName.replace(/["']/g, ''));
  const blob = response.body ?? new Blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1500);
}

export function downloadFileName(response: HttpResponse<Blob>, fallbackName: string): string {
  const disposition = response.headers.get('content-disposition') ?? '';
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  const plainMatch = disposition.match(/filename="?([^";]+)"?/i);
  return decodeURIComponent((utf8Match?.[1] ?? plainMatch?.[1] ?? fallbackName).replace(/["']/g, ''));
}
