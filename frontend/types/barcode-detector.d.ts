/**
 * Minimal typing for the Barcode Detection API, which TypeScript's DOM library
 * does not ship. Only the surface the scanner touches is declared.
 *
 * Chromium-only today; the scanner feature-detects before using it.
 */
interface DetectedBarcode {
  rawValue: string;
  format: string;
  boundingBox: DOMRectReadOnly;
}

declare class BarcodeDetector {
  constructor(options?: { formats?: string[] });
  detect(source: CanvasImageSource | Blob | ImageData): Promise<DetectedBarcode[]>;
  static getSupportedFormats(): Promise<string[]>;
}

interface Window {
  BarcodeDetector?: typeof BarcodeDetector;
}
