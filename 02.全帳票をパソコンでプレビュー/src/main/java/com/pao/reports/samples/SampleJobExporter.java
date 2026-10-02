package com.pao.reports.samples;

import com.pao.reports.engine.render.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

final class SampleJobExporter {
    @FunctionalInterface interface ReplaceObserver { void beforeReplace(Path temporary,Path destination)throws IOException; }
    private SampleJobExporter() {}
    static void exportPdf(SampleCatalog.Sample sample, Path target) throws IOException {
        exportPdf(sample, target, RenderControl.none());
    }
    static void exportPdf(SampleCatalog.Sample sample, Path target, RenderControl control) throws IOException {
        exportPdf(sample,target,control,(temporary,destination)->{});
    }
    static void exportPdf(SampleCatalog.Sample sample, Path target, RenderControl control, ReplaceObserver observer) throws IOException {
        Path selected=target.toAbsolutePath().normalize(),parent=selected.getParent();
        if(parent==null||!Files.isDirectory(parent))throw new IOException("PDF target must have an existing parent");
        Path destination=Files.isSymbolicLink(selected)?selected.toRealPath():parent.toRealPath().resolve(selected.getFileName());parent=destination.getParent();
        if(Files.exists(destination)&&(!Files.isRegularFile(destination)||!Files.isWritable(destination)))throw new IOException("PDF target is not a writable regular file");
        Path temporary=Files.createTempFile(parent,".reports-sample-",".pdf.part");
        Throwable failure=null;try{prepareOutputFile(destination,temporary,parent);try(var pdf=new PDDocument()){
            var renderer=new ReportRenderer();
            int index=0;
            for(var page:sample.pages()){
                control.checkpoint();
                var definition=page.definition();var image=renderer.renderPage(definition,page.data(),RenderOptions.print(),page.sourcePage(),control);
                float width=(float)(definition.widthMm*72/25.4),height=(float)(definition.heightMm*72/25.4);
                var pdfPage=new PDPage(new PDRectangle(width,height));pdf.addPage(pdfPage);var pdImage=LosslessFactory.createFromImage(pdf,image);
                try(var content=new PDPageContentStream(pdf,pdfPage)){content.drawImage(pdImage,0,0,width,height);}image.flush();
                control.progress(++index/(double)sample.pageCount());
            }
            control.checkpoint();
            pdf.save(temporary.toFile());
            control.checkpoint();
            finishOutputFile(destination,temporary);
            observer.beforeReplace(temporary,destination);
            Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException|RuntimeException|Error ex){failure=ex;throw ex;}finally{try{Files.deleteIfExists(temporary);}catch(IOException cleanup){if(failure!=null)failure.addSuppressed(cleanup);else throw cleanup;}}
    }
    static void prepareOutputFile(Path destination,Path temporary,Path parent)throws IOException{
        if(!Files.exists(destination)){try{Path probe=parent.resolve(".reports-mode-"+java.util.UUID.randomUUID());try{Files.createFile(probe);Files.setPosixFilePermissions(temporary,Files.getPosixFilePermissions(probe));}finally{Files.deleteIfExists(probe);}}catch(UnsupportedOperationException ignored){}return;}
        if(System.getProperty("os.name","").startsWith("Mac"))copyMacMetadataSource(destination,temporary);else Files.copy(destination,temporary,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.COPY_ATTRIBUTES);
        var sourceAcl=Files.getFileAttributeView(destination,AclFileAttributeView.class);var targetAcl=Files.getFileAttributeView(temporary,AclFileAttributeView.class);if(sourceAcl!=null){if(targetAcl==null)throw new IOException("Cannot preserve output ACL");if(!targetAcl.getOwner().equals(sourceAcl.getOwner()))targetAcl.setOwner(sourceAcl.getOwner());targetAcl.setAcl(sourceAcl.getAcl());if(!targetAcl.getAcl().equals(sourceAcl.getAcl()))throw new IOException("Output ACL preservation failed");}
        var sourceUser=Files.getFileAttributeView(destination,UserDefinedFileAttributeView.class);var targetUser=Files.getFileAttributeView(temporary,UserDefinedFileAttributeView.class);if(sourceUser!=null){if(targetUser==null)throw new IOException("Cannot preserve output attributes");for(String name:sourceUser.list()){int size=sourceUser.size(name);var value=ByteBuffer.allocate(size);sourceUser.read(name,value);value.flip();targetUser.write(name,value);var check=ByteBuffer.allocate(size);targetUser.read(name,check);if(!java.util.Arrays.equals(value.array(),check.array()))throw new IOException("Output attribute preservation failed: "+name);}}
    }
    static void finishOutputFile(Path destination,Path temporary)throws IOException{if(Files.exists(destination)&&System.getProperty("os.name","").startsWith("Windows"))copyWindowsMetadata(destination,temporary);}
    private static void copyWindowsMetadata(Path source,Path target)throws IOException{
        var sourceAcl=Files.getFileAttributeView(source,AclFileAttributeView.class);var targetAcl=Files.getFileAttributeView(target,AclFileAttributeView.class);if(sourceAcl!=null){if(targetAcl==null)throw new IOException("Cannot preserve output ACL");if(!targetAcl.getOwner().equals(sourceAcl.getOwner()))targetAcl.setOwner(sourceAcl.getOwner());targetAcl.setAcl(sourceAcl.getAcl());if(!targetAcl.getAcl().equals(sourceAcl.getAcl()))throw new IOException("Output ACL preservation failed");}
        var sourceUser=Files.getFileAttributeView(source,UserDefinedFileAttributeView.class);var targetUser=Files.getFileAttributeView(target,UserDefinedFileAttributeView.class);if(sourceUser!=null){if(targetUser==null)throw new IOException("Cannot preserve output attributes");for(String name:sourceUser.list()){int size=sourceUser.size(name);var value=ByteBuffer.allocate(size);sourceUser.read(name,value);value.flip();targetUser.write(name,value);var check=ByteBuffer.allocate(size);targetUser.read(name,check);if(!java.util.Arrays.equals(value.array(),check.array()))throw new IOException("Output attribute preservation failed: "+name);}}
    }
    private static void copyMacMetadataSource(Path source,Path target)throws IOException{runMacCommand(java.util.List.of("/bin/cp","-p",source.toString(),target.toString()),"preserving output metadata");if(!macAcl(source).equals(macAcl(target)))throw new IOException("Output ACL preservation failed");}
    private static String macAcl(Path path)throws IOException{byte[] bytes=runMacCommand(java.util.List.of("/bin/ls","-lde",path.toString()),"verifying output ACL");return new String(bytes,java.nio.charset.StandardCharsets.UTF_8).lines().skip(1).collect(java.util.stream.Collectors.joining("\n"));}
    private static byte[] runMacCommand(java.util.List<String> command,String action)throws IOException{Path output=Files.createTempFile("reports-metadata-",".log");Process process=null;try{process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();try{if(!process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();if(!process.waitFor(5,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("Could not terminate metadata helper");throw new IOException("Timed out "+action);}}catch(InterruptedException ex){process.destroyForcibly();try{process.waitFor(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException ignored){ex.addSuppressed(ignored);}Thread.currentThread().interrupt();throw new IOException("Interrupted while "+action,ex);}byte[] bytes=Files.readAllBytes(output);if(process.exitValue()!=0)throw new IOException("Cannot finish "+action+": "+new String(bytes,java.nio.charset.StandardCharsets.UTF_8));return bytes;}finally{if(process!=null&&process.isAlive())process.destroyForcibly();Files.deleteIfExists(output);}}

    /** Windows byte-exact route; callers must supply the canonical serialized request. */
    static CanonicalExporter.Result exportCanonicalExact(Path bridge, Path serializedRequest,
                                                          Path targetDirectory) throws Exception {
        return new CanonicalExporter().exportExact(bridge, serializedRequest, targetDirectory);
    }
}
