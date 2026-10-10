#!/usr/bin/env python3
"""MCPサーバーのリソース（@での添付）を大きなDBで検証するための、合成スナップショットを出力する。

cliが出力するスナップショットと同じ配置（{出力先}/{DB名}/database.json、{出力先}/{DB名}/{スキーマ名}/tables.jsonl）で、
指定した数のテーブルを複数のスキーマに振り分けて出す。名前は日本の現行DBでよくある「業務の領域×種別」の組み合わせで、
物理名（t_order_detail）と論理名（受注明細）の両方を持つ。外部キーは同じ領域のマスタへ張る。

使い方:
    python3 scripts/generate-large-snapshot.py --tables 3000 --out /tmp/big-snapshot
    java -jar mcp/dbxray-mcp.jar --snapshot=/tmp/big-snapshot
"""

import argparse
import json
import os

# (物理名, 論理名, スキーマ)
DOMAINS = [
    ("order", "受注", "sales"),
    ("customer", "顧客", "sales"),
    ("estimate", "見積", "sales"),
    ("shipment", "出荷", "sales"),
    ("invoice", "請求", "accounting"),
    ("payment", "入金", "accounting"),
    ("account", "勘定", "accounting"),
    ("purchase", "発注", "purchase"),
    ("supplier", "仕入先", "purchase"),
    ("stock", "在庫", "inventory"),
    ("product", "商品", "inventory"),
    ("employee", "社員", "hr"),
    ("dept", "部署", "hr"),
    ("contract", "契約", "common"),
    ("project", "案件", "common"),
]

# (物理名, 論理名, マスタか)
KINDS = [
    ("master", "マスタ", True),
    ("attr", "属性", True),
    ("header", "ヘッダ", False),
    ("detail", "明細", False),
    ("history", "履歴", False),
    ("log", "ログ", False),
    ("summary", "集計", False),
    ("status", "ステータス", False),
    ("rel", "関連", False),
    ("work", "ワーク", False),
]


def table_names(count):
    """領域×種別を連番付きで並べ、count件分の(スキーマ, 物理名, 論理名, 領域の物理名, マスタか)を返す。"""
    names = []
    round_no = 0
    while len(names) < count:
        for domain, domain_logical, schema in DOMAINS:
            for kind, kind_logical, is_master in KINDS:
                if len(names) >= count:
                    return names
                suffix = "" if round_no == 0 else "_%02d" % round_no
                logical_suffix = "" if round_no == 0 else str(round_no)
                prefix = "m_" if is_master else "t_"
                names.append(
                    (
                        schema,
                        "%s%s_%s%s" % (prefix, domain, kind, suffix),
                        "%s%s%s" % (domain_logical, kind_logical, logical_suffix),
                        domain,
                        is_master,
                        round_no,
                    )
                )
        round_no += 1
    return names


def build_row(schema, name, logical, domain, is_master, round_no):
    """cliの出力と同じ形のtables.jsonlの1行（1テーブル）を組み立てる。"""
    id_column = name + "_id"
    columns = [
        {"name": id_column, "logicalName": logical + "ID", "type": "bigint", "primaryKey": True, "notNull": True},
        {"name": "code", "logicalName": logical + "コード", "type": "character varying(20)", "precisionScale": "20",
         "primaryKey": False, "notNull": True},
        {"name": "name", "logicalName": logical + "名", "type": "character varying(100)", "precisionScale": "100",
         "primaryKey": False, "notNull": False},
        {"name": "created_at", "logicalName": "登録日時", "type": "timestamp without time zone",
         "primaryKey": False, "notNull": True},
    ]
    row = {
        "schema": schema,
        "name": name,
        "logicalName": logical,
        "type": "table",
        "columns": columns,
        "constraints": [
            {"name": name + "_pkey", "type": "PRIMARY KEY", "definition": "PRIMARY KEY (%s)" % id_column}
        ],
    }
    if len(name) % 7 == 0:
        row["description"] = "%sに関する%sを管理する。" % (logical, "基本情報" if is_master else "取引の記録")
    if not is_master:
        # 2周目以降のテーブルも、同じ領域の1周目のマスタ（連番なし）を参照する
        master = "m_%s_master" % domain
        master_column = master + "_id"
        columns.append(
            {"name": master_column, "logicalName": "マスタID", "type": "bigint", "primaryKey": False, "notNull": True}
        )
        row["foreignKeys"] = [
            {
                "name": "%s_%s_fkey" % (name, master_column),
                "columns": [master_column],
                "referenceSchema": schema,
                "referenceTable": master,
                "referenceColumns": [master + "_id"],
                "cardinality": "ONE_TO_MANY",
            }
        ]
    return row


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--tables", type=int, default=3000, help="テーブル数（既定3000）")
    parser.add_argument("--database", default="testdb", help="DB名（既定testdb）")
    parser.add_argument("--out", required=True, help="スナップショットの出力先ディレクトリ（--snapshotに渡す）")
    args = parser.parse_args()

    rows_by_schema = {}
    for schema, name, logical, domain, is_master, round_no in table_names(args.tables):
        rows_by_schema.setdefault(schema, []).append(build_row(schema, name, logical, domain, is_master, round_no))

    database_dir = os.path.join(args.out, args.database)
    os.makedirs(database_dir, exist_ok=True)
    with open(os.path.join(database_dir, "database.json"), "w", encoding="utf-8") as file:
        json.dump({"formatVersion": 1, "name": args.database, "dbms": "PostgreSQL", "majorVersion": 16}, file,
                  ensure_ascii=False)
        file.write("\n")
    for schema, rows in rows_by_schema.items():
        schema_dir = os.path.join(database_dir, schema)
        os.makedirs(schema_dir, exist_ok=True)
        with open(os.path.join(schema_dir, "tables.jsonl"), "w", encoding="utf-8") as file:
            for row in rows:
                file.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")
    print("%d tables, %d schemas -> %s" % (args.tables, len(rows_by_schema), os.path.abspath(args.out)))


if __name__ == "__main__":
    main()
