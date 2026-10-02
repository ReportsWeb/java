# 03. 全帳票をWebでプレビュー — Pure Java

SQLiteのデータからJavaだけで帳票を作り、ブラウザーでプレビュー・印刷・PDF保存するサンプルです。WebAssemblyは使いません。帳票生成はサーバー側のPure Javaエンジンが担当します。

## 事前準備

- 直接起動する場合：JDK 21以上（検証環境はJDK 21）。新しいJDKでは同梱Gradleの対応も必要です。問題がある場合はJDK 21を使用してください。`java -version` で確認できます。Dockerだけで試す場合は、パソコンへJDKを追加する必要はありません。
- Docker Desktop + Docker Compose、または Linux Docker Engine + Docker Compose（Dockerで試す場合のみ）
- Reports.Web のエンジン（`ac.pao:reports-java-engine`）は、Gradle が Maven Central から自動で取得します。

このリポジトリの `03.全帳票をWebでプレビュー` フォルダーを開きます。VS Code や Cursor なら、このフォルダーで `code .` を実行します。

## Java から直接起動する

この README があるフォルダーで実行します。

```powershell
.\gradlew.bat run --no-daemon
```

macOS / Linux では `./gradlew` を使用します。全8帳票を検査したい場合だけ `apiCheck` を実行します。

ZIP展開後に `Permission denied` が出る場合は、このフォルダーで一度 `chmod +x gradlew` を実行します。

コンソールに `Started SampleWebApplication` が表示されてから、[http://127.0.0.1:8080/](http://127.0.0.1:8080/) を開きます。帳票を選ぶと、まずプレビューだけを表示します。「ツールバーを表示」を押すと、ページ移動、拡大・縮小、検索、PDF表示を試せます。PDFは「PDF表示」を押した時に生成します。終了は起動したターミナルで `Ctrl+C` です。

## プレビューだけをアプリに組み込む

帳票選択の横のボタンは、プレビュアー内部ではなく**サンプルアプリ側のボタン**です。「ツールバーを非表示」を押すと、設定ボタン・ステータスバーも消えます。もう一度「ツールバーを表示」を押せば、すべての機能を表示できます。

実装は `src/main/resources/pure-java-demo/index.html` の末尾です。同じ外部APIを自分のボタンから呼び出せます。

```javascript
window.ReportsWebNative.hideAll(); // 設定ボタンも含め、プレビューだけにする
window.ReportsWebNative.showAll(); // すべての操作を再表示する
```

非表示にしてもPDF表示・検索などの機能は無効になりません。アプリから `showPdfPreview()`、`search("請求")` などを呼び出せます。

## 帳票を編集して確認する

`src/main/java/com/pao/reports/samples/LegacySampleData.java` の請求書データ設定を変更します。

変更前:

```java
result.values.put("txtNo", number); result.values.put("txtCustomer", header.get("お客様名"));
```

変更後:

```java
result.values.put("txtNo", number); result.values.put("txtCustomer", "サンプル商事");
```

保存後、アプリを `Ctrl+C` で止め、`.\gradlew.bat run --no-daemon`（macOS / Linux は `./gradlew`）で再コンパイルして起動します。その後ブラウザーを再読み込みし、「請求書」で変更を確認します。

## Docker で起動する

この README があるフォルダーで実行します。

```powershell
docker compose up -d --build
docker compose logs -f
```

ログに `Started SampleWebApplication` が表示されたら、[http://127.0.0.1:19303/](http://127.0.0.1:19303/) を開きます。ソース変更時は、ブラウザーを更新する前にもう一度 `docker compose up -d --build` を実行します。

停止は `docker compose down`、コンテナーとこのサンプル専用イメージの削除は `docker compose down --rmi local` です。SQLite の `common/database/sample.db` はイメージへコピーされるため、これらのコマンドで元の DB は削除されません。`docker compose down -v` は将来ボリュームを追加した場合にそのデータも削除するため、通常は不要です。

## 主なファイル

- `LegacySampleData.java`: SQLite を SQL で読み込み、帳票データへ変換
- `SampleCatalog.java`: 8種類の帳票を組み立て
- `SampleRestController.java`: PREPEJ・SVG・PDF API
- `resources/reports`: 帳票定義
- `Dockerfile` / `compose.yaml`: サーバーのコンテナー実行

## 困ったとき

- `JAVA_HOME` エラー: JDK 21以上を指定し、`java -version` を確認します。
- DB が見つからない: リポジトリ全体を clone し、`common/database/sample.db` を確認します。
- ポート使用中: 直接起動は 8080、Docker は 19303 です。競合するプロセスを止めます。
- Docker が起動しない: Docker Desktop または Linux Docker Engine の起動後、`docker compose logs` を確認します。
- 変更が見えない: Java は再起動、Docker は再ビルドしてからブラウザーを更新します。
- DBを編集した: 直接起動ではサーバーを再起動し、Dockerではイメージを再ビルドします。
