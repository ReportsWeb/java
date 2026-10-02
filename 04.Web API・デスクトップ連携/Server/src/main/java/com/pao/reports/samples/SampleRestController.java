package com.pao.reports.samples;

import com.pao.reports.engine.model.*;
import com.pao.reports.engine.render.*;
import com.pao.reports.engine.io.PreviewJobIO;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Demo-only routes. Generation runs in the Java engine, never Node/WASM. */
@RestController
public class SampleRestController {
    public record Request(Map<String,Object> values){}
    public record Description(String id,String name,int pages,String engine){}
    private final Semaphore pdfSlots=new Semaphore(2,true);
    // Includes the 81-page postal sample after self-contained common-format export.
    // The real 81-page postal sample is about 34 MiB in the self-contained
    // physical-page format. Keep a finite bound while allowing our own output
    // to be uploaded again.
    private static final int MAX_PRINT_DATA_BYTES=64*1024*1024;
    private static final ObjectMapper JSON=new ObjectMapper();

    @GetMapping("/health")
    public ResponseEntity<Map<String,String>> health(){
        return ResponseEntity.ok().header("X-Reports-Engine","PureJava")
            .cacheControl(CacheControl.noStore())
            .body(Map.of("status","ok","engine","PureJava"));
    }

    @GetMapping(value="/",produces=MediaType.TEXT_HTML_VALUE)
    public String index()throws java.io.IOException{
        try(var input=getClass().getResourceAsStream("/pure-java-demo/index.html")){
            if(input==null)throw new java.io.FileNotFoundException("Pure Java demo HTML");
            return new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    @GetMapping(value="/{asset:native-previewer\\.(?:js|css)}")
    public ResponseEntity<byte[]> previewAsset(@PathVariable String asset)throws java.io.IOException{
        try(var input=getClass().getResourceAsStream("/pure-java-demo/"+asset)){
            if(input==null)throw new java.io.FileNotFoundException(asset);
            var type=asset.endsWith(".js")?"text/javascript; charset=utf-8":"text/css; charset=utf-8";
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).cacheControl(CacheControl.noCache()).body(input.readAllBytes());
        }
    }
    @GetMapping("/api/samples") public List<Description> samples(){
        var all=SampleCatalog.all();var result=new ArrayList<Description>();
        for(int i=0;i<all.size();i++){
            var sample=all.get(i);
            result.add(new Description(String.format(Locale.ROOT,"S%02d",i+1),sample.name(),sample.pageCount(),"Pure Java"));
        }
        return result;
    }
    @GetMapping(value="/api/reports/{id}/pdf",produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdfGet(@PathVariable String id)throws Exception{return pdf(id,new Request(Map.of()));}
    @GetMapping(value="/api/reports/{id}/data",produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> printData(@PathVariable String id)throws Exception{
        var value=sample(id,new Request(Map.of()));
        var job=SampleCatalog.toPreviewJob(value);
        var bytes=value.canonicalSourceJson()!=null
            ?value.canonicalSourceJson().getBytes(java.nio.charset.StandardCharsets.UTF_8)
            :new PreviewJobIO().toCanonicalJson(job);
        return ResponseEntity.ok().header("X-Reports-Engine","PureJava")
            .header("Content-Disposition","attachment; filename=reports-java-"+id.toUpperCase(Locale.ROOT)+".prepej")
            .cacheControl(CacheControl.noStore()).body(bytes);
    }
    @PostMapping(value="/api/print-data/svg",consumes=MediaType.APPLICATION_JSON_VALUE,produces="image/svg+xml")
    public ResponseEntity<String> uploadedSvg(@RequestBody byte[] body,@RequestParam(defaultValue="0") int page)throws Exception{
        var value=readUploaded(body);
        if(page<0||page>=value.pageCount())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"ページ番号が範囲外です。");
        var p=value.pages().get(page);
        return ResponseEntity.ok().header("X-Reports-Engine","PureJava").cacheControl(CacheControl.noStore())
            .body(new SvgExporter().svg(p.definition(),p.data(),p.sourcePage(),150));
    }
    @PostMapping(value="/api/print-data/pdf",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> uploadedPdf(@RequestBody byte[] body)throws Exception{return renderPdf(readUploaded(body),"uploaded");}
    @PostMapping(value="/api/reports/{id}/pdf",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable String id,@RequestBody Request request)throws Exception{
        return renderPdf(sample(id,request),id);
    }
    private ResponseEntity<byte[]> renderPdf(SampleCatalog.Sample sample,String id)throws Exception{
        if(!pdfSlots.tryAcquire(30,TimeUnit.SECONDS))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"帳票を生成中です。少し待って再度お試しください。");
        Path directory=null;
        try{
            directory=Files.createTempDirectory("reports-java-web-");
            // macOS /var is an OS alias for /private/var. Resolve our own fresh
            // directory, keeping the PDF exporter's destination safety checks.
            var target=directory.toRealPath().resolve("report.pdf");
            var pages=sample.pages().stream().map(p->new PdfExporter.Page(p.definition(),p.data(),p.sourcePage())).toList();
            // This new Web sample explicitly chooses vector output; the engine's legacy default is unchanged.
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
            var control=new RenderControl(()->Thread.currentThread().isInterrupted()||System.nanoTime()>deadline,null);
            var fonts=Arrays.stream(System.getProperty("reports.demo.pdf.fontFiles","").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
                .filter(value->!value.isBlank()).map(Path::of).toList();
            new PdfExporter().exportPages(pages,target,PdfExporter.Options.vector(150,fonts),control);
            return ResponseEntity.ok().header("X-Request-Id",UUID.randomUUID().toString())
                .header("X-Reports-Engine","PureJava").header("X-Reports-Pdf-Mode","vector")
                .header("Content-Disposition","inline; filename=reports-java-"+id.toUpperCase(Locale.ROOT)+".pdf")
                .cacheControl(CacheControl.noStore()).body(Files.readAllBytes(target));
        }finally{
            try{if(directory!=null){Files.deleteIfExists(directory.resolve("report.pdf"));Files.deleteIfExists(directory);}}
            finally{pdfSlots.release();}
        }
    }
    private static SampleCatalog.Sample readUploaded(byte[] body)throws Exception{
        if(body.length==0||body.length>MAX_PRINT_DATA_BYTES)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"印刷データのサイズが上限を超えています。");
        Path path=Files.createTempFile("reports-java-upload-",".prepej");
        try{
            Files.write(path,body);
            var loaded=new PreviewJobIO().read(path);
            if(!loaded.selfContained())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"自己完結したPREPEJ印刷データではありません。");
            return SampleCatalog.fromPreviewJob(loaded.job());
        }catch(java.io.IOException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"PREPEJ印刷データを読み込めません。",ex);}
        finally{Files.deleteIfExists(path);}
    }
    @GetMapping(value="/api/reports/{id}/svg",produces="image/svg+xml")
    public ResponseEntity<String> svg(@PathVariable String id,@RequestParam(defaultValue="0") int page)throws Exception{
        var sample=sample(id,new Request(Map.of()));
        if(page<0||page>=sample.pageCount())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"ページ番号が範囲外です。");
        var p=sample.pages().get(page);
        return ResponseEntity.ok().header("X-Reports-Engine","PureJava").cacheControl(CacheControl.noStore())
            .body(new SvgExporter().svg(p.definition(),p.data(),p.sourcePage(),150));
    }
    static SampleCatalog.Sample sample(String id,Request request){
        final SampleCatalog.Sample base;
        try{base=SampleCatalog.byId(id);}catch(NoSuchElementException ex){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Unknown report id");}
        if(request==null||request.values()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"values is required");
        // Public inputs replace text only, never image paths, URLs or object attributes.
        var allowed=new HashSet<String>();
        for(var page:base.pages())for(var object:page.definition().objects)if(object.type==ReportObjectType.TEXT)allowed.add(object.name);
        if(request.values().size()>64)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Too many values");
        for(var entry:request.values().entrySet()){
            var value=entry.getValue();
            if(!allowed.contains(entry.getKey())||!(value instanceof String||value instanceof Number)||String.valueOf(value).length()>2000)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Only sample text values can be changed");
        }
        // Preserve each compound page's definition, customer data, paper size and source page.
        var pages=new ArrayList<SampleCatalog.SamplePage>();
        for(var page:base.pages())pages.add(new SampleCatalog.SamplePage(page.definition(),withValues(page.data(),request.values()),page.sourcePage()));
        return new SampleCatalog.Sample(base.name(),base.definition(),withValues(base.data(),request.values()),List.copyOf(pages),null,base.diagnostics());
    }
    private static ReportData withValues(ReportData source,Map<String,Object> values){
        var data=new ReportData();if(source!=null){data.values.putAll(source.values);for(var row:source.rows)data.rows.add(new LinkedHashMap<>(row));}
        data.values.putAll(values);return data;
    }
}
