package com.peoplelab.pdfjpgconverter;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_PDF = 1, PICK_IMAGES = 2, PICK_FOLDER = 3, SAVE_PDF = 4;
    private Uri selectedPdfUri;
    private String selectedPdfName = "document.pdf";
    private final List<Uri> selectedImageUris = new ArrayList<>();
    private TextView pdfStatus, imageStatus;
    private Spinner qualitySpinner;
    private ProgressBar progressBar;
    private Button selectPdfButton, pdfToJpgButton, selectImagesButton, jpgToPdfButton;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final RewardedAdGate adGate = new RewardedAdGate();

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        adGate.initialize(this);
    }

    private TextView text(String t, float sp, boolean bold) {
        TextView v = new TextView(this); v.setText(t); v.setTextSize(sp); v.setTextColor(Color.rgb(25,31,40));
        if (bold) v.setTypeface(null, android.graphics.Typeface.BOLD); return v;
    }
    private Button button(String t) { Button b = new Button(this); b.setText(t); return b; }
    private LinearLayout.LayoutParams mp(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.topMargin = dp(top); return p;
    }
    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(Color.rgb(247,249,252));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20),dp(24),dp(20),dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(-1,-2));
        root.addView(text("PDF ↔ JPG",30,true));
        TextView desc = text("파일은 기기 안에서 변환됩니다. 변환 파일은 서버에 업로드하지 않습니다.",14,false); root.addView(desc, mp(6));

        root.addView(text("PDF → JPG",22,true), mp(24));
        pdfStatus = text("PDF를 선택해 주세요.",14,false); root.addView(pdfStatus, mp(8));
        selectPdfButton = button("PDF 선택"); root.addView(selectPdfButton, mp(10));
        root.addView(text("JPG 품질",14,true), mp(10));
        qualitySpinner = new Spinner(this); String[] q={"고화질 · 90%","표준 · 80%","용량 절약 · 65%"};
        qualitySpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, q)); root.addView(qualitySpinner, mp(4));
        pdfToJpgButton = button("광고 보고 JPG로 변환"); root.addView(pdfToJpgButton, mp(10));

        root.addView(text("JPG → PDF",22,true), mp(26));
        imageStatus = text("JPG 이미지를 선택해 주세요.",14,false); root.addView(imageStatus, mp(8));
        selectImagesButton = button("JPG 여러 장 선택"); root.addView(selectImagesButton, mp(10));
        jpgToPdfButton = button("광고 보고 PDF 만들기"); root.addView(jpgToPdfButton, mp(10));
        progressBar = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progressBar.setVisibility(View.GONE); root.addView(progressBar, mp(18));
        TextView footer=text("개발용 Google 테스트 광고 사용 중",12,false); footer.setGravity(android.view.Gravity.CENTER); root.addView(footer,mp(14));
        setContentView(scroll);

        selectPdfButton.setOnClickListener(v -> { Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/pdf").addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,PICK_PDF); });
        pdfToJpgButton.setOnClickListener(v -> { if(selectedPdfUri==null){toast("먼저 PDF를 선택해 주세요.");return;} Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE); startActivityForResult(i,PICK_FOLDER); });
        selectImagesButton.setOnClickListener(v -> { Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/jpeg").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); startActivityForResult(i,PICK_IMAGES); });
        jpgToPdfButton.setOnClickListener(v -> { if(selectedImageUris.isEmpty()){toast("먼저 JPG 이미지를 선택해 주세요.");return;} String f="converted_"+new SimpleDateFormat("yyyyMMdd_HHmm",Locale.US).format(new Date())+".pdf"; Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/pdf").putExtra(Intent.EXTRA_TITLE,f).addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,SAVE_PDF); });
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){ super.onActivityResult(requestCode,resultCode,data); if(resultCode!=RESULT_OK||data==null)return;
        if(requestCode==PICK_PDF){ selectedPdfUri=data.getData(); if(selectedPdfUri!=null){ tryPersistRead(selectedPdfUri); selectedPdfName=displayName(selectedPdfUri); pdfStatus.setText("선택됨: "+selectedPdfName); } }
        else if(requestCode==PICK_IMAGES){ selectedImageUris.clear(); ClipData c=data.getClipData(); if(c!=null){for(int n=0;n<c.getItemCount();n++){Uri u=c.getItemAt(n).getUri(); selectedImageUris.add(u);tryPersistRead(u);}} else if(data.getData()!=null){selectedImageUris.add(data.getData());tryPersistRead(data.getData());} imageStatus.setText("선택됨: JPG "+selectedImageUris.size()+"장"); }
        else if(requestCode==PICK_FOLDER){ Uri tree=data.getData(); if(tree!=null){try{getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception ignored){} adGate.showOrLoadThen(this,()->convertPdfToJpg(tree));} }
        else if(requestCode==SAVE_PDF){ Uri out=data.getData(); if(out!=null) adGate.showOrLoadThen(this,()->convertJpgToPdf(out)); }
    }

    private void convertPdfToJpg(Uri tree){ setBusy(true); int quality=qualitySpinner.getSelectedItemPosition()==1?80:qualitySpinner.getSelectedItemPosition()==2?65:90;
        executor.execute(()->{try{int count=PdfConverter.pdfToJpg(this,selectedPdfUri,tree,selectedPdfName,quality,(a,b)->runOnUiThread(()->{progressBar.setMax(b);progressBar.setProgress(a);}));runOnUiThread(()->{setBusy(false);pdfStatus.setText("완료: JPG "+count+"장 저장됨");toast("변환 완료");});}catch(Exception e){runOnUiThread(()->{setBusy(false);toast("변환 실패: "+e.getMessage());});}}); }
    private void convertJpgToPdf(Uri out){ setBusy(true); List<Uri> images=new ArrayList<>(selectedImageUris);
        executor.execute(()->{try{int count=PdfConverter.jpgToPdf(this,images,out,(a,b)->runOnUiThread(()->{progressBar.setMax(b);progressBar.setProgress(a);}));runOnUiThread(()->{setBusy(false);imageStatus.setText("완료: JPG "+count+"장을 PDF로 저장함");toast("PDF 생성 완료");});}catch(Exception e){runOnUiThread(()->{setBusy(false);toast("변환 실패: "+e.getMessage());});}}); }
    private void setBusy(boolean busy){progressBar.setVisibility(busy?View.VISIBLE:View.GONE); selectPdfButton.setEnabled(!busy);pdfToJpgButton.setEnabled(!busy);selectImagesButton.setEnabled(!busy);jpgToPdfButton.setEnabled(!busy);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void tryPersistRead(Uri u){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}
    private String displayName(Uri u){try(Cursor c=getContentResolver().query(u,null,null,null,null)){if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0)return c.getString(i);}}catch(Exception ignored){}return "document.pdf";}
}
