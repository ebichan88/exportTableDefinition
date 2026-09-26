---
name: verify
description: >-
  docs/sample/postgres/ddl.sql を使い捨てのPostgreSQL(Docker)に流し込み、jarをビルド・実行して
  実際にテーブル定義書を出力し、結果を確認する手順。tableDefinitionMapper.xmlやドメイン層
  （エンティティ・テンプレート・ER図生成など）を変更した後、「動作確認して」「出力結果を確認して」
  「verify」などと言われたとき、またはマッパーSQLや出力ロジックに手を入れた後に必ず使う。
---

# exportTableDefinition 動作確認手順（PostgreSQL）

DB接続を要するツールなので、単体テストが通っていても実際のDBに対するSQLが壊れていることがある
（実例: マルチバイトコメントの改行崩れによるSQL構文エラー、文字列リテラルの改行混入による
Markdown表崩れ。詳細は末尾「踏み抜いた地雷」参照）。**マッパーXMLや出力ロジックを変更したら、
必ずこの手順で実DBに対して1回通してから完了とする。**

## 前提

- Dockerが使えること
- JDK 21 が `/usr/lib/jvm/java-21-amazon-corretto` にあること（無ければ `update-alternatives --list java` 等で確認し読み替える）
  - **重要**: このマシンのデフォルトJavaはJDK25。Gradle 8.7はJDK25を認識できず
    `Unsupported class file major version 69` で即死する。必ずJAVA_HOME/PATHをJDK21に切り替えてから
    `gradlew` を実行すること。

## 手順

### 1. 使い捨てPostgreSQLを起動してDDLを流し込む

既存のコンテナがあれば作り直す（DDLの変更を確実に反映するため、毎回スキーマをまっさらにする）。

```bash
docker rm -f exporttabledefinition-verify-db 2>/dev/null
docker run -d --name exporttabledefinition-verify-db \
  -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=testdb \
  -p 15432:5432 postgres:16
# 起動待ち
for i in $(seq 1 30); do docker exec exporttabledefinition-verify-db pg_isready -U postgres >/dev/null 2>&1 && break; sleep 1; done
docker cp docs/sample/postgres/ddl.sql exporttabledefinition-verify-db:/tmp/ddl.sql
docker exec -e PGPASSWORD=postgres exporttabledefinition-verify-db \
  psql -U postgres -d testdb -v ON_ERROR_STOP=1 -f /tmp/ddl.sql
```

`ON_ERROR_STOP=1` で流し込みが失敗したらここで気づける。DDL側の問題とアプリ側の問題を切り分けるため、
まずこのステップ単体で成功することを確認する。

