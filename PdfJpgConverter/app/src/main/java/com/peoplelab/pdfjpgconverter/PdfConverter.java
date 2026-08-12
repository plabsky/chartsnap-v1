package com.peoplelab.pdfjpgconverter;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public final class PdfConverter {

    public interface ProgressCallback {
        void onProgress(int completed, int total);
    }

    private PdfConverter() {}

    public static int pdfToJpg(
            Context context,
            Uri pdfUri,
            Uri outputTreeUri,
            String baseName,
            int jpegQuality,
            ProgressCallback progressCallback
    ) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(pdfUri, "r")) {
            if (pfd == null) throw new FileNotFoundException("PDF 파일을 열 수 없습니다.");

            try (PdfRenderer renderer = new PdfRenderer(pfd)) {
                int count = renderer.getPageCount();
                for (int i = 0; i < count; i++) {
                    PdfRenderer.Page page = renderer.openPage(i);
                    try {
                        int sourceW = page.getWidth();
                        int sourceH = page.getHeight();
                        float scale = Math.min(2.0f, 2400f / Math.max(sourceW, sourceH));
                        scale = Math.max(0.6f, scale);
                        int width = Math.max(1, Math.round(sourceW * scale));
                        int height = Math.max(1, Math.round(sourceH * scale));

                        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                        String filename = String.format(
                                java.util.Locale.US,
                                "%s_page_%03d.jpg",
                                safeBaseName(baseName),
                                i + 1
                        );

                        Uri outFile = DocumentsContract.createDocument(
                                resolver,
                                outputTreeUri,
                                "image/jpeg",
                                filename
                        );
                        if (outFile == null) {
                            bitmap.recycle();
                            throw new IOException("JPG 파일을 생성할 수 없습니다: " + filename);
                        }

                        try (OutputStream out = resolver.openOutputStream(outFile, "w")) {
                            if (out == null) {
                                bitmap.recycle();
                                throw new IOException("JPG 출력 스트림을 열 수 없습니다.");
                            }
                            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)) {
                                throw new IOException("JPG 압축에 실패했습니다.");
                            }
                        } finally {
                            bitmap.recycle();
                        }
                    } finally {
                        page.close();
                    }

                    if (progressCallback != null) {
                        progressCallback.onProgress(i + 1, count);
                    }
                }
                return count;
            }
        }
    }

    public static int jpgToPdf(
            Context context,
            List<Uri> imageUris,
            Uri outputPdfUri,
            ProgressCallback progressCallback
    ) throws IOException {
        if (imageUris == null || imageUris.isEmpty()) {
            throw new IOException("선택한 이미지가 없습니다.");
        }

        ContentResolver resolver = context.getContentResolver();
        PdfDocument document = new PdfDocument();
        Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        try {
            for (int i = 0; i < imageUris.size(); i++) {
                Bitmap bitmap = decodeScaledBitmap(resolver, imageUris.get(i), 2400);
                if (bitmap == null) {
                    throw new IOException("이미지를 읽을 수 없습니다: " + (i + 1) + "번째");
                }

                boolean landscape = bitmap.getWidth() > bitmap.getHeight();
                int pageWidth = landscape ? 842 : 595;
                int pageHeight = landscape ? 595 : 842;
                int margin = 24;

                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                        pageWidth,
                        pageHeight,
                        i + 1
                ).create();

                PdfDocument.Page page = document.startPage(pageInfo);
                Canvas canvas = page.getCanvas();
                canvas.drawColor(Color.WHITE);

                RectF content = new RectF(
                        margin,
                        margin,
                        pageWidth - margin,
                        pageHeight - margin
                );
                RectF destination = fitCenter(
                        bitmap.getWidth(),
                        bitmap.getHeight(),
                        content
                );
                canvas.drawBitmap(bitmap, null, destination, bitmapPaint);
                document.finishPage(page);
                bitmap.recycle();

                if (progressCallback != null) {
                    progressCallback.onProgress(i + 1, imageUris.size());
                }
            }

            try (OutputStream out = resolver.openOutputStream(outputPdfUri, "w")) {
                if (out == null) throw new IOException("PDF 출력 파일을 열 수 없습니다.");
                document.writeTo(out);
            }
            return imageUris.size();
        } finally {
            document.close();
        }
    }

    private static Bitmap decodeScaledBitmap(ContentResolver resolver, Uri uri, int maxDimension)
            throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, bounds);
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / sample > maxDimension * 2) {
            sample *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return null;
            return BitmapFactory.decodeStream(in, null, options);
        }
    }

    private static RectF fitCenter(int imageW, int imageH, RectF bounds) {
        float scale = Math.min(bounds.width() / imageW, bounds.height() / imageH);
        float w = imageW * scale;
        float h = imageH * scale;
        float left = bounds.left + (bounds.width() - w) / 2f;
        float top = bounds.top + (bounds.height() - h) / 2f;
        return new RectF(left, top, left + w, top + h);
    }

    private static String safeBaseName(String name) {
        if (name == null || name.trim().isEmpty()) return "converted";
        String base = name.replaceAll("(?i)\\.pdf$", "");
        base = base.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return base.isEmpty() ? "converted" : base;
    }
}
