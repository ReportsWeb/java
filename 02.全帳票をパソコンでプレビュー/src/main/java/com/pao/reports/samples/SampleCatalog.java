package com.pao.reports.samples;

import com.pao.reports.engine.io.*;
import com.pao.reports.engine.model.*;
import java.io.*;
import java.util.*;

final class SampleCatalog {
    record SamplePage(ReportDocument definition, ReportData data, int sourcePage) {}
    record Sample(String name, ReportDocument definition, ReportData data, List<SamplePage> pages, String canonicalSourceJson, List<String> diagnostics) {
        Sample(String name,ReportDocument definition,ReportData data,List<SamplePage> pages){this(name,definition,data,pages,null,List.of());}
        Sample(String name, ReportDocument definition, ReportData data) {
            this(name, definition, data, ordinaryPages(definition, data));
        }
        int pageCount(){ return pages.size(); }
    }

    static Sample fromPreviewJob(PreviewJob job) {
        if (job == null || job.pages == null || job.pages.isEmpty()) throw new IllegalArgumentException("Preview job has no pages");
        var pages = new ArrayList<SamplePage>();
        for (var page : job.pages) pages.add(new SamplePage(page.definition, page.data, page.sourcePage));
        var first = job.pages.getFirst();
        return new Sample(job.name == null || job.name.isBlank() ? "Reports.web / Pure Java" : job.name,
                first.definition, first.data, List.copyOf(pages),job.canonicalSourceJson,List.copyOf(job.diagnostics));
    }

    static PreviewJob toPreviewJob(Sample sample) {
        var job = new PreviewJob(); job.name = sample.name();
        job.canonicalSourceJson=sample.canonicalSourceJson();job.diagnostics=new ArrayList<>(sample.diagnostics());
        for (var page : sample.pages()) job.pages.add(new PreviewJob.Page(page.definition(), page.data(), page.sourcePage()));
        return job;
    }

    /** Prepare a complete replacement; callers commit only after this returns successfully. */
    static Sample readPreviewData(java.nio.file.Path path,Sample current)throws IOException{
        var loaded=new PreviewJobIO().read(path);
        return loaded.selfContained()?fromPreviewJob(loaded.job()):new Sample(current.name(),current.definition(),loaded.legacyData());
    }

    private static final class CatalogHolder { static final List<Sample> ALL=buildAll(); }
    static List<Sample> all() { return CatalogHolder.ALL; }
    private static List<Sample> buildAll() {
        return List.of(
                loadWeb("プログラマー向け基本出力", "simple.prepd", Map.of("Text2","UTF-8日本語のクイックスタート")),
                loadWeb("繰返し一覧", "sample10.prepd", Map.of("日付","2003/05/24 12:00:00","頁数","Page - 1")),
                loadWeb("名刺", "business-card.prepdj", Map.of()),
                loadPostalJob(),
                loadEstimateJob(),
                loadInvoiceJob(),loadProductsJob(),
                loadWeb("デザイン機能見本", "design-showcase.prepdj", Map.of()));
    }

    static Sample byId(String id){
        if(id==null||!id.toUpperCase(Locale.ROOT).matches("S[0-9]{2}"))throw new NoSuchElementException("Unknown sample: "+id);
        var samples=all();int index=Integer.parseInt(id.substring(1))-1;
        if(index<0||index>=samples.size())throw new NoSuchElementException("Unknown sample: "+id);
        return samples.get(index);
    }
    private static Sample renamed(String name,Sample source){return new Sample(name,source.definition(),source.data(),source.pages());}

    private static Sample loadPostalSingleDefinition(){
        try{var definition=readDefinition("zip1.prepd");var all=LegacySampleData.load("postal.data.json","postal");var pages=new ArrayList<SamplePage>();for(int offset=0,page=0;offset<all.rows.size();offset+=32,page++){var data=new ReportData();data.values.putAll(all.values);data.values.put("ページ","Page-"+(page+1));data.rows.addAll(all.rows.subList(offset,Math.min(offset+32,all.rows.size())));pages.add(new SamplePage(definition,data,0));}return new Sample("郵便番号一覧（縦1）",definition,all,List.copyOf(pages));}catch(IOException ex){throw new UncheckedIOException(ex);}
    }

    private static Sample loadWeb(String displayName,String fileName,Map<String,Object> values){
        if("sample10.prepd".equals(fileName))return loadMultiplesJob(displayName);
        if("simple.prepd".equals(fileName)){fileName="quick-report.prepdj";values=Map.of("Title","あっという間に帳票出力","CustomerName","株式会社パオ");}
        try(var input=SampleCatalog.class.getResourceAsStream("/reports/"+fileName)){if(input==null)throw new FileNotFoundException(fileName);var data=new ReportData();data.values.putAll(values);
            if("itemlist.prepd".equals(fileName))data=LegacySampleData.load("products.data.json","products");
            var document=new ReportDefinitionIO().read(input,fileName);
            if("quick-report.prepdj".equals(fileName))document.objects.forEach(object->object.binding=object.name);
            if("simple.prepd".equals(fileName))document.objects.stream().filter(o->"Line3".equals(o.name)).forEach(o->o.visible=false);
            return new Sample(displayName,document,data);}catch(IOException ex){throw new UncheckedIOException(ex);}
    }

