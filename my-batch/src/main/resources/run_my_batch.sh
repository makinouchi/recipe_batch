#!/bin/bash

if [ $# -ne 1 ]; then
 echo "エラー: 引数を1つ指定してください。"
 echo "使い方: $0 <引数>"
 exit 1
fi

# Javaコマンドのパス
JAVA_BIN="/usr/bin/java"

# Javaオプション
JAVA_OPTS="-Xmx128m -Xms128m -XX:+UseSerialGC"

# 実行するJARファイル
JAR_PATH="/opt/myapp/my-batch-0.0.1-SNAPSHOT.jar"

CHANNEL_ID="$1"

# 実行
$JAVA_BIN $JAVA_OPTS -jar $JAR_PATH "$CHANNEL_ID"
