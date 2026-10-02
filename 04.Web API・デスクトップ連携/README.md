# 04. Web API・デスクトップ連携 — Pure Java

Server が SQLite から自己完結 PREPEJ を生成し、Client が HTTP で受信してネイティブ・プレビューアーで開くサンプルです。PDF を画像の代わりに表示する構成ではありません。受信後の PDF 保存、印刷、検索、ページ移動、ズーム、PREPEJ 保存は Client 内で処理します。

## 事前準備

- JDK 21以上（このサンプルの検証環境はJDK 21）。新しいJDKでは同梱Gradleの対応も必要です。問題がある場合はJDK 21を使用してください。`java -version` で確認できます。
- Docker Desktop + Docker Compose、または Linux Docker Engine + Docker Compose（ServerをDockerで試す場合のみ）
- Reports.Web のエンジン（`ac.pao:reports-java-engine`）は、Gradle が Maven Central から自動で取得します。

このリポジトリの `04.Web API・デスクトップ連携` フォルダーを開きます。VS Code や Cursor なら、このフォルダーで `code .` を実行します。Server と Client は別プロセスなので、2つのターミナルを使用します。

## 1. Server を直接起動する

ターミナル1で、この README のフォルダーから実行します。

```powershell
cd Server
.\gradlew.bat run --no-daemon --args="--server.port=19304"
```

macOS / Linux は `./gradlew` を使用します。全8帳票を検査したい場合だけ `apiCheck` を実行します。ログに `Started SampleWebApplication` が表示されたら Server の準備完了です。[http://127.0.0.1:19304/health](http://127.0.0.1:19304/health) でも確認できます。

ZIP展開後に `Permission denied` が出る場合は、Serverフォルダーで一度 `chmod +x gradlew` を実行します。次のClient起動でも同じエラーが出た場合は、Clientフォルダーで同じ操作を行います。

## 2. Client を起動する

Server を動かしたまま、ターミナル2で、この README のフォルダーから実行します。

```powershell
cd Client
.\gradlew.bat run --no-daemon
```

画面の URL が `http://127.0.0.1:19304/api/reports/S06/data` であることを確認し、「PREPEJを受信してローカルプレビュー」を押します。請求書が開いたら、検索、ページ移動、ズーム、PDF 保存、印刷、PREPEJ 保存を試してください。接続できない場合は画面にエラーが表示されます。Client はウィンドウを閉じて終了し、Server はターミナル1で `Ctrl+C` を押します。

ほかの帳票も、URLの`S06`だけを次のIDに変えて受信できます。URLは`http://`から始まる形で入力してください。

|ID|帳票|ID|帳票|
|---|---|---|---|
|S01|基本出力|S05|見積書|
|S02|繰返し一覧|S06|請求書|
|S03|名刺|S07|商品一覧|
|S04|郵便番号一覧|S08|デザイン機能見本|

受信したプレビューを開いたままServerを止めても、ページ移動やPDF保存はできます。データを受け取った後の帳票処理は、Client側のJavaエンジンが行うためです。もう一度受信する前にはServerを起動し直してください。

## 帳票を編集して確認する

`Server/src/main/java/com/pao/reports/samples/LegacySampleData.java` の請求書データ設定を変更します。

変更前:

```java
result.values.put("txtNo", number); result.values.put("txtCustomer", header.get("お客様名"));
```

変更後:

```java
result.values.put("txtNo", number); result.values.put("txtCustomer", "サンプル商事");
```

保存後、Server を `Ctrl+C` で止め、Server フォルダーで `.\gradlew.bat run --no-daemon --args="--server.port=19304"` を再実行します。Client の URL を `http://127.0.0.1:19304/api/reports/S06/data` にして再受信すると変更を確認できます。Client 自体の再起動は不要です。

## Server を Docker で起動する

直接起動した Server を止め、この README があるルートフォルダーへ戻って実行します。

```powershell
docker compose up -d --build
docker compose logs -f
```

ログに `Started SampleWebApplication` が表示されたら Client を起動します。接続先は同じ `127.0.0.1:19304` です。Server ソースを変更した場合は、Client で再受信する前に `docker compose up -d --build` を再実行します。

停止は `docker compose down`、コンテナーとこのサンプル専用イメージの削除は `docker compose down --rmi local` です。SQLite の `common/database/sample.db` は Server イメージへコピーされるため、元の DB は削除されません。`docker compose down -v` は将来ボリュームを追加した場合にそのデータも削除するため、通常は使用しません。

## 主なファイル

- `Server/src/main/java/.../LegacySampleData.java`: SQLite 読み込み
- `Server/src/main/java/.../SampleCatalog.java`: 8帳票の組み立て
- `Server/src/main/java/.../SampleRestController.java`: PREPEJ・SVG・PDF API
- `Client/src/main/java/.../RestClientApp.java`: 非同期 HTTP 受信と PREPEJ 検証
- `Client/src/main/java/.../SamplePreviewWindow.java`: ローカル・プレビュー、PDF、印刷、検索、ズーム
- `Server/Dockerfile` / `compose.yaml`: Server のコンテナー実行

`Server/README.md` と `Client/README.md` は、このルート README の該当手順への入口です。

## 困ったとき

- `JAVA_HOME` エラー: JDK 21以上を指定し、`java -version` を確認します。
- Server の接続エラー: `http://127.0.0.1:19304/health` を先に確認します。
- DB が見つからない: リポジトリ全体を clone し、`common/database/sample.db` を確認します。
- ポート使用中: 19304 を使う別プロセス、または直接起動と Docker の二重起動を止めます。
- JavaFX エラー: Client はデスクトップ環境で実行します。ヘッドレス端末では画面を開けません。
- 変更が見えない: Server を再起動（Docker は再ビルド）してから Client で再受信します。
- DBを編集した: 直接起動ではServerを再起動し、DockerではServerイメージを再ビルドします。
