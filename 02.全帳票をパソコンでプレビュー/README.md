# 02.全帳票をパソコンでプレビュー — Pure Java

SQLiteから請求書、見積書、郵便番号一覧などを組み立て、JavaFXのネイティブプレビュアーで表示するJava 21サンプルです。ブラウザー、WebAssembly、Dockerは使いません。

## 1. 用意するもの

- Java Development Kit（JDK）21以上（動作確認は21）。新しいJDKでは同梱Gradleの対応も必要です。問題がある場合はJDK 21を使用してください。`java -version`で確認できます。
- Reports.Web のエンジン（`ac.pao:reports-java-engine`）は、Gradle が Maven Central から自動で取得します。共通DBは `../common/database/sample.db` を利用します。
- PostgreSQLやSQLite本体のインストールは不要です。SQLite JDBCドライバーはGradleが初回ビルド時に取得します。

## 2. ビルドして起動する

このREADMEがあるフォルダーをIDEで開くか、ターミナルでこのフォルダーへ移動します。Windowsでは次を実行します。

```powershell
.\gradlew.bat run --no-daemon
```

macOS／Linuxでは `./gradlew` を使います。帳票一覧画面が開けば成功です。全帳票を検査したい場合だけ `catalogCheck` を実行します。IntelliJ IDEAなどからは `com.pao.reports.samples.SampleLauncher` の `main` を実行します。

ZIP展開後に `Permission denied` が出る場合は、このフォルダーで一度 `chmod +x gradlew` を実行します。

## 3. 画面で確かめる

一覧から請求書、見積書、郵便番号一覧、商品一覧を選び、「印刷プレビューを開く」を押します。プレビューではページ移動、検索、倍率変更、PREPEJ保存、PDF・PNG・SVG保存、印刷を試せます。見積書は4ページ、郵便番号一覧は81ページです。

## 4. ソースを変更する

`src/main/java/com/pao/reports/samples/LegacySampleData.java`の次の行を探します。

```java
result.values.put("txtNo", number); result.values.put("txtCustomer", header.get("お客様名"));
```

`header.get("お客様名")`を`"サンプル商事"`へ変更し、もう一度 `./gradlew run --no-daemon`（Windowsは `.\gradlew.bat`）を実行します。請求書の宛名が変わります。

## 5. 主なファイル

|場所|役割|
|---|---|
|`LegacySampleData.java`|共通SQLiteをSQLで読み、帳票フィールドへ変換|
|`SampleCatalog.java`|8帳票と複数ページを組み立てる中心|
|`SampleApp.java`|帳票一覧のJavaFX画面|
|`SamplePreviewWindow.java`|ネイティブの複数ページプレビュー|
|`CatalogCheck.java`|DB件数、ページ数、請求番号・合計のヘッドレス検証|
|`resources/reports/`|帳票定義（PREPD）|
|`resources/images/`|帳票で使う画像|
|`build.gradle.kts`|Java 21、JavaFX、SDK、SQLite JDBCの設定|

DBは`../common/database/sample.db`の一つだけを読みます。生成済みPREPEJを固定配信していません。DBを編集した場合は、画面を閉じてサンプルを再起動すると変更が反映されます。

## 6. 終了と片付け

画面を閉じれば終了します。ビルド成果物だけ削除する場合は次を実行します。

```powershell
.\gradlew.bat clean --no-daemon
```

`src/`、`resources/`、共通DBは削除されません。

## 困ったとき

- Javaの版が違う場合はJDK 21以上を選び直します（検証環境はJDK 21です）。
- DBが見つからない場合はリポジトリ全体を clone し、`../common/database/sample.db`を確認します。
- 画像が出ない場合は`resources/images/`を確認します。
- 詳細は `./gradlew --version` と `./gradlew catalogCheck --stacktrace` で確認できます。

## 外部アプリへの組み込み

`SamplePreviewWindow` はこの配布サンプルに同梱したJavaFX実装であり、SDK本体のUIクラスではありません。外部アプリへ同クラスを組み込んだ場合は、次のように帳票以外の操作UIをまとめて非表示・再表示できます。サンプル自身の初期表示は変わりません。

```java
var preview = new SamplePreviewWindow(owner, "帳票", definition, data);
preview.setPreviewChromeVisible(false); // メニュー、ツールバー、ステータス、ページ一覧を非表示
preview.show();
preview.setPreviewChromeVisible(true);  // すべて再表示
```