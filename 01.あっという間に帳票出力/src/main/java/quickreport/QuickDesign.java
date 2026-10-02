package quickreport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pao.reports.engine.io.ReportDefinitionIO;
import com.pao.reports.engine.io.PreviewJobIO;
import com.pao.reports.engine.model.ReportData;
import com.pao.reports.engine.model.PreviewJob;
import com.pao.reports.engine.render.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.List;
import javax.swing.*;

/** Native Java edit/save/preview exercise. No WASM, browser or server. */
public final class QuickDesign extends JFrame {
    private final Path root, work, definition;
    private final JLabel preview = new JLabel(), status = new JLabel();
    private long stamp = -1;
    private boolean busy;

    public static void open(Path root) {
        SwingUtilities.invokeLater(() -> {
            try { new QuickDesign(root).setVisible(true); }
            catch (Exception ex) { JOptionPane.showMessageDialog(null, ex.getMessage(), "起動できません", JOptionPane.ERROR_MESSAGE); }
        });
    }
    private QuickDesign(Path root) throws Exception {
        super("あっという間に帳票出力 — デザインも変えてみましょう");
        this.root = root;
        work = root.resolve("output/design-session");
        Files.createDirectories(work);
        definition = work.resolve("quick-report.prepdj");
        if (!Files.exists(definition)) {
            var mapper = new ObjectMapper();
            var doc = mapper.readTree(root.resolve("definition/quick-report.prepdj").toFile());
            var values = (ObjectNode) mapper.readTree(root.resolve("input/quick-report-values.json").toFile());
            values.put("Title", QuickReport.TITLE); values.put("CustomerName", QuickReport.CUSTOMER);
            for (var object : doc.path("Objects")) {
                var value = values.get(object.path("Name").asText());
                if (value != null) ((ObjectNode)object).put("Text", value.asText());
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(definition.toFile(), doc);
        }
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(1000,800);
        var buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addButton(buttons,"デザイナーでデザインを変更",this::edit);
        addButton(buttons,"PDFを保存",()->refresh(()->{
            var dialog=new JFileChooser();dialog.setSelectedFile(new java.io.File("quick-report.pdf"));
            if(dialog.showSaveDialog(this)==JFileChooser.APPROVE_OPTION){
                var target=dialog.getSelectedFile().toPath();
                if(Files.exists(target)&&JOptionPane.showConfirmDialog(this,"上書きしますか？", "PDFを保存", JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
                try { if(!target.toAbsolutePath().equals(work.resolve("quick-report.pdf")))Files.copy(work.resolve("quick-report.pdf"),target,StandardCopyOption.REPLACE_EXISTING); }
                catch(Exception ex){error(ex);}
            }
        }));
        add(buttons,BorderLayout.NORTH);add(new JScrollPane(preview),BorderLayout.CENTER);add(status,BorderLayout.SOUTH);
        // デザイナーの終了待ちや常時監視ではなく、この画面へ戻った時に
        // 定義ファイルの更新日時を確認し、保存済みなら帳票を作り直します。
        // 未保存の変更は反映しません。デザイナーは開いたままでも構いません。
        addWindowFocusListener(new WindowAdapter(){public void windowGainedFocus(WindowEvent e){
            try { if(stamp>=0&&Files.getLastModifiedTime(definition).toMillis()!=stamp)refresh(null); }
            catch(Exception ex){error(ex);}
        }});
        refresh(null);
    }
    private void addButton(JPanel panel,String title,Runnable action){var button=new JButton(title);button.addActionListener(e->{if(!busy)action.run();});panel.add(button);}
    private void error(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"操作できません",JOptionPane.ERROR_MESSAGE);}
    private void refresh(Runnable after){
        if(busy)return;busy=true;status.setText("保存したデザインからプレビューとPDFを作っています…");
        new SwingWorker<ImageIcon,Void>(){
            protected ImageIcon doInBackground() throws Exception {
                var doc=new ReportDefinitionIO().read(definition);
                var data=new ReportData();for(var object:doc.objects)object.binding=null;
                var renderer=new ReportRenderer();
                if(renderer.pageCount(doc,data)!=1)throw new IllegalArgumentException("この最初のサンプルでは1ページのデザインを使ってください。");
                var image=renderer.render(doc,data,RenderOptions.preview());
                new PdfExporter().exportPages(List.of(new PdfExporter.Page(doc,data,0)),work.resolve("quick-report.pdf"),PdfExporter.Options.image(),RenderControl.none());
                var job=new PreviewJob();job.name="編集した帳票";job.pages.add(new PreviewJob.Page(doc,data,0));
                new PreviewJobIO().write(work.resolve("quick-report.prepej"),job);
                return new ImageIcon(image);
            }
            protected void done(){try{preview.setIcon(get());stamp=Files.getLastModifiedTime(definition).toMillis();status.setText("デザイナーで保存して戻ると反映します。元ファイルは変更しません。");busy=false;if(after!=null)after.run();}catch(Exception ex){busy=false;error(ex);}}
        }.execute();
    }
    private void edit(){
        try{
            Path tools=root.getParent().getParent().resolve("Tools"),target;
            List<String> command;String os=System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
            if(os.contains("win")){target=tools.resolve("Windows/Designer/Reports.java Designer.exe");command=List.of(target.toString(),definition.toString());}
            else if(os.contains("mac")){target=tools.resolve("macOS/ReportsWebDesigner.app");command=List.of("/usr/bin/open","-a",target.toString(),definition.toString());}
            else{target=tools.resolve("Linux/ReportsWebDesigner.sh");command=List.of("/bin/sh",target.toString(),definition.toString());}
            if(!Files.exists(target))throw new java.io.FileNotFoundException("同梱デザイナーが見つかりません。Toolsの起動案内を確認してください。Macでは先にMacアプリを配置します。");
            new ProcessBuilder(command).directory(target.getParent().toFile()).start();
            status.setText("デザイナーで上書き保存して戻ってください："+definition);
        }catch(Exception ex){error(ex);}
    }
}
