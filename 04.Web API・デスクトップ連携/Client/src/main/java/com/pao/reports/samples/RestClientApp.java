package com.pao.reports.samples;

import com.pao.reports.engine.io.PreviewJobIO;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Downloads self-contained PREPEJ; preview, PDF, print, search and zoom are local. */
public final class RestClientApp extends Application {
    @Override public void start(Stage stage) {
        var endpoint=new TextField("http://127.0.0.1:19304/api/reports/S06/data");
        var status=new Label("待機中");
        var receive=new Button("PREPEJを受信してローカルプレビュー");
        receive.setOnAction(event->{receive.setDisable(true);status.setText("受信中…");var task=new Task<SampleCatalog.Sample>(){
            @Override protected SampleCatalog.Sample call()throws Exception{var request=HttpRequest.newBuilder(URI.create(endpoint.getText())).GET().build();var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofByteArray());if(response.statusCode()!=200)throw new IllegalStateException("HTTP "+response.statusCode());return readPrintData(response.body());}};
            task.setOnSucceeded(done->{receive.setDisable(false);status.setText("受信完了。PDF生成・印刷もローカル処理です。");new SamplePreviewWindow(stage,task.getValue()).show();});
            task.setOnFailed(done->{receive.setDisable(false);status.setText(connectionFailureMessage(task.getException()));});var thread=new Thread(task,"reports-web-prepej-client");thread.setDaemon(true);thread.start();});
        var root=new VBox(12,new Label("Reports.web / Pure Java 帳票API"),endpoint,receive,status);root.setStyle("-fx-padding:18");stage.setTitle("Reports.web / Pure Java PREPEJ Client");stage.setScene(new Scene(root,720,220));stage.show();
    }
    static SampleCatalog.Sample readPrintData(byte[] bytes)throws Exception{var file=Files.createTempFile("reports-java-client-",".prepej");try{Files.write(file,bytes);var loaded=new PreviewJobIO().read(file);if(!loaded.selfContained())throw new IllegalArgumentException("自己完結PREPEJではありません");return SampleCatalog.fromPreviewJob(loaded.job());}finally{Files.deleteIfExists(file);}}
    static String connectionFailureMessage(Throwable failure){
        var guidance="APIサーバーの起動状態とURLを確認してください。";
        if(failure==null||failure.getMessage()==null||failure.getMessage().isBlank())return "接続失敗: "+guidance;
        return "接続失敗: "+guidance+" 詳細: "+failure.getMessage();
    }
    public static void main(String[] args){launch(args);}
}
