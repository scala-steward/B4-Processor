# RISC-Vテスト

## このディレクトリの構成
`riscv-tests`がgitのsubmoduleとなっており、Makefileは`riscv-tests/isa/Makefile`をコピーして編集したものである。

`riscv-tests`自体は次のコマンドでダウンロードできる

```sh
$ git submodule update --init --recursive
```

## hexファイルの書き出し方

```shell
$ make
```

## hexの種類
* `.text.hex`: .text.initからとったプログラム本体
* `.data.hex`: .dataからとったデータメモリ初期化用

## テストケース
* テストケースを実行
* x2に今回のテスト番号を代入
* 結果をチェック
  * テストケースが通った場合は次のテストに移動
  * テストケースが通らなかった場合はfailラベルに移動