package com.peoplelab.pdfjpgconverter;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PdfConverter {

    public interface ProgressCallback {
        void onProgress(int completed, int total);
    }

    public static final class PageSpec {
        public final int sourceIndex;
        public int rotation;

        public PageSpec(int sourceIndex) {
            this(sourceIndex, 0);
        }

        public PageSpec(int sourceIndex, int rotation) {
            this.sourceIndex = sourceIndex;
            this.rotation = normalizeRotation(rotation);
        }
    }

    private PdfConverter() {}

    public static int getPdfPageCount(Context context, Uri pdfUri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(pdfUri, "r")) {
            if (pfd == null) throw new FileNotFoundException("PDF 파일을 열 수 없습니다.");
            try (PdfRenderer renderer = new PdfRenderer(pfd)) {
                return renderer.getPageCount();
            }
        }
    }

    public static List<PageSpec> createDefaultPageSpecs(int pageCount) {
        List<PageSpec> specs = new ArrayList<>();
        for (int i = 0; i < pageCount; i++) specs.add(new PageSpec(i));
        return specs;
    }

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
                    try (PdfRenderer.Page page = renderer.openPage(i)) {
                        Bitmap bitmap = renderPageBitmap(page, 2200);
                        String filename = String.format(
                                Locale.US,
                                "%s_page_%03d.jpg",
                                safeBaseName(baseName),
                                i + 1
                        );

                        Uri outFile = createDocumentInTree(
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
                    }

                    if (progressCallback != null) progressCallback.onProgress(i + 1, count);
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

                RectF content = new RectF(margin, margin, pageWidth - margin, pageHeight - margin);
                RectF destination = fitCenter(bitmap.getWidth(), bitmap.getHeight(), content);
                canvas.drawBitmap(bitmap, null, destination, bitmapPaint);
                document.finishPage(page);
                bitmap.recycle();

                if (progressCallback != null) {
                    progressCallback.onProgress(i + 1, imageUris.size());
                }
            }

            writePdfDocument(resolver, document, outputPdfUri);
            return imageUris.size();
        } finally {
            document.close();
        }
    }

    public static int mergePdfs(
            Context context,
            List<Uri> pdfUris,
            Uri outputPdfUri,
            ProgressCallback progressCallback
    ) throws IOException {
        if (pdfUris == null || pdfUris.size() < 2) {
            throw new IOException("합칠 PDF를 2개 이상 선택해 주세요.");
        }

        int totalPages = 0;
        for (Uri uri : pdfUris) totalPages += getPdfPageCount(context, uri);

        ContentResolver resolver = context.getContentResolver();
        PdfDocument output = new PdfDocument();
        int completed = 0;
        int outputPageNumber = 1;

        try {
            for (Uri uri : pdfUris) {
                try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r")) {
                    if (pfd == null) throw new IOException("PDF 파일을 열 수 없습니다.");
                    try (PdfRenderer renderer = new PdfRenderer(pfd)) {
                        for (int i = 0; i < renderer.getPageCount(); i++) {
                            try (PdfRenderer.Page source = renderer.openPage(i)) {
                                appendRenderedPage(output, source, 0, outputPageNumber++);
                            }
                            completed++;
                            if (progressCallback != null) {
                                progressCallback.onProgress(completed, totalPages);
                            }
                        }
                    }
                }
            }
            writePdfDocument(resolver, output, outputPdfUri);
            return completed;
        } finally {
            output.close();
        }
    }

    public static int splitPdf(
            Context context,
            Uri pdfUri,
            Uri outputTreeUri,
            String baseName,
            ProgressCallback progressCallback
    ) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(pdfUri, "r")) {
            if (pfd == null) throw new IOException("PDF 파일을 열 수 없습니다.");
            try (PdfRenderer renderer = new PdfRenderer(pfd)) {
                int total = renderer.getPageCount();
                for (int i = 0; i < total; i++) {
                    PdfDocument singlePagePdf = new PdfDocument();
                    try {
                        try (PdfRenderer.Page source = renderer.openPage(i)) {
                            appendRenderedPage(singlePagePdf, source, 0, 1);
                        }

                        String filename = String.format(
                                Locale.US,
                                "%s_page_%03d.pdf",
                                safeBaseName(baseName),
                                i + 1
                        );
                        Uri out = createDocumentInTree(
                                resolver,
                                outputTreeUri,
                                "application/pdf",
                                filename
                        );
                        if (out == null) throw new IOException("분할 PDF를 생성할 수 없습니다.");
                        writePdfDocument(resolver, singlePagePdf, out);
                    } finally {
                        singlePagePdf.close();
                    }

                    if (progressCallback != null) progressCallback.onProgress(i + 1, total);
                }
                return total;
            }
        }
    }

    public static int saveEditedPdf(
            Context context,
            Uri sourcePdfUri,
            List<PageSpec> pageSpecs,
            Uri outputPdfUri,
            ProgressCallback progressCallback
    ) throws IOException {
        if (pageSpecs == null || pageSpecs.isEmpty()) {
            throw new IOException("저장할 페이지가 없습니다.");
        }

        ContentResolver resolver = context.getContentResolver();
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(sourcePdfUri, "r")) {
            if (pfd == null) throw new IOException("PDF 파일을 열 수 없습니다.");
            try (PdfRenderer renderer = new PdfRenderer(pfd)) {
                PdfDocument output = new PdfDocument();
                try {
                    for (int i = 0; i < pageSpecs.size(); i++) {
                        PageSpec spec = pageSpecs.get(i);
                        if (spec.sourceIndex < 0 || spec.sourceIndex >= renderer.getPageCount()) {
                            throw new IOException("잘못된 페이지 정보가 있습니다.");
                        }
                        try (PdfRenderer.Page source = renderer.openPage(spec.sourceIndex)) {
                            appendRenderedPage(output, source, spec.rotation, i + 1);
                        }
                        if (progressCallback != null) {
                            progressCallback.onProgress(i + 1, pageSpecs.size());
                        }
                    }
                    writePdfDocument(resolver, output, outputPdfUri);
                    return pageSpecs.size();
                } finally {
                    output.close();
                }
            }
        }
    }

    private static void appendRenderedPage(
            PdfDocument output,
            PdfRenderer.Page source,
            int rotation,
            int outputPageNumber
    ) {
        int normalizedRotation = normalizeRotation(rotation);
        Bitmap bitmap = renderPageBitmap(source, 2200);
        Bitmap finalBitmap = bitmap;

        if (normalizedRotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(normalizedRotation);
            finalBitmap = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    matrix,
                    true
            );
            bitmap.recycle();
        }

        boolean quarterTurn = normalizedRotation == 90 || normalizedRotation == 270;
        int pageWidth = quarterTurn ? source.getHeight() : source.getWidth();
        int pageHeight = quarterTurn ? source.getWidth() : source.getHeight();
        pageWidth = Math.max(1, pageWidth);
        pageHeight = Math.max(1, pageHeight);

        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                pageWidth,
                pageHeight,
                outputPageNumber
        ).create();
        PdfDocument.Page outputPage = output.startPage(pageInfo);
        Canvas canvas = outputPage.getCanvas();
        canvas.drawColor(Color.WHITE);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        RectF destination = fitCenter(
                finalBitmap.getWidth(),
                finalBitmap.getHeight(),
                new RectF(0, 0, pageWidth, pageHeight)
        );
        canvas.drawBitmap(finalBitmap, null, destination, paint);
        output.finishPage(outputPage);
        finalBitmap.recycle();
    }

    private static Bitmap renderPageBitmap(PdfRenderer.Page page, int maxDimension) {
        int sourceW = page.getWidth();
        int sourceH = page.getHeight();
        float scale = Math.min(2.0f, maxDimension / (float) Math.max(sourceW, sourceH));
        scale = Math.max(1.0f, scale);
        int width = Math.max(1, Math.round(sourceW * scale));
        int height = Math.max(1, Math.round(sourceH * scale));

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        return bitmap;
    }

    private static void writePdfDocument(ContentResolver resolver, PdfDocument document, Uri outputUri)
            throws IOException {
        try (OutputStream out = resolver.openOutputStream(outputUri, "w")) {
            if (out == null) throw new IOException("PDF 출력 파일을 열 수 없습니다.");
            document.writeTo(out);
        }
    }

    private static Uri createDocumentInTree(
            ContentResolver resolver,
            Uri treeUri,
            String mimeType,
            String displayName
    ) throws FileNotFoundException {
        String treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId);
        return DocumentsContract.createDocument(resolver, parent, mimeType, displayName);
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
        while (largest / sample > maxDimension * 2) sample *= 2;

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

    private static int normalizeRotation(int rotation) {
        int value = rotation % 360;
        if (value < 0) value += 360;
        return ((value + 45) / 90 * 90) % 360;
    }

    private static String safeBaseName(String name) {
        if (name == null || name.trim().isEmpty()) return "converted";
        String base = name.replaceAll("(?i)\\.pdf$", "");
        base = base.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return base.isEmpty() ? "converted" : base;
    }
}
