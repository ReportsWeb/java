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
public class SampleRestController implements org.springframework.boot.web.servlet.error.ErrorController {
    public record Request(Map<String,Object> values){}
    public record Description(String id,String name,int pages,String engine){}
    private final Semaphore pdfSlots=new Semaphore(2,true);
    // Includes the 81-page postal sample after self-contained common-format export.
    // The real 81-page postal sample is about 34 MiB in the self-contained
    // physical-page format. Keep a finite bound while allowing our own output
    // to be uploaded again.
    static final int MAX_PRINT_DATA_BYTES=64*1024*1024; // 4 つのサーバーで同じ 64 MiB（2026-10-13 決定）
    private static final ObjectMapper JSON=new ObjectMapper();

    /** 断った理由（固定の日本語の文言だけ。受け取った内容やパスは入れない）を JSON で返す。 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,Object>> refused(ResponseStatusException exception){
        // 2026-10-13 決定（B18）：4 つのサーバー（Web・Java・Python・.NET）の誤りの本文は同じ形 {"error":"日本語の理由"}。
        var body=new LinkedHashMap<String,Object>();
        body.put("error",exception.getReason()==null||exception.getReason().isBlank()?"要求を処理できませんでした。":exception.getReason());
        return ResponseEntity.status(exception.getStatusCode()).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore()).body(body);
    }
    /**
     * PR-322（2026-10-13 決定）：コンテナー（Tomcat）が先に断った要求（本文が届かない 408 など）も、
     * 4 つのサーバーで同じ形 {"error":"日本語の理由"} で返す（Spring Boot の既定の英語の誤りの本文にしない）。
     */
    @RequestMapping("/error")
    public ResponseEntity<Map<String,Object>> containerError(jakarta.servlet.http.HttpServletRequest request){
        Object code=request.getAttribute(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE);
        int status=code instanceof Integer value?value:500;
        String message=switch(status){
            case 400->"要求の内容が正しくありません。";
            case 404->"指定された場所はありません。";
            case 405->"この方法では呼び出せません。";
            case 408->"要求の本文が時間内に届きませんでした。";
            case 413->"印刷データのサイズが上限を超えています。";
            case 415->"印刷データは JSON（application/json）で送ってください。";
            case 503->"帳票を生成中です。少し待って再度お試しください。";
            default->"要求を処理できませんでした。";
        };
        var body=new LinkedHashMap<String,Object>();body.put("error",message);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore()).body(body);
    }
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
            // The fixed zip1-only fixture is useful internally, but listing it next to
            // the real postal sample makes two identical first pages appear to users.
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
    public ResponseEntity<String> uploadedSvg(jakarta.servlet.http.HttpServletRequest request,@RequestParam(defaultValue="0") int page)throws Exception{
        return renderUploaded(request,value->{
            if(page<0||page>=value.pageCount())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"ページ番号が範囲外です。");
            return svgResponse(value,page);
        });
    }
    @PostMapping(value="/api/print-data/pdf",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> uploadedPdf(jakarta.servlet.http.HttpServletRequest request)throws Exception{
        return renderUploaded(request,value->renderPdfUnlocked(value,"uploaded",renderDeadline()));
    }
    @PostMapping(value="/api/reports/{id}/pdf",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable String id,@RequestBody Request request)throws Exception{
        return renderPdf(sample(id,request),id);
    }
    private ResponseEntity<byte[]> renderPdf(SampleCatalog.Sample sample,String id)throws Exception{
        if(!pdfSlots.tryAcquire(30,TimeUnit.SECONDS))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"帳票を生成中です。少し待って再度お試しください。");
        try{return renderPdfUnlocked(sample,id,System.nanoTime()+TimeUnit.SECONDS.toNanos(60));}
        catch(java.util.concurrent.CancellationException timeout){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"帳票の生成が時間内に終わりませんでした。");}
        finally{pdfSlots.release();}
    }
    private ResponseEntity<byte[]> renderPdfUnlocked(SampleCatalog.Sample sample,String id,long deadline)throws Exception{
        Path directory=null;
        try{
            directory=Files.createTempDirectory("reports-java-web-");
            // macOS /var is an OS alias for /private/var. Resolve our own fresh
            // directory, keeping the PDF exporter's destination safety checks.
            var target=directory.toRealPath().resolve("report.pdf");
            var pages=sample.pages().stream().map(p->new PdfExporter.Page(p.definition(),p.data(),p.sourcePage())).toList();
            // This new Web sample explicitly chooses vector output; the engine's legacy default is unchanged.
            var control=new RenderControl(()->Thread.currentThread().isInterrupted()||System.nanoTime()>deadline,null);
            var fonts=Arrays.stream(System.getProperty("reports.demo.pdf.fontFiles","").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
                .filter(value->!value.isBlank()).map(Path::of).toList();
            var diagnostics=new ArrayList<ObjectDiagnostic>(sample.warnings());
            ImageObjectSupport.collectObjectDiagnostics(diagnostics,()->new PdfExporter().exportPages(pages,target,PdfExporter.Options.vector(150,fonts),control));
            var response=ResponseEntity.ok();String header=diagnosticsHeader(diagnostics);if(header!=null)response=response.header(DIAGNOSTICS_HEADER,header);
            return response.header("X-Request-Id",UUID.randomUUID().toString())
                .header("X-Reports-Engine","PureJava").header("X-Reports-Pdf-Mode","vector")
                .header("Content-Disposition","inline; filename=reports-java-"+id.toUpperCase(Locale.ROOT)+".pdf")
                .cacheControl(CacheControl.noStore()).body(Files.readAllBytes(target));
        }finally{
            if(directory!=null){Files.deleteIfExists(directory.resolve("report.pdf"));Files.deleteIfExists(directory);}
        }
    }
    private long renderDeadline(){return System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(renderTimeoutMillis);}

    // ── 利用者から受け取る印刷データ（T-H1・BUG-28） ──
    // ・受け取りの大きさ：Content-Length が上限を超えれば読まずに 413。chunked でも読みながら数え、上限を 1 バイト超えた所で読むのを止めて 413。
    // ・同時に処理する量：本文のバイト数で重みを付けた枠（上限の 2 本分）。上限近くの要求は同時に 2 本まで、小さい要求は多く並べる。
    //   枠は本文を読む前に取り、描き終わるまで（時間切れの後も）持つ。UPLOAD_WAIT_SECONDS 秒待って空かなければ 503。
    // ・描画の重さ：描く前に RenderBudget で調べ、超えれば 413。
    // ・時間：解析と描画に reports.demo.render-timeout-ms（既定 60 秒）。超えたら 503（PDF は描画も止める）。
    // 上限近く（64 MiB）の要求は 1 本ずつ展開する（B18、2026-10-13 コーディネーターの決定 (a)：2 本同時では作業メモリーが 4.8GiB になった）。
    static final long UPLOAD_BUDGET_BYTES=1L*MAX_PRINT_DATA_BYTES;
    private static final int UPLOAD_WAIT_SECONDS=30;
    private static final int MIN_UPLOAD_WEIGHT=1024*1024;
    private final Semaphore uploadBytes=new Semaphore((int)Math.min(Integer.MAX_VALUE,UPLOAD_BUDGET_BYTES),true);
    private final java.util.concurrent.ExecutorService renderWorkers=java.util.concurrent.Executors.newCachedThreadPool(runnable->{var thread=new Thread(runnable,"reports-java-upload-render");thread.setDaemon(true);return thread;});
    @org.springframework.beans.factory.annotation.Value("${reports.demo.render-timeout-ms:60000}") long renderTimeoutMillis=60_000;
    @jakarta.annotation.PreDestroy void stopRenderWorkers(){renderWorkers.shutdownNow();}

    @FunctionalInterface interface UploadedRender<T>{T render(SampleCatalog.Sample sample)throws Exception;}
    private <T> T renderUploaded(jakarta.servlet.http.HttpServletRequest request,UploadedRender<T> render)throws Exception{
        long declared=request.getContentLengthLong();
        if(declared>MAX_PRINT_DATA_BYTES)throw tooLarge();
        int weight=(int)Math.max(MIN_UPLOAD_WEIGHT,declared<0?MAX_PRINT_DATA_BYTES:declared);
        if(!uploadBytes.tryAcquire(weight,UPLOAD_WAIT_SECONDS,TimeUnit.SECONDS))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"帳票を生成中です。少し待って再度お試しください。");
        boolean handedOver=false;
        try{
            byte[] body=readLimited(request.getInputStream());
            long deadline=renderDeadline();
            java.util.concurrent.Future<T> future=renderWorkers.submit(()->{
                try{return render.render(readUploaded(body));}finally{uploadBytes.release(weight);}
            });
            handedOver=true;
            try{return future.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);}
            catch(java.util.concurrent.TimeoutException timeout){
                // PDF は締め切りで止まる。SVG は途中で止められないので、終わるまで枠を持ったままにして同時実行の量を守る。
                future.cancel(true);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"帳票の生成が時間内に終わりませんでした。");
            }catch(java.util.concurrent.ExecutionException failed){
                var cause=failed.getCause();
                if(cause instanceof ResponseStatusException status)throw status;
                if(cause instanceof RenderBudget.ExceededException budget)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,budget.getMessage());
                if(cause instanceof java.util.concurrent.CancellationException)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"帳票の生成が時間内に終わりませんでした。");
                if(cause instanceof OutOfMemoryError)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"帳票を生成するメモリーが足りません。");
                if(cause instanceof Exception exception)throw exception;
                throw failed;
            }
        }finally{if(!handedOver)uploadBytes.release(weight);}
    }
    private static ResponseStatusException tooLarge(){return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"印刷データのサイズが上限を超えています。");}
    /** 上限を 1 バイト超えた所で読むのを止める（chunked の本文を最後まで受け取らない）。 */
    static byte[] readLimited(java.io.InputStream input)throws java.io.IOException{
        var output=new java.io.ByteArrayOutputStream(64*1024);var buffer=new byte[64*1024];long total=0;
        try{
            for(int n;(n=input.read(buffer))>=0;){
                total+=n;if(total>MAX_PRINT_DATA_BYTES)throw tooLarge();
                output.write(buffer,0,n);
            }
        }catch(java.io.IOException failed){
            // 本文が server.tomcat.connection-timeout（既定 30 秒）以内に届かないときは 408（4 つのサーバーで同じ。2026-10-13 決定）。
            for(Throwable t=failed;t!=null;t=t.getCause())
                if(t instanceof java.net.SocketTimeoutException)throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT,"要求の本文が時間内に届きませんでした。");
            throw failed;
        }
        if(total==0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"印刷データが空です。");
        return output.toByteArray();
    }
    private static SampleCatalog.Sample readUploaded(byte[] body)throws Exception{
        Path path=null;
        final SampleCatalog.Sample sample;
        try{
            // 利用者から受け取った印刷データの画像は埋め込みだけ（パス・URL・UNC は読む前に断る）。
            var json=UntrustedImagePolicy.checkPrintDataJson(body);
            // 埋め込み画像の大きさも、エンジンが読む（値の画像を展開する）前に調べる（413）。
            RenderBudget.checkJson(json);
            path=Files.createTempFile("reports-java-upload-",".prepej");
            Files.write(path,body);
            var loaded=new PreviewJobIO().read(path);
            if(!loaded.selfContained())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"自己完結したPREPEJ印刷データではありません。");
            // 動的属性・結び付け・Java の拡張から戻った値も含め、描画の直前の形で確かめ、パスを捨てる。
            UntrustedImagePolicy.requireEmbeddedImages(loaded.job());
            sample=SampleCatalog.fromPreviewJob(loaded.job());
        }catch(UntrustedImagePolicy.RejectedImageException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,UntrustedImagePolicy.MESSAGE);}
        catch(java.io.IOException|IllegalArgumentException|IllegalStateException|NullPointerException|ClassCastException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"PREPEJ印刷データを読み込めません。",ex);}
        finally{if(path!=null)Files.deleteIfExists(path);}
        // 描くと膨らむ入力は描く前に断る（413）。
        RenderBudget.check(sample);
        return sample;
    }
    @GetMapping(value="/api/reports/{id}/svg",produces="image/svg+xml")
    public ResponseEntity<String> svg(@PathVariable String id,@RequestParam(defaultValue="0") int page)throws Exception{
        var sample=sample(id,new Request(Map.of()));
        if(page<0||page>=sample.pageCount())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"ページ番号が範囲外です。");
        return svgResponse(sample,page);
    }
    /** 項目 8：SVG を描き、診断があれば見出し x-reports-image-diagnostics を付ける（描くのと同じスレッドで集める）。 */
    private static ResponseEntity<String> svgResponse(SampleCatalog.Sample sample,int page){
        var p=sample.pages().get(page);
        var diagnostics=new ArrayList<ObjectDiagnostic>(sample.warnings());
        String svg=ImageObjectSupport.collectObjectDiagnostics(diagnostics,()->{ImageObjectSupport.page(page+1);return new SvgExporter().svg(p.definition(),p.data(),p.sourcePage(),150);});
        var response=ResponseEntity.ok().header("X-Reports-Engine","PureJava").cacheControl(CacheControl.noStore());
        String header=diagnosticsHeader(diagnostics);if(header!=null)response=response.header(DIAGNOSTICS_HEADER,header);
        return response.body(svg);
    }
    /**
     * 項目 8（IMPL-BRIEF 01:55・02:00、2026-10-14 コーディネーター）：W の server.ts と同じ見出し。200 の応答で要素が 1 つ以上のときだけ付ける。
     * 値は encodeURIComponent(JSON.stringify(配列))（ASCII だけ）。要素は objectName・pageNumber・reason・status・source・message の順で、
     * 値の無いもの（定義の読み込みの警告の pageNumber など）は出さない。
     */
    static final String DIAGNOSTICS_HEADER="x-reports-image-diagnostics";
    static String diagnosticsHeader(List<ObjectDiagnostic> diagnostics){
        if(diagnostics==null||diagnostics.isEmpty())return null;
        var array=new ArrayList<Map<String,Object>>();
        for(var d:diagnostics){
            var item=new LinkedHashMap<String,Object>();
            item.put("objectName",d.objectName()==null?"":d.objectName());
            if(d.pageNumber()!=null)item.put("pageNumber",d.pageNumber());
            item.put("reason",d.reason());
            if(d.source()!=null&&!d.source().isBlank())item.put("source",d.source());
            item.put("message",d.message());
            array.add(item);
        }
        try{return encodeURIComponent(new ObjectMapper().writeValueAsString(array));}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
    }
    /** JavaScript の encodeURIComponent と同じ符号化（英数字と - _ . ! ~ * ' ( ) は残す）。 */
    static String encodeURIComponent(String value){
        return java.net.URLEncoder.encode(value,java.nio.charset.StandardCharsets.UTF_8).replace("+","%20")
            .replace("%21","!").replace("%27","'").replace("%28","(").replace("%29",")").replace("%7E","~");
    }
    static SampleCatalog.Sample sample(String id,Request request){
        final SampleCatalog.Sample base;
        try{base=SampleCatalog.byId(id);}catch(NoSuchElementException ex){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"指定された帳票の ID がありません。");}
        if(request==null||request.values()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"values（差し込む値）を指定してください。");
        // Public inputs replace text only, never image paths, URLs or object attributes.
        var allowed=new HashSet<String>();
        for(var page:base.pages())for(var object:page.definition().objects)if(object.type==ReportObjectType.TEXT)allowed.add(object.name);
        // 画像に結び付いた名前は、文字の名前と重なっても受け付けない（値がパスとして読まれないように）。
        for(var page:base.pages())for(var object:page.definition().objects)if(object.type==ReportObjectType.IMAGE&&object.binding!=null)allowed.remove(object.binding);
        if(request.values().size()>64)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"差し込む値が多すぎます（64 個まで）。");
        for(var entry:request.values().entrySet()){
            var value=entry.getValue();
            if(!allowed.contains(entry.getKey())||!(value instanceof String||value instanceof Number)||String.valueOf(value).length()>2000)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"変えられるのはサンプルの文字の値だけです（2000 文字まで）。");
        }
        // Preserve each compound page's definition, customer data, paper size and source page.
        var pages=new ArrayList<SampleCatalog.SamplePage>();
        for(var page:base.pages())pages.add(new SampleCatalog.SamplePage(page.definition(),withValues(page.data(),request.values()),page.sourcePage()));
        return new SampleCatalog.Sample(base.name(),base.definition(),withValues(base.data(),request.values()),List.copyOf(pages),null,base.diagnostics(),base.warnings());
    }
    private static ReportData withValues(ReportData source,Map<String,Object> values){
        var data=new ReportData();if(source!=null){data.values.putAll(source.values);for(var row:source.rows)data.rows.add(new LinkedHashMap<>(row));}
        data.values.putAll(values);return data;
    }
}
