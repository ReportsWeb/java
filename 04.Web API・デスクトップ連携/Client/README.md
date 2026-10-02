# Client

Server を先に起動してください。Client の起動方法と画面操作は1つ上の [`README.md`](../README.md) の「Client」を参照してください。Gradle コマンドはこの `Client` フォルダーで実行します。

## 外部アプリへの組み込み

`SamplePreviewWindow` はこの配布サンプルに同梱したJavaFX実装であり、SDK本体のUIクラスではありません。外部アプリへ同クラスを組み込んだ場合は、次のように帳票以外の操作UIをまとめて非表示・再表示できます。サンプル自身の初期表示は変わりません。

```java
var preview = new SamplePreviewWindow(owner, "帳票", definition, data);
preview.setPreviewChromeVisible(false); // メニュー、ツールバー、ステータス、ページ一覧を非表示
preview.show();
preview.setPreviewChromeVisible(true);  // すべて再表示
```