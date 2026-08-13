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
import android.widget.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_COMPRESS_PDF = 1, SAVE_COMPRESS_PDF = 2;
    private static final int PICK_CONVERT_PDF = 3, PICK_IMAGES = 4, PICK_JPG_FOLDER = 5, SAVE_IMAGES_PDF = 6;
    private static final int PICK_MERGE_PDFS = 7, SAVE_MERGED_PDF = 8;
    private static final int PICK_SPLIT_PDF = 9, PICK_SPLIT_FOLDER = 10;
    private static final int PICK_EDIT_PDF = 11, SAVE_EDITED_PDF = 12;

    private Uri compressPdfUri, selectedConvertPdfUri, splitPdfUri, editPdfUri;
    private String compressPdfName = "document.pdf", selectedConvertPdfName = "document.pdf";
    private String splitPdfName = "document.pdf", editPdfName = "document.pdf";
    private final List<Uri> selectedImageUris = new ArrayList<>();
    private final List<Uri> mergePdfUris = new ArrayList<>();
    private final List<PdfConverter.PageSpec> editPageSpecs = new ArrayList<>();

    private TextView compressStatus, convertPdfStatus, imageStatus, mergeStatus, splitStatus, editStatus;
    private Spinner compressSpinner, qualitySpinner;
    private LinearLayout mergeContainer, editorContainer;
    private ProgressBar progressBar;
    private TextView progressText;
    private final List<Button> actionButtons = new ArrayList<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final RewardedAdGate adGate = new RewardedAdGate();

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        adGate.initialize(this);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(247,249,252));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(22),dp(18),dp(28));
        scroll.addView(root,new ScrollView.LayoutParams(-1,-2));

        root.addView(text("PDF 도구",30,true));
        root.addView(text("v0.3 · 최적화 · 변환 · 병합 · 페이지 내보내기 · 페이지 편집",14,false),mp(4));
        root.addView(text("🔒 문서는 외부 서버에 업로드하지 않고 기기 안에서 처리합니다.",13,false),mp(10));
        root.addView(text("각 기능은 파일 선택 → 옵션/순서 설정 → 저장의 단순한 문서 편집 흐름으로 구성했습니다.",12,false),mp(6));

        section(root,"1. PDF 최적화 · 용량 줄이기","PDF를 선택하고 압축 강도를 고른 뒤 새 파일로 저장합니다.");
        compressStatus=text("용량을 줄일 PDF를 선택해 주세요.",14,false); root.addView(compressStatus,mp(8));
        Button selectCompress=button("PDF 선택"); root.addView(selectCompress,mp(8));
        compressSpinner=new Spinner(this);
        compressSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
                new String[]{"추천 · 균형 (일반 문서)","강력 · 작은 용량 우선","고화질 · 화질 우선"}));
        root.addView(compressSpinner,mp(7));
        root.addView(text("※ 현재 최적화는 페이지를 다시 구성하므로 일부 PDF에서 선택 가능한 텍스트·링크가 이미지화될 수 있습니다.",11,false),mp(6));
        Button compressButton=button("광고 보고 최적화 PDF 저장"); root.addView(compressButton,mp(8));

        section(root,"2. PDF ↔ JPG 변환","PDF를 JPG로 내보내거나 여러 JPG를 하나의 PDF로 만듭니다.");
        convertPdfStatus=text("PDF를 선택해 주세요.",14,false); root.addView(convertPdfStatus,mp(8));
        Button selectConvertPdf=button("PDF 선택"); root.addView(selectConvertPdf,mp(8));
        qualitySpinner=new Spinner(this);
        qualitySpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
                new String[]{"고화질 · 90%","표준 · 80%","용량 절약 · 65%"}));
        root.addView(qualitySpinner,mp(6));
        Button pdfToJpg=button("광고 보고 JPG로 내보내기"); root.addView(pdfToJpg,mp(8));
        imageStatus=text("JPG 이미지를 선택해 주세요.",14,false); root.addView(imageStatus,mp(16));
        Button selectImages=button("JPG 여러 장 선택"); root.addView(selectImages,mp(8));
        Button jpgToPdf=button("광고 보고 PDF 만들기"); root.addView(jpgToPdf,mp(8));

        section(root,"3. PDF 병합","여러 PDF를 추가하고 순서를 정한 뒤 하나의 PDF로 저장합니다.");
        mergeStatus=text("합칠 PDF를 선택해 주세요.",14,false); root.addView(mergeStatus,mp(8));
        Button selectMerge=button("PDF 여러 개 선택"); root.addView(selectMerge,mp(8));
        mergeContainer=new LinearLayout(this); mergeContainer.setOrientation(LinearLayout.VERTICAL); root.addView(mergeContainer,mp(7));
        Button mergeButton=button("광고 보고 PDF 병합"); root.addView(mergeButton,mp(8));

        section(root,"4. 페이지 내보내기 · 분할","PDF 각 페이지를 개별 PDF 파일로 내보냅니다.");
        splitStatus=text("페이지를 내보낼 PDF를 선택해 주세요.",14,false); root.addView(splitStatus,mp(8));
        Button selectSplit=button("PDF 선택"); root.addView(selectSplit,mp(8));
        Button splitButton=button("광고 보고 페이지별 내보내기"); root.addView(splitButton,mp(8));

        section(root,"5. 페이지 편집","한 화면에서 페이지 순서 변경 · 삭제 · 90° 회전 후 새 PDF로 저장합니다.");
        editStatus=text("편집할 PDF를 선택해 주세요.",14,false); root.addView(editStatus,mp(8));
        Button selectEdit=button("편집할 PDF 선택"); root.addView(selectEdit,mp(8));
        editorContainer=new LinearLayout(this); editorContainer.setOrientation(LinearLayout.VERTICAL); root.addView(editorContainer,mp(8));
        Button saveEdited=button("광고 보고 편집본 저장"); root.addView(saveEdited,mp(8));

        progressText=text("",13,true); progressText.setGravity(Gravity.CENTER); progressText.setVisibility(View.GONE); root.addView(progressText,mp(20));
        progressBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progressBar.setVisibility(View.GONE); root.addView(progressBar,mp(4));
        TextView footer=text("개발용 Google 테스트 광고 사용 중",12,false); footer.setGravity(Gravity.CENTER); root.addView(footer,mp(18));
        setContentView(scroll);

        selectCompress.setOnClickListener(v->openSinglePdf(PICK_COMPRESS_PDF));
        compressButton.setOnClickListener(v->{
            if(compressPdfUri==null){toast("먼저 PDF를 선택해 주세요.");return;}
            createPdfDocument(SAVE_COMPRESS_PDF,"optimized_"+stripPdf(compressPdfName)+".pdf");
        });

        selectConvertPdf.setOnClickListener(v->openSinglePdf(PICK_CONVERT_PDF));
        pdfToJpg.setOnClickListener(v->{
            if(selectedConvertPdfUri==null){toast("먼저 PDF를 선택해 주세요.");return;}
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),PICK_JPG_FOLDER);
        });
        selectImages.setOnClickListener(v->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/jpeg").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
            startActivityForResult(i,PICK_IMAGES);
        });
        jpgToPdf.setOnClickListener(v->{
            if(selectedImageUris.isEmpty()){toast("먼저 JPG 이미지를 선택해 주세요.");return;}
            createPdfDocument(SAVE_IMAGES_PDF,"images_"+timestamp()+".pdf");
        });

        selectMerge.setOnClickListener(v->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/pdf").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
            startActivityForResult(i,PICK_MERGE_PDFS);
        });
        mergeButton.setOnClickListener(v->{
            if(mergePdfUris.size()<2){toast("합칠 PDF를 2개 이상 선택해 주세요.");return;}
            createPdfDocument(SAVE_MERGED_PDF,"merged_"+timestamp()+".pdf");
        });

        selectSplit.setOnClickListener(v->openSinglePdf(PICK_SPLIT_PDF));
        splitButton.setOnClickListener(v->{
            if(splitPdfUri==null){toast("먼저 PDF를 선택해 주세요.");return;}
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),PICK_SPLIT_FOLDER);
        });

        selectEdit.setOnClickListener(v->openSinglePdf(PICK_EDIT_PDF));
        saveEdited.setOnClickListener(v->{
            if(editPdfUri==null){toast("먼저 편집할 PDF를 선택해 주세요.");return;}
            if(editPageSpecs.isEmpty()){toast("저장할 페이지가 없습니다.");return;}
            createPdfDocument(SAVE_EDITED_PDF,"edited_"+stripPdf(editPdfName)+".pdf");
        });
    }

    private void section(LinearLayout root,String title,String desc){
        View divider=new View(this); divider.setBackgroundColor(Color.rgb(224,229,236));
        LinearLayout.LayoutParams d=new LinearLayout.LayoutParams(-1,dp(1)); d.topMargin=dp(24); d.bottomMargin=dp(4); root.addView(divider,d);
        root.addView(text(title,21,true),mp(14)); root.addView(text(desc,13,false),mp(5));
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null)return;
        Uri u=data.getData();
        if(requestCode==PICK_COMPRESS_PDF&&u!=null){
            compressPdfUri=u; tryPersistRead(u); compressPdfName=displayName(u);
            long size=PdfCompressor.getSize(this,u); compressStatus.setText("선택됨: "+compressPdfName+(size>0?" · "+formatBytes(size):""));
        }else if(requestCode==SAVE_COMPRESS_PDF&&u!=null){adGate.showOrLoadThen(this,()->compressPdf(u));
        }else if(requestCode==PICK_CONVERT_PDF&&u!=null){
            selectedConvertPdfUri=u;tryPersistRead(u);selectedConvertPdfName=displayName(u);convertPdfStatus.setText("선택됨: "+selectedConvertPdfName);
        }else if(requestCode==PICK_IMAGES){
            selectedImageUris.clear();collectUris(data,selectedImageUris);imageStatus.setText("선택됨: JPG "+selectedImageUris.size()+"장");
        }else if(requestCode==PICK_JPG_FOLDER&&u!=null){persistTree(u);adGate.showOrLoadThen(this,()->convertPdfToJpg(u));
        }else if(requestCode==SAVE_IMAGES_PDF&&u!=null){adGate.showOrLoadThen(this,()->convertJpgToPdf(u));
        }else if(requestCode==PICK_MERGE_PDFS){
            mergePdfUris.clear();collectUris(data,mergePdfUris);mergeStatus.setText("선택됨: PDF "+mergePdfUris.size()+"개 · 아래에서 순서 조정");rebuildMergeRows();
        }else if(requestCode==SAVE_MERGED_PDF&&u!=null){adGate.showOrLoadThen(this,()->mergePdfs(u));
        }else if(requestCode==PICK_SPLIT_PDF&&u!=null){
            splitPdfUri=u;tryPersistRead(u);splitPdfName=displayName(u);splitStatus.setText("선택됨: "+splitPdfName);
        }else if(requestCode==PICK_SPLIT_FOLDER&&u!=null){persistTree(u);adGate.showOrLoadThen(this,()->splitPdf(u));
        }else if(requestCode==PICK_EDIT_PDF&&u!=null){
            editPdfUri=u;tryPersistRead(u);editPdfName=displayName(u);loadEditPages();
        }else if(requestCode==SAVE_EDITED_PDF&&u!=null){adGate.showOrLoadThen(this,()->saveEditedPdf(u));}
    }

    private void rebuildMergeRows(){
        mergeContainer.removeAllViews();
        for(int i=0;i<mergePdfUris.size();i++){
            final int index=i; LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name=text((i+1)+". "+displayName(mergePdfUris.get(i)),13,false); row.addView(name,new LinearLayout.LayoutParams(0,-2,3f));
            Button up=miniButton("▲"),down=miniButton("▼"); row.addView(up,new LinearLayout.LayoutParams(0,-2,1f)); row.addView(down,new LinearLayout.LayoutParams(0,-2,1f));
            up.setEnabled(i>0);down.setEnabled(i<mergePdfUris.size()-1);
            up.setOnClickListener(v->{Collections.swap(mergePdfUris,index,index-1);rebuildMergeRows();});
            down.setOnClickListener(v->{Collections.swap(mergePdfUris,index,index+1);rebuildMergeRows();});
            mergeContainer.addView(row,mp(4));
        }
    }

    private void loadEditPages(){
        setBusy(true,"PDF 페이지를 읽는 중…"); Uri source=editPdfUri;
        executor.execute(()->{try{
            int count=PdfConverter.getPdfPageCount(this,source); List<PdfConverter.PageSpec> defaults=PdfConverter.createDefaultPageSpecs(count);
            runOnUiThread(()->{editPageSpecs.clear();editPageSpecs.addAll(defaults);editStatus.setText("선택됨: "+editPdfName+" · "+count+"페이지");rebuildEditorRows();setBusy(false,"");});
        }catch(Exception e){runOnUiThread(()->{editPageSpecs.clear();editorContainer.removeAllViews();setBusy(false,"");toast("PDF를 읽지 못했습니다: "+safeMessage(e));});}});
    }

    private void rebuildEditorRows(){
        editorContainer.removeAllViews();
        if(editPageSpecs.isEmpty()){editorContainer.addView(text("모든 페이지가 삭제되었습니다.",13,false));return;}
        for(int i=0;i<editPageSpecs.size();i++){
            final int index=i; PdfConverter.PageSpec spec=editPageSpecs.get(i);
            LinearLayout block=new LinearLayout(this);block.setOrientation(LinearLayout.VERTICAL);block.setPadding(dp(6),dp(5),dp(6),dp(5));
            block.addView(text("페이지 "+(i+1)+" · 원본 "+(spec.sourceIndex+1)+(spec.rotation==0?"":" · "+spec.rotation+"°"),14,true));
            LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);
            Button up=miniButton("▲"),down=miniButton("▼"),rotate=miniButton("↻ 90°"),delete=miniButton("삭제");
            controls.addView(up,weight());controls.addView(down,weight());controls.addView(rotate,weight());controls.addView(delete,weight());block.addView(controls,mp(4));
            up.setEnabled(i>0);down.setEnabled(i<editPageSpecs.size()-1);
            up.setOnClickListener(v->{Collections.swap(editPageSpecs,index,index-1);rebuildEditorRows();});
            down.setOnClickListener(v->{Collections.swap(editPageSpecs,index,index+1);rebuildEditorRows();});
            rotate.setOnClickListener(v->{PdfConverter.PageSpec p=editPageSpecs.get(index);p.rotation=(p.rotation+90)%360;rebuildEditorRows();});
            delete.setOnClickListener(v->{editPageSpecs.remove(index);editStatus.setText("편집 중: "+editPageSpecs.size()+"페이지 남음");rebuildEditorRows();});
            editorContainer.addView(block,mp(6));
        }
    }

    private void compressPdf(Uri out){
        setBusy(true,"PDF 용량을 최적화하는 중…"); int preset=compressSpinner.getSelectedItemPosition(); Uri source=compressPdfUri;
        executor.execute(()->{try{
            PdfCompressor.Result r=PdfCompressor.compress(this,source,out,preset,this::updateProgress);
            runOnUiThread(()->{setBusy(false,"");
                if(r.keptOriginal)compressStatus.setText("완료: 이미 충분히 최적화되어 원본 크기로 저장함 · "+formatBytes(r.outputBytes));
                else if(r.originalBytes>0)compressStatus.setText("완료: "+formatBytes(r.originalBytes)+" → "+formatBytes(r.outputBytes)+" · 약 "+r.reductionPercent()+"% 감소");
                else compressStatus.setText("최적화 완료: "+r.pages+"페이지"); toast("PDF 최적화 완료");});
        }catch(Exception e){failed("최적화 실패",e);}});
    }

    private void convertPdfToJpg(Uri tree){
        setBusy(true,"PDF를 JPG로 내보내는 중…"); int pos=qualitySpinner.getSelectedItemPosition();int q=pos==1?80:pos==2?65:90;Uri source=selectedConvertPdfUri;String name=selectedConvertPdfName;
        executor.execute(()->{try{int count=PdfConverter.pdfToJpg(this,source,tree,name,q,this::updateProgress);runOnUiThread(()->{setBusy(false,"");convertPdfStatus.setText("완료: JPG "+count+"장 저장됨");toast("JPG 내보내기 완료");});}catch(Exception e){failed("변환 실패",e);}});
    }

    private void convertJpgToPdf(Uri out){
        setBusy(true,"JPG를 PDF로 만드는 중…");List<Uri> images=new ArrayList<>(selectedImageUris);
        executor.execute(()->{try{int count=PdfConverter.jpgToPdf(this,images,out,this::updateProgress);runOnUiThread(()->{setBusy(false,"");imageStatus.setText("완료: JPG "+count+"장을 PDF로 저장함");toast("PDF 생성 완료");});}catch(Exception e){failed("PDF 생성 실패",e);}});
    }

    private void mergePdfs(Uri out){
        setBusy(true,"PDF를 병합하는 중…");List<Uri> inputs=new ArrayList<>(mergePdfUris);
        executor.execute(()->{try{int count=PdfConverter.mergePdfs(this,inputs,out,this::updateProgress);runOnUiThread(()->{setBusy(false,"");mergeStatus.setText("완료: "+inputs.size()+"개 PDF · 총 "+count+"페이지");toast("PDF 병합 완료");});}catch(Exception e){failed("PDF 병합 실패",e);}});
    }

    private void splitPdf(Uri tree){
        setBusy(true,"페이지를 내보내는 중…");Uri source=splitPdfUri;String name=splitPdfName;
        executor.execute(()->{try{int count=PdfConverter.splitPdf(this,source,tree,name,this::updateProgress);runOnUiThread(()->{setBusy(false,"");splitStatus.setText("완료: PDF "+count+"개 저장됨");toast("페이지 내보내기 완료");});}catch(Exception e){failed("페이지 내보내기 실패",e);}});
    }

    private void saveEditedPdf(Uri out){
        setBusy(true,"편집한 PDF를 저장하는 중…");Uri source=editPdfUri;List<PdfConverter.PageSpec> specs=new ArrayList<>();
        for(PdfConverter.PageSpec s:editPageSpecs)specs.add(new PdfConverter.PageSpec(s.sourceIndex,s.rotation));
        executor.execute(()->{try{int count=PdfConverter.saveEditedPdf(this,source,specs,out,this::updateProgress);runOnUiThread(()->{setBusy(false,"");editStatus.setText("완료: 편집본 "+count+"페이지 저장됨");toast("편집본 저장 완료");});}catch(Exception e){failed("편집본 저장 실패",e);}});
    }

    private void failed(String label,Exception e){runOnUiThread(()->{setBusy(false,"");toast(label+": "+safeMessage(e));});}
    private void updateProgress(int done,int total){runOnUiThread(()->{progressBar.setMax(Math.max(1,total));progressBar.setProgress(done);progressText.setText(done+" / "+total);});}
    private void setBusy(boolean busy,String message){progressBar.setVisibility(busy?View.VISIBLE:View.GONE);progressText.setVisibility(busy?View.VISIBLE:View.GONE);if(busy){progressBar.setProgress(0);progressText.setText(message);}for(Button b:actionButtons)b.setEnabled(!busy);compressSpinner.setEnabled(!busy);qualitySpinner.setEnabled(!busy);}

    private void openSinglePdf(int code){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/pdf").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,code);}
    private void createPdfDocument(int code,String name){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/pdf").putExtra(Intent.EXTRA_TITLE,name).addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,code);}
    private void collectUris(Intent data,List<Uri> target){ClipData c=data.getClipData();if(c!=null){for(int i=0;i<c.getItemCount();i++){Uri u=c.getItemAt(i).getUri();target.add(u);tryPersistRead(u);}}else if(data.getData()!=null){target.add(data.getData());tryPersistRead(data.getData());}}
    private void persistTree(Uri u){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception ignored){}}
    private void tryPersistRead(Uri u){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}
    private String displayName(Uri u){try(Cursor c=getContentResolver().query(u,null,null,null,null)){if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0)return c.getString(i);}}catch(Exception ignored){}return "document.pdf";}
    private String timestamp(){return new SimpleDateFormat("yyyyMMdd_HHmm",Locale.US).format(new Date());}
    private String stripPdf(String name){if(name==null||name.trim().isEmpty())return "document";String b=name.replaceAll("(?i)\\.pdf$","").replaceAll("[\\\\/:*?\"<>|]","_").trim();return b.isEmpty()?"document":b;}
    private String formatBytes(long bytes){if(bytes<0)return "용량 확인 불가";if(bytes<1024)return bytes+" B";double kb=bytes/1024.0;if(kb<1024)return String.format(Locale.KOREA,"%.1f KB",kb);return String.format(Locale.KOREA,"%.2f MB",kb/1024.0);}
    private String safeMessage(Exception e){return e.getMessage()==null||e.getMessage().trim().isEmpty()?"알 수 없는 오류":e.getMessage();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    private TextView text(String t,float sp,boolean bold){TextView v=new TextView(this);v.setText(t);v.setTextSize(sp);v.setTextColor(Color.rgb(25,31,40));if(bold)v.setTypeface(null,android.graphics.Typeface.BOLD);return v;}
    private Button button(String t){Button b=new Button(this);b.setText(t);b.setAllCaps(false);actionButtons.add(b);return b;}
    private Button miniButton(String t){Button b=new Button(this);b.setText(t);b.setAllCaps(false);b.setTextSize(12);return b;}
    private LinearLayout.LayoutParams mp(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);return p;}
    private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1f);p.setMargins(dp(2),0,dp(2),0);return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