### 2. jarをビルドする

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto
export PATH=$JAVA_HOME/bin:$PATH
./gradlew build --console=plain
```

`build/libs/exportTableDefinition-1.0-SNAPSHOT.jar` と `build/libs/conf/` 一式が作られる
（`test`タスクも実行されるが数秒で終わる。ユニットテストが落ちたらそこで止めて直す）。

### 3. 設定ファイルを書き換える（ビルドの後に行うこと）

`build.dependsOn(copyResources)` により、ビルドのたびに `src/main/resources/conf/` の内容で
`build/libs/conf/` が**上書きされる**。設定編集は必ずビルドの後に行うこと（先に編集すると消える）。

`build/libs/conf/ExportTableDefinition.properties` を編集:

```properties
schema=sample
table=
outputPath=<リポジトリの絶対パス>/docs/sample/postgres/output
chunkSize=
erDiagramMaxNodes=
outputObjects=
annotationPath=<リポジトリの絶対パス>/docs/sample/postgres/annotations.sample.yml
```

Markdownに加えて、常に`docs/sample/postgres/output/snapshot/`配下へスキーマのスナップショット
（JSON Lines）も出力される（ベースラインにコミット済み）。

### 4. 実行する

DB接続情報は環境変数で渡せる（`conf/mybatis.properties` を用意しなくてよい）。
`PropertyLoader`はカレントディレクトリ相対の`./conf`→`./src/main/resources/conf`の順で探すため、
**`build/libs` に `cd` してから実行する**こと（リポジトリ直下から実行すると `src/main/resources/conf`
側の設定＝schema空白＝全スキーマ対象を拾ってしまう）。

```bash
cd build/libs
DB_DRIVER=org.postgresql.Driver \
DB_URL=jdbc:postgresql://localhost:15432/testdb \
DB_USERNAME=postgres \
DB_PASSWORD=postgres \
java -jar exportTableDefinition-1.0-SNAPSHOT.jar
```

`[result]:SUCCESS` が出れば成功。`[result]:FAIL` の場合は次の「原因調査」を参照。

### 5. 出力を確認する

```bash
cd <リポジトリルート>
git status --short docs/sample/postgres/output
git diff docs/sample/postgres/output
```

`docs/sample/postgres/output/` をベースライン（コミット済みの「正しい出力」）として扱っている場合、
`git diff`で見るのが最速。**「基本情報」表の作成日（実行日）は毎回変わるので、その1行だけの差分は
無視してよい**。それ以外の差分（カラム・制約・ER図・多重度など）が意図した変更と一致しているか確認する。
スナップショット（`snapshot/`配下）は作成日を含まないため、意図した変更が無ければ差分ゼロになる。

初回や、意図的に出力仕様を変えた場合は、この出力一式をコミットしてベースラインを更新する
（ユーザーの明示的な許可なくcommitはしないこと）。

### 6. 後片付け

```bash
docker rm -f exporttabledefinition-verify-db
```

継続して何度も試す場合はコンテナを残しておいてよい（次回はステップ1で作り直せばよい）。

## 原因調査（`[result]:FAIL` になったら）

まずコンソールの`[errmsg]`（どのSQLで失敗したか。`Failed to select: …selectAllColumnInfo`等）と
`[cause]`（DBが返したエラー。PSQLExceptionのメッセージ等）を見る。スタックトレースは実行したディレクトリの
`var/log/exportTableDefinition.log`（`build/libs`で実行した場合は`build/libs/var/log/`）に記録される
（`FailureReporter`が想定外の失敗をスタックトレース付きでログへ出す）。
実際に組み立てられたSQL文と合わせて調べたい場合は、該当のMyBatisステートメントを直接叩く
使い捨てJavaプログラムを書くのが早い。

```java
// /tmp/repro/Repro.java など、build/libs の jar をクラスパスに使う
Properties overrides = new Properties();
overrides.setProperty("driver", "org.postgresql.Driver");
overrides.setProperty("url", "jdbc:postgresql://localhost:15432/testdb");
overrides.setProperty("username", "postgres");
overrides.setProperty("password", "postgres");
MyBatisSqlSessionFactory.setConnectionOverrides(overrides);
try (SqlSession session = MyBatisSqlSessionFactory.openSession()) {
    session.selectList(
        "com.export_table_definition.domain.repository.postgresql.TableDefinitionRepository.<問題のid>",
        Map.of("schemaList", List.of("sample"), "tableList", List.of()));
} catch (Exception e) {
    e.printStackTrace(); // Caused by: PSQLException ... と、MyBatisが添えるSQL文を合わせて確認できる
}
```

```bash
cd /tmp/repro
javac -cp <リポジトリルート>/build/libs/exportTableDefinition-1.0-SNAPSHOT.jar Repro.java
java -cp .:<リポジトリルート>/build/libs/exportTableDefinition-1.0-SNAPSHOT.jar Repro
```

PSQLExceptionの`Position:`はUTF-8バイトオフセットなので、日本語コメントが混じるSQLでは
Javaの文字インデックスとズレる。位置を厳密に特定したいときは、`MappedStatement#getBoundSql`
で実際に組み立てられたSQL文字列を取得し、UTF-8バイト列に変換してからオフセットを引く。

## 踏み抜いた地雷（同じ轍を踏まないためのメモ）

`tableDefinitionMapper.xml`（PostgreSQL/Oracle双方）は長いSQLを可読性のために手動で
折り返しているが、この折り返しが**SQLの意味を変えてしまう**箇所が過去に存在した。
マッパーXMLを編集するときは以下を守ること。

1. **`--`の単一行コメントを複数の物理行にまたがせない。**
   `-- コメント` の直後に改行を入れると、そこでコメントは終わってしまい、次の行の文字列が
   （コメントのつもりでも）生のSQLとして解釈される。日本語コメントが長くなる場合は
   必ず `/* ... */` のブロックコメントを使う（複数行にまたがっても安全）。
   実際にこれが原因で `selectAllForeignKeyInfo`（PostgreSQL）が
   `syntax error at or near "pg_catalog"` で例外を吐き、外部キーを1つでも持つDBに対して
   **一切テーブル定義書を出力できなくなっていた**（本skill作成時に発見・修正済み）。
2. **文字列リテラル（`'...'`）を複数の物理行にまたがせない。**
   `'PRIMARY\n        KEY'` のように改行を挟むと、値そのものに改行＋インデントの空白が
   混入し、生成されたMarkdownの表セルが壊れる（表の途中で改行されて別の行として見えてしまう）。
   `selectAllConstraintInfo`の`'PRIMARY KEY'`がこれで壊れていた（修正済み）。
   同様の理由でOracle側マッパーの`'CREATE INDEX '`も修正済み。
3. 上記1点目のパターンで、Oracle側マッパー（`mapper/oracle/tableDefinitionMapper.xml`）の
   `selectAllForeignKeyInfo`にも同種の壊れたコメントが3箇所あり、目視で同様に修正した
   （**Oracle環境での動作確認は未実施**。Oracle DBが手元にあるときに一度流して確認すること）。

4. **resultMapの`<arg javaType>`でプリミティブ型を指定するときは`_int`・`_boolean`のように`_`を付ける。**
   MyBatisの型エイリアスでは`int`は`Integer`、`boolean`は`Boolean`を指すため、DTO（record）の
   コンポーネントが`int`・`boolean`だとコンストラクタが見つからず、`Failed to select: ...`で失敗する
   （関数のオーバーロード番号を追加した際に実際に踏んだ。単体テストはDTOを直接生成するため検知できない）。

この手順（実DBに対して1回通す）を省略すると、上記のような「単体テストは通るが実行すると
即エラー」という不具合を見逃す。
