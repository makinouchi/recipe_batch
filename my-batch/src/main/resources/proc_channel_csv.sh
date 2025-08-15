#!/bin/bash

# --- 設定 ---
DATE=$(date '+%Y%m%d')       # 今日の日付を取得（例：20250815）
FILE="${DATE}_channel.csv"   # 対象CSVファイル
JAR_PATH="my-batch-0.0.1-SNAPSHOT.jar"       # JARファイルのパス

# --- ファイル存在チェック ---
if [ ! -f "$FILE" ]; then
  echo "エラー: ファイルが見つかりません: $FILE"
  exit 1
fi

if [ ! -f "$JAR_PATH" ]; then
  echo "エラー: JARファイルが見つかりません: $JAR_PATH"
  exit 1
fi

# --- 処理対象のチャンネルID（1列目）を1行だけ取得 ---
channel_id=$(awk -F, '
  {
    # 条件: 3列目が無い、または空、または0以外
    if (NF < 3 || $3 == "" || $3 != "0") {
      # ただし「$3 が 0 の行」は除外
      if ($3 != "0") {
        print $1;
        exit;
      }
    }
  }
' "$FILE")

# --- 該当なし（全て処理済み） ---
if [ -z "$channel_id" ]; then
  echo "処理対象なし：全ての行が完了済みです。"
  exit 0
fi

# --- JARを実行（引数にチャンネルIDを渡す） ---
echo "▶ JAR実行: $channel_id"

# Javaコマンドのパス
JAVA_BIN="/usr/bin/java"

# Javaオプション
JAVA_OPTS="-Xmx128m -Xms128m -XX:+UseSerialGC"

# 実行するJARファイル
JAR_PATH="/opt/myapp/my-batch-0.0.1-SNAPSHOT.jar"

# 実行
$JAVA_BIN $JAVA_OPTS -jar $JAR_PATH "$channel_id"


#TODO 実行結果の判定はあとでいれるか検討
#ret=$?

#if [ $ret -ne 0 ]; then
#  echo "エラー: JAR処理失敗（exit code $ret）"
#  exit 1
#fi


# --- CSVファイル更新処理（1行だけ -1000） ---
awk -F, -v target="$channel_id" '
BEGIN { OFS = "," }
{
  if ($1 == target) {
    # 3列目が空・なし・0以外 → 処理対象
    if (NF < 3 || $3 == "" || $3 != "0") {
      current = (NF < 3 || $3 == "") ? $2 : $3;
      new_val = current - 1000;
      if (new_val < 0) new_val = 0;
      $3 = new_val;
    }
    # $3 == 0 のときはそのまま
  }
  print $0;
}
' "$FILE" > tmpfile && mv tmpfile "$FILE"

echo "✔ 処理完了: $channel_id の行を更新（-1000）しました。"

