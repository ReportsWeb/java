package com.pao.reports.samples;

import java.awt.print.PrinterException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;

/** A real PDFKit NSPrintOperation; standalone JDK native panels report one page. */
final class MacPdfPrintBackend implements SamplePrintService.Backend {
    private final SampleCatalog.Sample sample;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final AtomicReference<Process> active=new AtomicReference<>();
    MacPdfPrintBackend(SampleCatalog.Sample sample){this.sample=sample;}
    public boolean dialog()throws Exception{
        Path directory=Files.createTempDirectory("reports-native-print-");
        Path pdf=directory.resolve("report.pdf");
        Path errors=directory.resolve("helper-stderr.txt");
        try{
            var pages=sample.pages().stream().map(p->new com.pao.reports.engine.render.PdfExporter.Page(
                p.definition(),p.data(),p.sourcePage())).toList();
            new com.pao.reports.engine.render.PdfExporter().exportPages(pages,pdf,
                com.pao.reports.engine.render.PdfExporter.Options.vector(300,java.util.List.of()),
                new com.pao.reports.engine.render.RenderControl(cancelled::get,null));
            if(cancelled.get())return false;
            Process process=new ProcessBuilder(helper().toString(),pdf.toString()).redirectError(errors.toFile()).start();
            active.set(process);
            if(cancelled.get()){process.destroy();if(!process.waitFor(2,TimeUnit.SECONDS))process.destroyForcibly();return false;}
            byte[] output=process.getInputStream().readAllBytes();
            byte[] error;try(var stream=Files.newInputStream(errors)){error=stream.readNBytes(8192);}
            int status=process.waitFor();
            if(status!=0&&!cancelled.get())throw new PrinterException(
                "macOS print operation failed: "+new String(error,StandardCharsets.UTF_8).strip());
            String response=new String(output,StandardCharsets.UTF_8).strip();
            if(response.length()>=2&&response.startsWith("\"")&&response.endsWith("\""))
                response=response.substring(1,response.length()-1);
            if(cancelled.get()||response.equals("REPORTS_CANCELLED"))return false;
            if(response.equals("REPORTS_PRINTED"))return true;
            throw new PrinterException("macOS print operation returned an invalid response");
        }finally{
            active.set(null);
            Files.deleteIfExists(pdf);Files.deleteIfExists(errors);Files.deleteIfExists(directory);
        }
    }
    public boolean hasPrinter(){return true;}
    public void print(){}
    private static Path helper()throws PrinterException{
        String override=System.getProperty("reports.mac.print.helper");
        Path result;
        if(override!=null&&!override.isBlank())result=Path.of(override);
        else{
            String launcher=System.getProperty("jpackage.app-path");
            if(launcher!=null&&!launcher.isBlank()){Path executable=Path.of(launcher).toAbsolutePath().normalize();result=executable.getParent().getParent().resolve("Resources/reports-print-helper");}
            else result=Path.of("build/reports-print-helper").toAbsolutePath().normalize();
        }
        if((override==null||override.isBlank())&&!Files.isExecutable(result)){
            Path parent=Path.of("").toAbsolutePath();
            for(int depth=0;parent!=null&&depth<8;depth++,parent=parent.getParent()){
                Path candidate=parent.resolve("Tools/macOS/ReportsWebPreviewer.app/Contents/Resources/reports-print-helper");
                if(Files.isRegularFile(candidate)&&Files.isExecutable(candidate)){result=candidate;break;}
            }
        }
        if(!Files.isRegularFile(result)||!Files.isExecutable(result))throw new PrinterException("macOS print helper is missing");
        return result;
    }
    public void cancel(){cancelled.set(true);Process process=active.get();if(process!=null){process.destroy();try{if(!process.waitFor(2,TimeUnit.SECONDS))process.destroyForcibly();}catch(InterruptedException ex){Thread.currentThread().interrupt();process.destroyForcibly();}}}
}
