package com.pao.reports.samples;

import com.pao.reports.engine.io.PreviewJobIO;
import com.pao.reports.engine.render.*;
import java.nio.file.Path;
import java.util.Locale;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public final class SampleApp extends Application {
    private final ListView<SampleCatalog.Sample> list=new ListView<>(); private final ImageView preview=new ImageView();
    @Override public void start(Stage stage){
        var samples=SampleCatalog.all(); var raw=getParameters().getRaw();
        if(!raw.isEmpty()&&isPreviewFile(raw.getFirst())){
            try{
                var path=Path.of(raw.getFirst());var loaded=new PreviewJobIO().read(path);
                var selected=loaded.selfContained()?SampleCatalog.fromPreviewJob(loaded.job()):new SampleCatalog.Sample(path.getFileName().toString(),samples.getFirst().definition(),loaded.legacyData());
                new SamplePreviewWindow(null,selected).show();return;
            }catch(Exception ex){var alert=new Alert(Alert.AlertType.ERROR,"帳票データを読み込めません。\n"+ex.getMessage(),ButtonType.OK);alert.setTitle("Reports.web / Pure Java");alert.showAndWait();Platform.exit();return;}
        }
        list.getItems().addAll(samples);list.setCellFactory(v->new ListCell<>(){protected void updateItem(SampleCatalog.Sample s,boolean e){super.updateItem(s,e);setText(e||s==null?null:s.name());}});list.getSelectionModel().selectedItemProperty().addListener((o,a,b)->render(b));preview.setPreserveRatio(true);preview.setFitHeight(730);var open=new Button("別ウィンドウでプレビュアー（ツールバー付き）を開く");open.setTooltip(new Tooltip("ページ移動・検索・印刷・PDF保存ができる別ウィンドウを開きます"));open.setOnAction(e->{var selected=list.getSelectionModel().getSelectedItem();if(selected!=null)new SamplePreviewWindow(stage,selected).show();});var root=new BorderPane(new ScrollPane(preview),new ToolBar(new Label("Reports.java Samples"),open),null,null,list);stage.setTitle("Reports.java Samples");stage.setScene(new Scene(root,1180,850));stage.show();list.getSelectionModel().selectFirst();if(Boolean.getBoolean("reports.java.previewEvidence"))new SamplePreviewWindow(stage,list.getItems().getFirst()).show();
    }
    private static boolean isPreviewFile(String value){var n=value.toLowerCase(Locale.ROOT);return n.endsWith(".prepe")||n.endsWith(".prepej");}
    private void render(SampleCatalog.Sample sample){if(sample==null)return;preview.setImage(SwingFXUtils.toFXImage(new ReportRenderer().render(sample.definition(),sample.data(),new RenderOptions(120,false,0)),null));}
    public static void main(String[] args){launch(args);}
}
