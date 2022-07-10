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