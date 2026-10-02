# 01. あっという間に帳票出力

## このサンプルで分かること
このサンプルは完成結果を見るためのデモではありません。Javaの短いソースと帳票定義を開き、自分で値やレイアウトを変更して、PREPEJ、ページ単位SVG、PDFへ反映される流れを学ぶ最初の開発サンプルです。WASMは使用しません。

## 画面からデザインも変えてみましょう

下の準備を済ませたら、このフォルダーでWindowsは `./run.ps1 --design`、Mac／Linuxは `sh run.sh --design` を実行すると、編集体験用の画面が開きます。引数なしでは従来どおりファイル生成だけを行います。

「デザイナーでデザインを変更」を押し、文字・色・配置を変更して、デザイナーで上書き保存してください。サンプルの画面へ戻ると、JavaのエンジンでプレビューとPDFを作り直します。デザイナーを閉じる必要はありません。保存後にサンプル画面をクリックすると、ファイルの変更を確認して自動反映します。未保存の変更は反映しません。「PDFを保存」で同じ内容を保存できます。

編集するのは `output/design-session/quick-report.prepdj` です。元の `definition` は上書きせず、編集結果は次回も残ります。元に戻すには画面を閉じ、この編集用ファイルだけを削除して起動し直します。ソースの値を変える練習とは別に、現在の文字を直接編集する体験です。この最初のサンプルは1ページを対象とします。

`--design` は製品に同梱のデザイナー（`../../Tools`）を使います。[体験版 ZIP](https://www.pao.ac/reports.web/index.html#download) の同じサンプルで試してください。

## 必要な環境
- JDK 21以上（このサンプルの検証環境はJDK 21）。新しいJDKでは同梱Gradleの対応も必要です。問題がある場合はJDK 21を使用してください。

Reports.Web のエンジン（`ac.pao:reports-java-engine`）は、Gradle が Maven Central から自動で取得します。

## 実行
Windowsは`run.cmd`をダブルクリックするか`.\run.ps1`、macOS／Linuxは次を実行します。
```sh
chmod +x run.sh
./run.sh
```

## 生成物の確認
- `output/quick-report.prepej`: 定義と値を保持した自己完結・全1ページの印刷データ
- `output/quick-report-page-001.svg`: タイトルと会社名を含む1ページ目のSVG
- `output/quick-report.pdf`: 全1ページのPDF

PREPEJはPure Java版プレビュアーの「帳票データを開く」で確認できます。生成後にページ数、主要文字、各形式を自動検査します。

## 開発環境でサンプルを開く

Visual Studio Code や Cursorで、このREADMEがあるフォルダーを開いてください。Gradle対応のJava用IDEでは、このフォルダーまたは`build.gradle.kts`を既存プロジェクトとして開けます。ほかのエディターも利用できます。

開いたフォルダーのターミナルから、Windowsは`.\gradlew.bat run`、macOS／Linuxは`./gradlew run`でも実行できます。初回はGradleの取得にインターネット接続が必要です。前述の`run.cmd`／`run.sh`も、同じGradleの`run`を呼び出します。

## 最初に読むソース
`src/main/java/quickreport/QuickReport.java`です。処理を番号順に並べています。

`QuickReport.java`の先頭の`TITLE`（タイトル）や`CUSTOMER`（会社名）を書き換え、同じ方法でもう一度実行してください。例えば`TITLE`を「初めてのJava帳票」にすると、`output`のSVGとPDFに反映されます。レイアウトは`definition/quick-report.prepdj`をデザイナーで変更できます。

## 帳票定義と入力データ
`definition/quick-report.prepdj`と`input/quick-report-values.json`は他方式とも共有できる言語中立データです。絶対パスや装飾文字は含みません。

この例ではJSONを読み込んだ後、タイトルと会社名をソースの`TITLE`・`CUSTOMER`で上書きしています。JSON側の値を使いたい場合は、2行の`values.put(...)`を外し、末尾の検査もJSONの値を確認するよう合わせます。

## 停止
生成完了時に終了します。常駐サーバーやDockerコンテナはありません。

## 次に進むサンプル
上位の案内へ戻り、`02.`以降へ進んでください。フォルダー先頭の番号が推奨学習順です。
