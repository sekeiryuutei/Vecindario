import { AfterViewInit, Component, EventEmitter, OnDestroy, Output, signal } from '@angular/core';
import { Html5Qrcode } from 'html5-qrcode';
import { scanTokenFromText } from '../core/qr';

let scannerSequence = 0;

@Component({
  selector: 'app-qr-scanner',
  standalone: true,
  template: `
    <div class="stack" style="gap:10px">
      <div [id]="elementId" class="qr-reader" [class.scanning]="scanning()"></div>
      @if (error()) { <div class="alert alert-error">{{ error() }}</div> }
      <div class="row">
        @if (!scanning()) {
          <button type="button" class="btn btn-primary" (click)="start()">Activar cámara</button>
        } @else {
          <button type="button" class="btn btn-secondary" (click)="stop()">Detener cámara</button>
        }
        <span class="muted small">Apunta la cámara al código QR de la invitación.</span>
      </div>
    </div>
  `
})
export class QrScannerComponent implements AfterViewInit, OnDestroy {
  readonly elementId = `qr-reader-${++scannerSequence}`;
  readonly scanning = signal(false);
  readonly error = signal('');
  @Output() readonly decoded = new EventEmitter<string>();
  private scanner: Html5Qrcode | null = null;
  private initialized = false;

  ngAfterViewInit(): void {
    this.initialized = true;
  }

  async start(): Promise<void> {
    if (!this.initialized || this.scanning()) return;
    this.error.set('');
    this.scanner = new Html5Qrcode(this.elementId, { verbose: false });
    try {
      await this.scanner.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 250, height: 250 }, aspectRatio: 1 },
        (text) => {
          const token = scanTokenFromText(text);
          if (!token) return;
          void this.stop();
          this.decoded.emit(token);
        },
        () => undefined
      );
      this.scanning.set(true);
    } catch {
      this.error.set('No fue posible acceder a la cámara. Verifica los permisos del navegador o digita el código manualmente.');
      await this.stop();
    }
  }

  async stop(): Promise<void> {
    if (!this.scanner) {
      this.scanning.set(false);
      return;
    }
    const scanner = this.scanner;
    this.scanner = null;
    this.scanning.set(false);
    try {
      if (scanner.isScanning) await scanner.stop();
      scanner.clear();
    } catch {
      // La cámara ya estaba detenida.
    }
  }

  ngOnDestroy(): void {
    void this.stop();
  }
}