    private static Sample loadMultiplesJob(String displayName) {
        try {
            var definition=readDefinition("sample10.prepd");
            var pages=new ArrayList<SamplePage>();
            for(int pageNumber=1;pageNumber<=4;pageNumber++) {
                // Page-local workspace: edits never mutate the design or other pages.
                var page=new ReportPageBuilder(definition);
                setPrintedText(page.object("日付"),java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")));
                setPrintedText(page.object("頁数"),"Page - "+pageNumber);
                var font=page.object("フォントサイズ");
                font.fontSizePt=12; font.fontUnit="Point";
                setPrintedText(font,"フォントサイズ\n 変更後");
                // 2ページ目だけ、ページ上部の線「Line3」を非表示にする。
                page.object("Line3").visible=pageNumber!=2;
                for(int row=0;row<15;row++) {
                    int number=(pageNumber-1)*15+row+1;
                    setPrintedText(page.object("行番号",row),Integer.toString(number));
                    setPrintedText(page.object("10倍数",row),Integer.toString(number*10));
                    // 100で割り切れる値だけ、この行の文字色を青にする。
                    if ((number * 10) % 100 == 0) page.object("10倍数",row).textColor="#0000FF";
                }
                pages.add(new SamplePage(page.build(),new ReportData(),0));
            }
            return new Sample(displayName,pages.getFirst().definition(),new ReportData(),List.copyOf(pages));
        } catch(IOException ex) { throw new UncheckedIOException(ex); }
    }
    private static void setPrintedText(ReportObject target,String text) {
        target.text=text; target.binding=""; // Physical page: no later data/page binding.
    }

    private static Sample loadEstimateJob(){
        try {
            var cover=readDefinition("表紙.prepd");splitContactLines(cover,"Field7",184,10);var detail=readDefinition("見積書.prepd");
            var pages=new ArrayList<SamplePage>();
            var headers=LegacySampleData.loadEstimates();
            for(var data:headers){pages.add(new SamplePage(cover,data,0));pages.add(new SamplePage(detail,data,0));}
            return new Sample("見積書",detail,headers.getFirst(),List.copyOf(pages));
        } catch(IOException ex){throw new UncheckedIOException(ex);}
    }
    private static Sample loadInvoiceJob(){try{var pages=new ArrayList<SamplePage>();for(var data:LegacySampleData.loadInvoices()){var definition=readDefinition("請求書.prepd");customize(definition,data,"invoice");materializePackagedImages(data);pages.add(new SamplePage(definition,data,0));}return new Sample("請求書",pages.getFirst().definition(),pages.getFirst().data(),List.copyOf(pages));}catch(IOException ex){throw new UncheckedIOException(ex);}}
    private static Sample loadProductsJob(){try{var definition=readDefinition("商品一覧.prepd");var all=LegacySampleData.load("products.data.json","products");var pages=new ArrayList<SamplePage>();for(int offset=0;offset<all.rows.size();offset+=20){var data=new ReportData();data.values.putAll(all.values);data.rows.addAll(all.rows.subList(offset,Math.min(offset+20,all.rows.size())));pages.add(new SamplePage(definition,data,0));}return new Sample("商品一覧",definition,all,List.copyOf(pages));}catch(IOException ex){throw new UncheckedIOException(ex);}}

    private static Sample loadPostalJob(){
        try {
            var first=readDefinition("zip1.prepd");var second=readDefinition("zip2.prepd");
            var all=LegacySampleData.load("postal.data.json","postal");var pages=new ArrayList<SamplePage>();
            for(int offset=0,page=0;offset<all.rows.size();offset+=32,page++){
                var data=new ReportData();data.values.putAll(all.values);data.values.put("ページ","Page-"+(page+1));
                data.rows.addAll(all.rows.subList(offset,Math.min(offset+32,all.rows.size())));
                pages.add(new SamplePage(page<5?first:second,data,0));
            }
            return new Sample("郵便番号一覧",first,all,List.copyOf(pages));
        } catch(IOException ex){throw new UncheckedIOException(ex);}
    }

    private static ReportDocument readDefinition(String fileName)throws IOException{
        try(var input=SampleCatalog.class.getResourceAsStream("/reports/"+fileName)){if(input==null)throw new FileNotFoundException(fileName);return new ReportDefinitionIO().read(input,fileName);}
    }

    private static List<SamplePage> ordinaryPages(ReportDocument definition,ReportData data){
        var renderer=new com.pao.reports.engine.render.ReportRenderer();var result=new ArrayList<SamplePage>();
        for(int page=0;page<renderer.pageCount(definition,data);page++)result.add(new SamplePage(definition,data,page));
        return List.copyOf(result);
    }

    private static Sample load(String displayName, String fileName, String dataFile, String dataKind) {
        var resource="/reports/"+fileName;
        try (var input=SampleCatalog.class.getResourceAsStream(resource)) {
            if(input==null) throw new FileNotFoundException(resource);
            var document=new ReportDefinitionIO().read(input,fileName);
            ReportData data=LegacySampleData.load(dataFile,dataKind);
            customize(document,data,dataKind);
            materializePackagedImages(data);
            return new Sample(displayName,document,data);
        } catch(IOException ex) { throw new UncheckedIOException("サンプル帳票を読み込めません: "+fileName,ex); }
    }

    private static void customize(ReportDocument document,ReportData data,String dataKind){
        if(!"invoice".equals(dataKind))return;
        double[] widths={33,82,18,28,29}; double x=10;
        var added=new ArrayList<ReportObject>();var fields=new ArrayList<ReportObject>();
        for(int column=0;column<5;column++){
            var name="field"+(column+1);var template=document.objects.stream().filter(o->name.equals(o.name)).findFirst().orElseThrow();
            for(int row=0;row<data.rows.size();row++){
                var value=data.rows.get(row).get(name);if(value==null||String.valueOf(value).isBlank())continue;
                var object=template.copy();object.id=UUID.randomUUID().toString();object.name=name+"_"+row;object.binding=object.name;object.repeat=0;object.x=x;object.y=68+16d*row;object.width=widths[column];
                object.fontSizePt=row==0?15.75:row>=11&&column==3?16:12;object.bold=row==0||row>=11&&column==3;object.horizontalAlignment=row==0?"CENTER":column==0?"CENTER":column==1?"LEFT":"RIGHT";if(row>=11&&column==3)object.horizontalAlignment="CENTER";
                data.values.put(object.binding,value);fields.add(object);
            }
            x+=widths[column];
        }
        var rect=document.objects.stream().filter(o->"LineRect".equals(o.name)).findFirst().orElseThrow();
        for(int row=0;row<14;row++){var object=rect.copy();object.id=UUID.randomUUID().toString();object.name="LineRect_"+row;object.repeat=0;object.y=64+16d*row;object.fillColor=row==0?"#FFDAB9":row>=11?"#FFFFB4":row%2==0?"#87CEFA":"#FFFFFF";object.fillEnabled=true;added.add(object);}
        var horizontal=document.objects.stream().filter(o->"hLine".equals(o.name)).findFirst().orElseThrow();
        for(int row=0;row<15;row++){var object=horizontal.copy();object.id=UUID.randomUUID().toString();object.name="hLine_"+row;object.repeat=0;object.y=64+16d*row;if(row==1||row==11){object.lineType="Double";object.strokeStyle="SOLID";};added.add(object);}
        var vertical=document.objects.stream().filter(o->"vLine".equals(o.name)).findFirst().orElseThrow();
        x=10;for(int column=0;column<=5;column++){var object=vertical.copy();object.id=UUID.randomUUID().toString();object.name="vLine_"+column;object.repeat=0;object.x=x;added.add(object);if(column<5)x+=widths[column];}
        materializeNonblankLines(document,"Text13",new double[]{24,30,38,44,52,58},6,added);
        added.addAll(fields);document.objects.removeIf(o->"LineRect".equals(o.name)||"hLine".equals(o.name)||"vLine".equals(o.name)||o.name.matches("field[1-5]"));document.objects.addAll(added);
    }

    private static void splitContactLines(ReportDocument document,String name,double y,double height){materializeNonblankLines(document,name,new double[]{160,174,179,184,189},5,document.objects);}
    private static void materializeNonblankLines(ReportDocument document,String name,double[] y,double height,List<ReportObject> destination){var source=document.objects.stream().filter(o->name.equals(o.name)).findFirst().orElseThrow();var lines=source.text.lines().filter(line->!line.isBlank()).toList();if(lines.size()!=y.length)throw new IllegalStateException(name+" line count "+lines.size());source.visible=false;for(int i=0;i<lines.size();i++){var line=source.copy();line.id=UUID.randomUUID().toString();line.name=name+"Line"+(i+1);line.text=lines.get(i);line.y=y[i];line.height=height;line.textElastic=false;line.visible=true;destination.add(line);}}

    private static void materializePackagedImages(ReportData data) throws IOException {
        materializePackagedImages(data.values);
        for (var row : data.rows) materializePackagedImages(row);
    }

    private static void materializePackagedImages(Map<String,Object> values) throws IOException {
        for (var key : List.of("画像1", "画像2", "Image1")) {
            var file=values.get(key); if(file==null) continue;
            try(var input=SampleCatalog.class.getResourceAsStream("/images/"+file)) {
                if(input!=null) values.put(key,input.readAllBytes());
            }
        }
    }
}
