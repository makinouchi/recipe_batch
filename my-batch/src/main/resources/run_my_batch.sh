#!/bin/bash

# Javaコマンドのパス
JAVA_BIN="/usr/bin/java"

# Javaオプション
JAVA_OPTS="-Xmx128m -Xms128m -XX:+UseSerialGC"

# 実行するJARファイル
JAR_PATH="/opt/myapp/my-batch-0.0.1-SNAPSHOT.jar"

# 実行
$JAVA_BIN $JAVA_OPTS -jar $JAR_PATH
