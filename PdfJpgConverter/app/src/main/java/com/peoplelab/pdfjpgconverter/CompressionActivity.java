package com.peoplelab.pdfjpgconverter;

import android.app.Activity;
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

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CompressionActivity extends Activity {
    private static final int PICK_PDF = 101;
    private static final int SAVE_PDF = 102;

    private Uri sourceUri;
    private String sourceName = "document.pdf";
    private long sourceBytes = -1;

    private TextView fileStatus;
    private TextView resultStatus;
    private TextView progressText;
    private ProgressBar progressBar;
    private Spinner presetSpinner;
    private Button selectButton;
    private Button saveButton;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final RewardedAdGate adGate = new RewardedAdGate();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        adGate.initialize(this);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(247, 249, 252));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        Button back = new Button(this);
        back.setText("← PDF 도구");
        back.setAllCaps(false);
        root.addView(back, matchParams(0));
        back.setOnClickListener(v -> finish());

        root.addView(text("PDF 최적화", 30, true), matchParams(18));
        root.addView(text("파일 선택 → 압축 강도 설정 → 저장 순서로 진행합니다.", 14, false), matchParams(5));
        root.addView(text("🔒 PDF는 외부 서버에 업로드하지 않고 기기 안에서 처리합니다.", 13, false), matchParams(10));

        root.addView(text("1  파일 선택", 20, true), matchParams(26));
        fileStatus = text("용량을 줄일 PDF를 선택해 주세요.", 14, false);
        root.addView(fileStatus, matchParams(8));
        selectButton = new Button(this);
        selectButton.setText("PDF 파일 선택");
        selectButton.setAllCaps(false);
        root.addView(selectButton, matchParams(8));

        root.addView(text("2  압축 강도", 20, true), matchParams(26));
        presetSpinner = new Spinner(this);
        String[] presets = {
                "추천 · 균형 (일반 문서)",
                "강력 · 작은 용량 우선",
                "고화질 · 화질 우선"
        };
        presetSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, presets));
        root.addView(presetSpinner, matchParams(8));
        root.addView(text("추천은 일반 공유용, 강력은 메신저·메일 첨부용, 고화질은 사진이 많은 문서에 적합합니다.", 12, false), matchParams(6));
        root.addView(text("※ 현재 최적화 방식은 페이지를 다시 구성하므로 일부 PDF에서 선택 가능한 텍스트·링크가 이미지화될 수 있습니다.", 12, false), matchParams(8));

        root.addView(text("3  저장", 20, true), matchParams(26));
        saveButton = new Button(this);
        saveButton.setText("광고 보고 최적화 PDF 저장");
        saveButton.setAllCaps(false);
        root.addView(saveButton, matchParams(8));

        progressText = text("", 13, true);
        progressText.setGravity(Gravity.CENTER);
        progressText.setVisibility(View.GONE);
        root.addView(progressText, matchParams(20));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, matchParams(5));

        resultStatus = text("", 14, true);
        resultStatus.setGravity(Gravity.CENTER);
        root.addView(resultStatus, matchParams(14));

        setContentView(scroll);

        selectButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .setType("application/pdf")
                    .addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, PICK_PDF);
        });

        saveButton.setOnClickListener(v -> {
            if (sourceUri == null) {
                toast("먼저 PDF를 선택해 주세요.");
                return;
            }
            String name = "optimized_" + stripPdf(sourceName) + ".pdf";
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .setType("application/pdf")
                    .putExtra(Intent.EXTRA_TITLE, name)
                    .addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, SAVE_PDF);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == PICK_PDF) {
            Uri uri = data.getData();
            if (uri == null) return;
            sourceUri = uri;
            tryPersistRead(uri);
            sourceName = displayName(uri);
            sourceBytes = PdfCompressor.getSize(this, uri);
            fileStatus.setText("선택됨: " + sourceName
                    + (sourceBytes > 0 ? " · " + formatBytes(sourceBytes) : ""));
            resultStatus.setText("");
        } else if (requestCode == SAVE_PDF) {
            Uri output = data.getData();
            if (output != null) {
                adGate.showOrLoadThen(this, () -> compress(output));
            }
        }
    }

    private void compress(Uri output) {
        setBusy(true);
        int preset = presetSpinner.getSelectedItemPosition();
        Uri input = sourceUri;

        executor.execute(() -> {
            try {
                PdfCompressor.Result result = PdfCompressor.compress(
                        this,
                        input,
                        output,
                        preset,
                        (completed, total) -> runOnUiThread(() -> {
                            progressBar.setMax(Math.max(1, total));
                            progressBar.setProgress(completed);
                            progressText.setText("최적화 중 · " + completed + " / " + total + " 페이지");
                        })
                );

                runOnUiThread(() -> {
                    setBusy(false);
                    if (result.keptOriginal) {
                        resultStatus.setText("이미 충분히 최적화된 PDF입니다.\n원본 크기를 유지해 저장했습니다.");
                    } else if (result.originalBytes > 0) {
                        resultStatus.setText(
                                formatBytes(result.originalBytes) + " → "
                                        + formatBytes(result.outputBytes)
                                        + " · 약 " + result.reductionPercent() + "% 감소"
                        );
                    } else {
                        resultStatus.setText("최적화 완료 · " + result.pages + "페이지");
                    }
                    toast("PDF 최적화 완료");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    toast("최적화 실패: " + safeMessage(e));
                });
            }
        });
    }

    private void setBusy(boolean busy) {
        selectButton.setEnabled(!busy);
        saveButton.setEnabled(!busy);
        presetSpinner.setEnabled(!busy);
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        progressText.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) {
            progressBar.setProgress(0);
            progressText.setText("PDF 최적화를 준비하는 중…");
            resultStatus.setText("");
        }
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void tryPersistRead(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
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

    private String stripPdf(String name) {
        if (name == null || name.trim().isEmpty()) return "document";
        String base = name.replaceAll("(?i)\\.pdf$", "");
        base = base.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return base.isEmpty() ? "document" : base;
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) return "용량 확인 불가";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.KOREA, "%.1f KB", kb);
        double mb = kb / 1024.0;
        return String.format(Locale.KOREA, "%.2f MB", mb);
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? "알 수 없는 오류" : message;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
