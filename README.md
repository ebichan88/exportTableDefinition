# exportTableDefinition

## Overview

DBからMarkdown形式のテーブル定義書を作成するリポジトリ

## Description

対象のDBに接続し、テーブル一覧と各TBLのテーブル一覧をMarkdown形式で出力します。

### sample

[テーブル一覧](./sample/tableList_testdb.md)

### 対象DBMS
* PostgreSQL
* Oracle(一部制限あり)
    * Oracleの場合は、以下の項目の出力が不可
        * デフォルト値
        * view／materialized_viewのソース
        * Check制約の定義 

### 主なディレクトリ構成

```
exportTableDefinition
│  build
│  └─libs
│      ├─conf  ・・・ 設定ファイルが格納されているフォルダ
│      │  ├─ExportTableDefinition.properties
│      │  └─mybatis.properties
│      ├─output
│      └─exportTableDefinition-1.0-SNAPSHOT.jar ・・・ 実行可能形式Jarファイル
├─docs           ・・・ JavaDoc等のドキュメントが格納されているフォルダ
│  └─javadoc
├─gradle
│  └─wrapper
└─src
    └─ main     ・・・ javaソースコードが格納されているフォルダ
        └─ java
             └─ com
                 └─ export_table_definition
```

## Releasesからダウンロードして使う（ビルド不要・Windows向け）

開発環境を用意しなくても、[Releases](../../releases/tag/latest)から実行可能な形式一式をダウンロードしてすぐに使えます。mainブランチが更新される度に`latest`リリースの中身が自動的に最新化されます。

### 入手方法

1. [Releases](../../releases/tag/latest)から`exportTableDefinition-windows.zip`をダウンロードする
2. 好きな場所に展開する

展開すると以下の構成になっています。

```
exportTableDefinition-windows
│  run.bat                                     ・・・ ダブルクリックで実行する起動ファイル
│  exportTableDefinition-1.0-SNAPSHOT.jar      ・・・ 実行可能形式Jarファイル
├─runtime                                       ・・・ 同梱のJava実行環境（別途Javaのインストール不要）
└─conf
   ├─ExportTableDefinition.properties
   └─mybatis.properties.template
```

Java実行環境（runtimeフォルダ）を同梱しているため、PCにJavaをインストールしていなくてもそのまま実行できます。

### 設定

1. `conf\mybatis.properties.template`を`conf\mybatis.properties`にリネームし、接続先DBの情報を記載する（[mybatis.propertiesの記載内容](#mybatisproperties-の記載内容)を参照）
2. 必要に応じて`conf\ExportTableDefinition.properties`を編集する（[ExportTableDefinition.propertiesの記載内容](#exporttabledefinitionproperties-の記載内容)を参照。未編集でも全スキーマ・全テーブルが`.\output`配下に出力される）

### 実行

`run.bat`をダブルクリックする。コンソール画面が開いて処理が進み、完了すると`conf\ExportTableDefinition.properties`の`outputPath`（未指定の場合は実行フォルダ直下の`output`フォルダ）にMarkdown形式のテーブル定義書が出力される。

※Windows専用です。Windows以外の環境ではビルドして[Usage](#usage)の手順でjarファイルを直接実行してください。

## Usage

### build

以下のコマンドを実行することで、`exportTableDefinition/build/libs`フォルダ配下に`exportTableDefinition-1.0-SNAPSHOT.jar`が作成される

```
gradlew build
```

### Javadoc

以下のコマンドを実行することで、`exportTableDefinition/docs/javadoc`フォルダ配下にjavadocが作成される

```
gradlew javadoc
```

### 実行方法

`conf/ExportTableDefinition.properties`（※）及び`conf/mybatis.properties`に必要な設定値を記載した状態で以下のコマンドを実行する

```
java -jar .\exportTableDefinition-1.0-SNAPSHOT.jar
```

※`conf/mybatis.properties.template`を`conf/mybatis.properties`にリネームしてください

### ExportTableDefinition.properties の記載内容

```
schema={テーブル定義出力対象のスキーマ名（複数存在する場合はカンマ区切り）} ※空白の場合はinformation_schema／pg_catalogを除く全スキーマを対象
table={テーブル定義出力対象のテーブル名（複数存在する場合はカンマ区切り）} ※空白の場合は全テーブルを対象
outputPath={テーブル定義出力先のディレクトリパス}　※空白の場合は./outputにテーブル定義が出力されます
```

### mybatis.properties の記載内容

```
driver=ドライバーの名称
url=データベース接続先のURL
username=ユーザ名
password=パスワード
```
