import QRCode from 'qrcode';

export async function toQrDataUrl(value: string): Promise<string> {
  return QRCode.toDataURL(value, {
    errorCorrectionLevel: 'M',
    margin: 2,
    width: 360,
    color: {
      dark: '#0f172a',
      light: '#ffffff'
    }
  });
}

export function scanTokenFromText(value: string): string {
  const text = value.trim();
  if (!text) return '';
  try {
    const parsed = JSON.parse(text) as { token?: unknown };
    if (typeof parsed.token === 'string') return parsed.token.trim();
  } catch {
    // El contenido del QR no es JSON; se usa como token directo.
  }
  const url = text.match(/[?&]token=([^&\s]+)/);
  if (url?.[1]) return decodeURIComponent(url[1]);
  return text;
}
