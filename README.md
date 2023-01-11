[![Scala CI](https://github.com/NakajoLab/B4-Processor/actions/workflows/scala.yml/badge.svg)](https://github.com/NakajoLab/B4-Processor/actions/workflows/scala.yml)

# B4-Processor

OoOのプロセッサ

## 開き方

intellijでsbtプロジェクトとして読み込む

## テスト方法
intellij等でテスト、もしくは

```shell
$ sbt test
```

## プロセッサのVerilogファイルを出力する

デフォルトのパラメータでの出力

```shell
$ sbt "runMain b4processor.B4Processor"
```
