# BONUS.CALCULATE_BONUS

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|Oracle 23|FREEPDB1|2026/10/08|

## 定義

```sql
CREATE OR REPLACE package bonus as
    function calculate_bonus(p_salary number, p_rate number) return number deterministic;
    function calculate_bonus(p_salary number) return number deterministic;
end bonus;

CREATE OR REPLACE package body bonus as
    function calculate_bonus(p_salary number, p_rate number) return number deterministic is
    begin
        return p_salary * p_rate;
    end;

    function calculate_bonus(p_salary number) return number deterministic is
    begin
        return calculate_bonus(p_salary, 0.1);
    end;
end bonus;
```

___

[関数・プロシージャ一覧へ](../../functionList_FREEPDB1.md)
