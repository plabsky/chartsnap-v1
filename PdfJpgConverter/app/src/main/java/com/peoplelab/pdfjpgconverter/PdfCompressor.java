package com.peoplelab.pdfjpgconverter;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
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
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class PdfCompressor {
    public interface ProgressCallback {
        void onProgress(int completed, int total);
    }

    public static final class Result {
        public final int pages;
        public final long originalBytes;
        public final long outputBytes;
        public final boolean keptOriginal;

        Result(int pages, long originalBytes, long outputBytes, boolean keptOriginal) {
            this.pages = pages;
            this.originalBytes = originalBytes;
            this.outputBytes = outputBytes;
            this.keptOriginal = keptOriginal;
        }

        public int reductionPercent() {
            if (originalBytes <= 0 || outputBytes <= 0 || outputBytes >= originalBytes) return 0;
            return Math.max(0, Math.min(99,
                    Math.round((1f - outputBytes / (float) originalBytes) * 100f)));
        }
    }

    private PdfCompressor() {}

    public static Result compress(
            Context context,
            Uri sourceUri,
            Uri outputUri,
            int preset,
            ProgressCallback callback
    ) throws IOException {
        int maxDimension;
        int jpegQuality;

        // 0: 추천, 1: 강력, 2: 고화질
        if (preset == 1) {
            maxDimension = 1050;
            jpegQuality = 55;
        } else if (preset == 2) {
            maxDimension = 2100;
            jpegQuality = 85;
        } else {
            maxDimension = 1500;
            jpegQuality = 72;
        }

        ContentResolver resolver = context.getContentResolver();
        long originalSize = getSize(context, sourceUri);
        File tempFile = File.createTempFile("pdf_optimized_", ".pdf", context.getCacheDir());
        int pages;

        try {
            try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(sourceUri, "r")) {
                if (pfd == null) throw new IOException("PDF 파일을 열 수 없습니다.");

                try (PdfRenderer renderer = new PdfRenderer(pfd)) {
                    pages = renderer.getPageCount();
                    PdfDocument outputDocument = new PdfDocument();
                    try {
                        for (int i = 0; i < pages; i++) {
                            try (PdfRenderer.Page page = renderer.openPage(i)) {
                                Bitmap rendered = render(page, maxDimension);
                                Bitmap compressed = jpegRoundTrip(rendered, jpegQuality);
                                rendered.recycle();
                                appendPage(outputDocument, compressed,
                                        page.getWidth(), page.getHeight(), i + 1);
                                compressed.recycle();
                            }
                            if (callback != null) callback.onProgress(i + 1, pages);
                        }

                        try (FileOutputStream out = new FileOutputStream(tempFile)) {
                            outputDocument.writeTo(out);
                        }
                    } finally {
                        outputDocument.close();
                    }
                }
            }

            long optimizedSize = tempFile.length();
            boolean keepOriginal = originalSize > 0 && optimizedSize >= originalSize;

            if (keepOriginal) {
                try (InputStream in = resolver.openInputStream(sourceUri);
                     OutputStream out = resolver.openOutputStream(outputUri, "w")) {
                    if (in == null || out == null) throw new IOException("저장 파일을 열 수 없습니다.");
                    copy(in, out);
                }
                return new Result(pages, originalSize, originalSize, true);
            }

            try (InputStream in = new FileInputStream(tempFile);
                 OutputStream out = resolver.openOutputStream(outputUri, "w")) {
                if (out == null) throw new IOException("저장 파일을 열 수 없습니다.");
                copy(in, out);
            }

            return new Result(pages, originalSize, optimizedSize, false);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tempFile.delete();
        }
    }

    public static long getSize(Context context, Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        try (Cursor cursor = resolver.query(uri,
                new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index);
            }
        } catch (Exception ignored) {}

        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r")) {
            if (pfd != null && pfd.getStatSize() >= 0) return pfd.getStatSize();
        } catch (Exception ignored) {}
        return -1;
    }

    private static Bitmap render(PdfRenderer.Page page, int maxDimension) {
        int sourceW = page.getWidth();
        int sourceH = page.getHeight();
        float scale = Math.min(1f, maxDimension / (float) Math.max(sourceW, sourceH));
        int width = Math.max(1, Math.round(sourceW * scale));
        int height = Math.max(1, Math.round(sourceH * scale));

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        return bitmap;
    }

    private static Bitmap jpegRoundTrip(Bitmap source, int quality) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        if (!source.compress(Bitmap.CompressFormat.JPEG, quality, buffer)) {
            throw new IOException("이미지 압축에 실패했습니다.");
        }
        byte[] data = buffer.toByteArray();
        Bitmap decoded = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (decoded == null) throw new IOException("압축 이미지를 만들 수 없습니다.");
        return decoded;
    }

    private static void appendPage(PdfDocument document, Bitmap bitmap,
                                   int pageWidth, int pageHeight, int pageNumber) {
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(
                Math.max(1, pageWidth), Math.max(1, pageHeight), pageNumber).create();
        PdfDocument.Page page = document.startPage(info);
        Canvas canvas = page.getCanvas();
        canvas.drawColor(Color.WHITE);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        RectF target = fitCenter(bitmap.getWidth(), bitmap.getHeight(),
                new RectF(0, 0, pageWidth, pageHeight));
        canvas.drawBitmap(bitmap, null, target, paint);
        document.finishPage(page);
    }

    private static RectF fitCenter(int imageW, int imageH, RectF bounds) {
        float scale = Math.min(bounds.width() / imageW, bounds.height() / imageH);
        float width = imageW * scale;
        float height = imageH * scale;
        float left = bounds.left + (bounds.width() - width) / 2f;
        float top = bounds.top + (bounds.height() - height) / 2f;
        return new RectF(left, top, left + width, top + height);
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        out.flush();
    }
}
