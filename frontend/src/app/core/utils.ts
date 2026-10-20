export function toDateTimeInput(value: string | null | undefined): string {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function toDateInput(value: string | null | undefined): string {
  if (!value) return '';
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return value;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function toIsoDateTime(value: string | null | undefined): string | undefined {
  if (!value) return undefined;
  if (value.length === 16) return `${value}:00`;
  return value;
}

export function toIsoInstant(value: string | null | undefined): string | undefined {
  if (!value) return undefined;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return undefined;
  return date.toISOString();
}

export function today(): string {
  return toDateInput(new Date().toISOString());
}

export function addDays(days: number, from = new Date()): string {
  const date = new Date(from);
  date.setDate(date.getDate() + days);
  return toDateInput(date.toISOString());
}

export function startOfMonth(from = new Date()): string {
  return `${from.getFullYear()}-${String(from.getMonth() + 1).padStart(2, '0')}-01`;
}

export function endOfMonth(from = new Date()): string {
  const date = new Date(from.getFullYear(), from.getMonth() + 1, 0);
  return toDateInput(date.toISOString());
}

export function currentYear(): number {
  return new Date().getFullYear();
}

export function timeString(value: { hour?: number; minute?: number } | null | undefined): string {
  if (!value) return '';
  const hour = String(value.hour ?? 0).padStart(2, '0');
  const minute = String(value.minute ?? 0).padStart(2, '0');
  return `${hour}:${minute}`;
}

export function toLocalTime(value: string | null | undefined): { hour: number; minute: number; second: number; nano: number } {
  const [hour = '0', minute = '0'] = (value || '00:00').split(':');
  return { hour: Number(hour), minute: Number(minute), second: 0, nano: 0 };
}

export function initials(name: string | null | undefined): string {
  const parts = (name ?? '').trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return '?';
  return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

export function describeError(error: unknown): string {
  const candidate = error as { error?: { message?: string; error?: string }; message?: string };
  return candidate?.error?.message ?? candidate?.error?.error ?? candidate?.message ?? 'No fue posible completar la solicitud.';
}
