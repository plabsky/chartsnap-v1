package com.peoplelab.pdfjpgconverter;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_CONVERT_PDF = 1;
    private static final int PICK_IMAGES = 2;
    private static final int PICK_JPG_FOLDER = 3;
    private static final int SAVE_IMAGES_PDF = 4;
    private static final int PICK_MERGE_PDFS = 5;
    private static final int SAVE_MERGED_PDF = 6;
    private static final int PICK_SPLIT_PDF = 7;
    private static final int PICK_SPLIT_FOLDER = 8;
    private static final int PICK_EDIT_PDF = 9;
    private static final int SAVE_EDITED_PDF = 10;

    private Uri selectedConvertPdfUri;
    private String selectedConvertPdfName = "document.pdf";
    private final List<Uri> selectedImageUris = new ArrayList<>();

    private final List<Uri> mergePdfUris = new ArrayList<>();

    private Uri splitPdfUri;
    private String splitPdfName = "document.pdf";

    private Uri editPdfUri;
    private String editPdfName = "document.pdf";
    private final List<PdfConverter.PageSpec> editPageSpecs = new ArrayList<>();

    private TextView convertPdfStatus;
    private TextView imageStatus;
    private TextView mergeStatus;
    private TextView splitStatus;
    private TextView editStatus;
    private LinearLayout editorContainer;
    private Spinner qualitySpinner;
    private ProgressBar progressBar;
    private TextView progressText;

    private final List<Button> actionButtons = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final RewardedAdGate adGate = new RewardedAdGate();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        adGate.initialize(this);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, float sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.rgb(25, 31, 40));
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams matchParams(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        return params;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        actionButtons.add(button);
        return button;
    }

    private void addDivider(LinearLayout root) {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(224, 229, 236));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.topMargin = dp(24);
        params.bottomMargin = dp(4);
        root.addView(divider, params);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(247, 249, 252));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        root.addView(text("PDF 도구", 30, true));
        TextView version = text("v0.2 · 변환 · 합치기 · 나누기 · 페이지 편집", 14, false);
        root.addView(version, matchParams(4));
        TextView privacy = text("🔒 PDF/JPG 파일은 외부 서버에 업로드하지 않고 기기 안에서 처리합니다.", 13, false);
        root.addView(privacy, matchParams(10));

        addDivider(root);
        root.addView(text("1. PDF ↔ JPG 변환", 21, true), matchParams(14));

        convertPdfStatus = text("PDF를 선택해 주세요.", 14, false);
        root.addView(convertPdfStatus, matchParams(8));
        Button selectConvertPdf = button("PDF 선택");
        root.addView(selectConvertPdf, matchParams(8));

        root.addView(text("JPG 품질", 13, true), matchParams(8));
        qualitySpinner = new Spinner(this);
        String[] qualities = {"고화질 · 90%", "표준 · 80%", "용량 절약 · 65%"};
        qualitySpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, qualities));
        root.addView(qualitySpinner, matchParams(3));
        Button pdfToJpg = button("광고 보고 JPG로 변환");
        root.addView(pdfToJpg, matchParams(8));

        imageStatus = text("JPG 이미지를 선택해 주세요.", 14, false);
        root.addView(imageStatus, matchParams(16));
        Button selectImages = button("JPG 여러 장 선택");
        root.addView(selectImages, matchParams(8));
        Button jpgToPdf = button("광고 보고 PDF 만들기");
        root.addView(jpgToPdf, matchParams(8));

        addDivider(root);
        root.addView(text("2. PDF 합치기", 21, true), matchParams(14));
        TextView mergeDesc = text("PDF 2개 이상을 선택한 순서대로 하나의 PDF로 만듭니다.", 13, false);
        root.addView(mergeDesc, matchParams(5));
        mergeStatus = text("합칠 PDF를 선택해 주세요.", 14, false);
        root.addView(mergeStatus, matchParams(8));
        Button selectMerge = button("PDF 여러 개 선택");
        root.addView(selectMerge, matchParams(8));
        Button mergeButton = button("광고 보고 PDF 합치기");
        root.addView(mergeButton, matchParams(8));

        addDivider(root);
        root.addView(text("3. PDF 나누기", 21, true), matchParams(14));
        TextView splitDesc = text("PDF의 각 페이지를 개별 PDF 파일로 저장합니다.", 13, false);
        root.addView(splitDesc, matchParams(5));
        splitStatus = text("나눌 PDF를 선택해 주세요.", 14, false);
        root.addView(splitStatus, matchParams(8));
        Button selectSplit = button("PDF 선택");
        root.addView(selectSplit, matchParams(8));
        Button splitButton = button("광고 보고 페이지별로 나누기");
        root.addView(splitButton, matchParams(8));

        addDivider(root);
        root.addView(text("4. 페이지 편집", 21, true), matchParams(14));
        TextView editDesc = text("페이지 순서 변경 · 삭제 · 90° 회전을 한 뒤 새 PDF로 저장합니다.", 13, false);
        root.addView(editDesc, matchParams(5));
        editStatus = text("편집할 PDF를 선택해 주세요.", 14, false);
        root.addView(editStatus, matchParams(8));
        Button selectEdit = button("편집할 PDF 선택");
        root.addView(selectEdit, matchParams(8));

        editorContainer = new LinearLayout(this);
        editorContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(editorContainer, matchParams(8));

        Button saveEdited = button("광고 보고 편집본 저장");
        root.addView(saveEdited, matchParams(8));

        progressText = text("", 13, true);
        progressText.setGravity(Gravity.CENTER);
        progressText.setVisibility(View.GONE);
        root.addView(progressText, matchParams(20));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, matchParams(4));

        TextView footer = text("현재 개발용 Google 테스트 광고 사용 중", 12, false);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, matchParams(18));

        setContentView(scroll);

        selectConvertPdf.setOnClickListener(v -> openSinglePdf(PICK_CONVERT_PDF));
        pdfToJpg.setOnClickListener(v -> {
            if (selectedConvertPdfUri == null) {
                toast("먼저 PDF를 선택해 주세요.");
                return;
            }
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, PICK_JPG_FOLDER);
        });

        selectImages.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .setType("image/jpeg")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(intent, PICK_IMAGES);
        });
        jpgToPdf.setOnClickListener(v -> {
            if (selectedImageUris.isEmpty()) {
                toast("먼저 JPG 이미지를 선택해 주세요.");
                return;
            }
            createPdfDocument(SAVE_IMAGES_PDF, "images_" + timestamp() + ".pdf");
        });

        selectMerge.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .setType("application/pdf")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(intent, PICK_MERGE_PDFS);
        });
        mergeButton.setOnClickListener(v -> {
            if (mergePdfUris.size() < 2) {
                toast("합칠 PDF를 2개 이상 선택해 주세요.");
                return;
            }
            createPdfDocument(SAVE_MERGED_PDF, "merged_" + timestamp() + ".pdf");
        });

        selectSplit.setOnClickListener(v -> openSinglePdf(PICK_SPLIT_PDF));
        splitButton.setOnClickListener(v -> {
            if (splitPdfUri == null) {
                toast("먼저 나눌 PDF를 선택해 주세요.");
                return;
            }
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, PICK_SPLIT_FOLDER);
        });

        selectEdit.setOnClickListener(v -> openSinglePdf(PICK_EDIT_PDF));
        saveEdited.setOnClickListener(v -> {
            if (editPdfUri == null) {
                toast("먼저 편집할 PDF를 선택해 주세요.");
                return;
            }
            if (editPageSpecs.isEmpty()) {
                toast("저장할 페이지가 없습니다.");
                return;
            }
            createPdfDocument(SAVE_EDITED_PDF, "edited_" + stripPdf(editPdfName) + ".pdf");
        });
    }

    private void openSinglePdf(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .setType("application/pdf")
                .addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, requestCode);
    }

    private void createPdfDocument(int requestCode, String filename) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .setType("application/pdf")
                .putExtra(Intent.EXTRA_TITLE, filename)
                .addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == PICK_CONVERT_PDF) {
            Uri uri = data.getData();
            if (uri != null) {
                selectedConvertPdfUri = uri;
                tryPersistRead(uri);
                selectedConvertPdfName = displayName(uri);
                convertPdfStatus.setText("선택됨: " + selectedConvertPdfName);
            }
        } else if (requestCode == PICK_IMAGES) {
            selectedImageUris.clear();
            collectUris(data, selectedImageUris);
            imageStatus.setText("선택됨: JPG " + selectedImageUris.size() + "장");
        } else if (requestCode == PICK_JPG_FOLDER) {
            Uri tree = data.getData();
            if (tree != null) {
                persistTree(tree);
                adGate.showOrLoadThen(this, () -> convertPdfToJpg(tree));
            }
        } else if (requestCode == SAVE_IMAGES_PDF) {
            Uri out = data.getData();
            if (out != null) adGate.showOrLoadThen(this, () -> convertJpgToPdf(out));
        } else if (requestCode == PICK_MERGE_PDFS) {
            mergePdfUris.clear();
            collectUris(data, mergePdfUris);
            mergeStatus.setText("선택됨: PDF " + mergePdfUris.size() + "개");
        } else if (requestCode == SAVE_MERGED_PDF) {
            Uri out = data.getData();
            if (out != null) adGate.showOrLoadThen(this, () -> mergePdfs(out));
        } else if (requestCode == PICK_SPLIT_PDF) {
            Uri uri = data.getData();
            if (uri != null) {
                splitPdfUri = uri;
                tryPersistRead(uri);
                splitPdfName = displayName(uri);
                splitStatus.setText("선택됨: " + splitPdfName);
            }
        } else if (requestCode == PICK_SPLIT_FOLDER) {
            Uri tree = data.getData();
            if (tree != null) {
                persistTree(tree);
                adGate.showOrLoadThen(this, () -> splitPdf(tree));
            }
        } else if (requestCode == PICK_EDIT_PDF) {
            Uri uri = data.getData();
            if (uri != null) {
                editPdfUri = uri;
                tryPersistRead(uri);
                editPdfName = displayName(uri);
                loadEditPages();
            }
        } else if (requestCode == SAVE_EDITED_PDF) {
            Uri out = data.getData();
            if (out != null) adGate.showOrLoadThen(this, () -> saveEditedPdf(out));
        }
    }

    private void collectUris(Intent data, List<Uri> target) {
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                target.add(uri);
                tryPersistRead(uri);
            }
        } else if (data.getData() != null) {
            target.add(data.getData());
            tryPersistRead(data.getData());
        }
    }

    private void loadEditPages() {
        setBusy(true, "PDF 페이지를 읽는 중…");
        Uri source = editPdfUri;
        executor.execute(() -> {
            try {
                int count = PdfConverter.getPdfPageCount(this, source);
                List<PdfConverter.PageSpec> defaults = PdfConverter.createDefaultPageSpecs(count);
                runOnUiThread(() -> {
                    editPageSpecs.clear();
                    editPageSpecs.addAll(defaults);
                    editStatus.setText("선택됨: " + editPdfName + " · " + count + "페이지");
                    rebuildEditorRows();
                    setBusy(false, "");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    editPageSpecs.clear();
                    editorContainer.removeAllViews();
                    setBusy(false, "");
                    toast("PDF를 읽지 못했습니다: " + safeMessage(e));
                });
            }
        });
    }

    private void rebuildEditorRows() {
        editorContainer.removeAllViews();
        if (editPageSpecs.isEmpty()) {
            editorContainer.addView(text("모든 페이지가 삭제되었습니다.", 13, false));
            return;
        }

        for (int i = 0; i < editPageSpecs.size(); i++) {
            final int index = i;
            PdfConverter.PageSpec spec = editPageSpecs.get(i);

            LinearLayout block = new LinearLayout(this);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(dp(8), dp(7), dp(8), dp(7));

            String label = "페이지 " + (i + 1)
                    + " · 원본 " + (spec.sourceIndex + 1)
                    + (spec.rotation == 0 ? "" : " · " + spec.rotation + "° 회전");
            block.addView(text(label, 14, true));

            LinearLayout controls = new LinearLayout(this);
            controls.setOrientation(LinearLayout.HORIZONTAL);
            controls.setGravity(Gravity.CENTER_VERTICAL);

            Button up = miniButton("▲");
            Button down = miniButton("▼");
            Button rotate = miniButton("↻ 90°");
            Button delete = miniButton("삭제");

            controls.addView(up, weightedMiniParams());
            controls.addView(down, weightedMiniParams());
            controls.addView(rotate, weightedMiniParams());
            controls.addView(delete, weightedMiniParams());
            block.addView(controls, matchParams(4));

            up.setEnabled(i > 0);
            down.setEnabled(i < editPageSpecs.size() - 1);

            up.setOnClickListener(v -> {
                if (index > 0) {
                    Collections.swap(editPageSpecs, index, index - 1);
                    rebuildEditorRows();
                }
            });
            down.setOnClickListener(v -> {
                if (index < editPageSpecs.size() - 1) {
                    Collections.swap(editPageSpecs, index, index + 1);
                    rebuildEditorRows();
                }
            });
            rotate.setOnClickListener(v -> {
                PdfConverter.PageSpec page = editPageSpecs.get(index);
                page.rotation = (page.rotation + 90) % 360;
                rebuildEditorRows();
            });
            delete.setOnClickListener(v -> {
                editPageSpecs.remove(index);
                rebuildEditorRows();
                editStatus.setText("편집 중: " + editPageSpecs.size() + "페이지 남음");
            });

            LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(-1, -2);
            blockParams.topMargin = dp(6);
            editorContainer.addView(block, blockParams);
        }
    }

    private Button miniButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(12);
        return button;
    }

    private LinearLayout.LayoutParams weightedMiniParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private void convertPdfToJpg(Uri tree) {
        setBusy(true, "PDF를 JPG로 변환 중…");
        int position = qualitySpinner.getSelectedItemPosition();
        int quality = position == 1 ? 80 : position == 2 ? 65 : 90;
        Uri source = selectedConvertPdfUri;
        String name = selectedConvertPdfName;

        executor.execute(() -> {
            try {
                int count = PdfConverter.pdfToJpg(
                        this,
                        source,
                        tree,
                        name,
                        quality,
                        this::updateProgress
                );
                runOnUiThread(() -> {
                    setBusy(false, "");
                    convertPdfStatus.setText("완료: JPG " + count + "장 저장됨");
                    toast("JPG 변환 완료");
                });
            } catch (Exception e) {
                operationFailed("변환 실패", e);
            }
        });
    }

    private void convertJpgToPdf(Uri out) {
        setBusy(true, "JPG를 PDF로 만드는 중…");
        List<Uri> images = new ArrayList<>(selectedImageUris);
        executor.execute(() -> {
            try {
                int count = PdfConverter.jpgToPdf(this, images, out, this::updateProgress);
                runOnUiThread(() -> {
                    setBusy(false, "");
                    imageStatus.setText("완료: JPG " + count + "장을 PDF로 저장함");
                    toast("PDF 생성 완료");
                });
            } catch (Exception e) {
                operationFailed("PDF 생성 실패", e);
            }
        });
    }

    private void mergePdfs(Uri out) {
        setBusy(true, "PDF를 합치는 중…");
        List<Uri> inputs = new ArrayList<>(mergePdfUris);
        executor.execute(() -> {
            try {
                int count = PdfConverter.mergePdfs(this, inputs, out, this::updateProgress);
                runOnUiThread(() -> {
                    setBusy(false, "");
                    mergeStatus.setText("완료: " + inputs.size() + "개 PDF · 총 " + count + "페이지");
                    toast("PDF 합치기 완료");
                });
            } catch (Exception e) {
                operationFailed("PDF 합치기 실패", e);
            }
        });
    }

    private void splitPdf(Uri tree) {
        setBusy(true, "PDF를 페이지별로 나누는 중…");
        Uri source = splitPdfUri;
        String name = splitPdfName;
        executor.execute(() -> {
            try {
                int count = PdfConverter.splitPdf(this, source, tree, name, this::updateProgress);
                runOnUiThread(() -> {
                    setBusy(false, "");
                    splitStatus.setText("완료: PDF " + count + "개 저장됨");
                    toast("PDF 나누기 완료");
                });
            } catch (Exception e) {
                operationFailed("PDF 나누기 실패", e);
            }
        });
    }

    private void saveEditedPdf(Uri out) {
        setBusy(true, "편집한 PDF를 저장하는 중…");
        Uri source = editPdfUri;
        List<PdfConverter.PageSpec> specs = new ArrayList<>();
        for (PdfConverter.PageSpec spec : editPageSpecs) {
            specs.add(new PdfConverter.PageSpec(spec.sourceIndex, spec.rotation));
        }

        executor.execute(() -> {
            try {
                int count = PdfConverter.saveEditedPdf(this, source, specs, out, this::updateProgress);
                runOnUiThread(() -> {
                    setBusy(false, "");
                    editStatus.setText("완료: 편집본 " + count + "페이지 저장됨");
                    toast("편집본 저장 완료");
                });
            } catch (Exception e) {
                operationFailed("편집본 저장 실패", e);
            }
        });
    }

    private void updateProgress(int completed, int total) {
        runOnUiThread(() -> {
            progressBar.setMax(Math.max(1, total));
            progressBar.setProgress(completed);
            progressText.setText(completed + " / " + total);
        });
    }

    private void operationFailed(String label, Exception error) {
        runOnUiThread(() -> {
            setBusy(false, "");
            toast(label + ": " + safeMessage(error));
        });
    }

    private void setBusy(boolean busy, String message) {
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        progressText.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) {
            progressBar.setProgress(0);
            progressText.setText(message);
        }
        for (Button button : actionButtons) button.setEnabled(!busy);
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? "알 수 없는 오류" : message;
    }

    private void persistTree(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
        } catch (Exception ignored) {}
    }

    private void tryPersistRead(Uri uri) {
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {}
        return "document.pdf";
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date());
    }

    private String stripPdf(String name) {
        if (name == null || name.trim().isEmpty()) return "document";
        String base = name.replaceAll("(?i)\\.pdf$", "");
        base = base.replaceAll("[\\\\/:*?\"<>|]", "_");
        return base.trim().isEmpty() ? "document" : base.trim();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
