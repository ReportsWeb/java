package com.pao.reports.samples;

import com.pao.reports.engine.render.PrinterOutput;
import java.awt.EventQueue;
import java.awt.print.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;

/** AWT dialog on EDT, spool work on caller's worker. Tests inject a backend and never spool. */
final class SamplePrintService {
    interface Backend {
        boolean dialog() throws Exception;
        boolean hasPrinter();
        void print() throws Exception;
        void cancel();
    }
    private final Backend backend;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final AtomicBoolean started=new AtomicBoolean();
    SamplePrintService(Backend backend){this.backend=backend;}
    static SamplePrintService create(SampleCatalog.Sample sample){
        if(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("mac"))return new SamplePrintService(new MacPdfPrintBackend(sample));
        var pages=sample.pages().stream().map(p->new PrinterOutput.Page(p.definition(),p.data(),p.sourcePage())).toList();
        var pageable=new PrinterOutput().createPageable(pages);var job=PrinterJob.getPrinterJob();job.setJobName(sample.name());job.setPageable(pageable);
        PrintRequestAttributeSet attributes=initialAttributes(pageable);var initial=new HashPrintRequestAttributeSet(attributes);
        return new SamplePrintService(new Backend(){
            private final AtomicBoolean cancellation=new AtomicBoolean();
            private final java.util.concurrent.atomic.AtomicReference<java.awt.Frame> dialogOwner=new java.util.concurrent.atomic.AtomicReference<>();
            public boolean dialog()throws Exception{
                var accepted=new AtomicBoolean();var failure=new java.util.concurrent.atomic.AtomicReference<RuntimeException>();
                Runnable dialog=()->{
                    if(cancellation.get())return;
                    var owner=new java.awt.Frame("Reports.java print dialog owner");owner.setUndecorated(true);dialogOwner.set(owner);
                    attributes.add(new javax.print.attribute.standard.DialogOwner(owner));
                    try{if(!cancellation.get())accepted.set(job.printDialog(attributes));}
                    catch(RuntimeException ex){failure.set(ex);}
                    finally{attributes.remove(javax.print.attribute.standard.DialogOwner.class);closeOwnedDialogs(owner);owner.dispose();dialogOwner.compareAndSet(owner,null);}
                };
                if(EventQueue.isDispatchThread())dialog.run();else EventQueue.invokeAndWait(dialog);
                if(failure.get()!=null)throw failure.get();return accepted.get();
            }
            public boolean hasPrinter(){return job.getPrintService()!=null;}
            public void print()throws PrinterException{if(cancellation.get())return;job.setPageable(PrinterOutput.withPrintAttributes(pageable,geometryChanges(initial,attributes),job::validatePage));if(!cancellation.get())job.print(attributes);}
            public void cancel(){cancellation.set(true);job.cancel();EventQueue.invokeLater(()->{var owner=dialogOwner.get();if(owner!=null){closeOwnedDialogs(owner);owner.dispose();}});}
        });
    }
    /** Never enumerate/dispose unrelated application windows. WINDOW_CLOSING selects the dialog's cancel path. */
    private static void closeOwnedDialogs(java.awt.Window owner){for(var child:owner.getOwnedWindows()){closeOwnedDialogs(child);if(child.isDisplayable()){child.dispatchEvent(new java.awt.event.WindowEvent(child,java.awt.event.WindowEvent.WINDOW_CLOSING));if(child.isDisplayable())child.dispose();}}}
    static PrintRequestAttributeSet initialAttributes(Pageable pages){
        var attrs=new HashPrintRequestAttributeSet();PrinterOutput.initializePageAttributes(pages,attrs);return attrs;
    }
    static PrintRequestAttributeSet geometryChanges(PrintRequestAttributeSet initial,PrintRequestAttributeSet selected){
        var changes=new HashPrintRequestAttributeSet(selected);
        for(var category:java.util.List.of(javax.print.attribute.standard.Media.class,javax.print.attribute.standard.OrientationRequested.class,javax.print.attribute.standard.MediaPrintableArea.class))if(java.util.Objects.equals(initial.get(category),selected.get(category)))changes.remove(category);
        return changes;
    }
    boolean run()throws Exception{
        started.set(true);
        try{
        if(cancelled.get())return false;
        if(!backend.dialog()||cancelled.get()){backend.cancel();return false;}
        if(!backend.hasPrinter())throw new PrinterException("No printer selected");
        if(cancelled.get()){backend.cancel();return false;}
        backend.print();return !cancelled.get();
        }catch(Exception ex){backend.cancel();throw ex;}
    }
    boolean isCancelled(){return cancelled.get();}
    boolean hasStarted(){return started.get();}
    void cancel(){cancelled.set(true);backend.cancel();}
}
