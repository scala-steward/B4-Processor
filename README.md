[![Scala CI](https://github.com/NakajoLab/B4-Processor/actions/workflows/scala.yml/badge.svg)](https://github.com/NakajoLab/B4-Processor/actions/workflows/scala.yml)

# B4SMT Core

OoOのプロセッサ

## 準備
これらのソフトウェアをインストールしておく必要があります。

### プロセッサの生成・テストコードの生成
* [Nix][Nix download]

Nixについては[Nix]の公式サイトや[Zero to Nix]を参考にしてみてください。

[Nix download]: https://zero-to-nix.com/start/install
[Nix]: https://nixos.org/
[Zero to Nix]: https://zero-to-nix.com/

開発
* [intellij IDEA][intellij]
* java
* [sbt]
* [LLVM circt] (System Verilog生成用のfirtoolコマンドラインツール)

[intellij]: https://www.jetbrains.com/idea/
[sbt]: https://www.scala-sbt.org/
[LLVM circt]: https://circt.llvm.org/

テスト
* Verilator
* Icarus Verilog

## プロセッサの生成
次のコマンドで`./processor`にSystem Verilogのソースが出力されます。

```shell
$ make processor
```

## テストコードの生成
次のコマンドで`./programs`にいくつかテスト用のプログラムと[riscv-tests]のコンパイル結果が出力されます。

```shell
$ make programs
```

[riscv-tests]: https://github.com/riscv-software-src/riscv-tests

## テスト用のプログラムの生成
テスト用プログラムは`nix`を使って管理しています。

## テスト方法
いくつかの時間のかかるテストを除いたテストは次のように行います。
```shell
$ sbt "testOnly * -- -l org.scalatest.tags.Slow"
```

すべてのテストを実行するには次のコマンドを実行します。（とても時間がかかります）
```shell
$ sbt test
```

細かく実行するテストを決めたい場合はIntellij IDEAでプロジェクトを開き、各テストを実行してください。

## トラブルシューティング
### 依存関係周りを編集したらプロセッサが生成できない

コンパイル中に依存関係周りで問題が発生していれば、Nixの依存関係のキャッシュに問題がある場合があります。
その場合は`./flake.nix`を次のように編集します。
```diff
diff --git a/flake.nix b/flake.nix
index e04c9d2..5f6a965 100644
--- a/flake.nix
+++ b/flake.nix
@@ -26,7 +26,7 @@
           verilator_4 = final.callPackage ./nix/verilator_4.nix { };
           espresso = (import inputs.nixpkgs-pineapplehunter2 { inherit system; config.allowUnfree = true; }).espresso;
           b4smtGen = final.callPackage ./nix { riscv-programs = self.packages.${system}.default; };
-          b4smt = final.b4smtGen { hash = "sha256-dm8qlhY87+9tKoX9TWACi+yyzPbSFEnQTGEdJmQl4LE="; };
+          b4smt = final.b4smtGen { hash = ""; };
         };
         pkgs = import nixpkgs {
           inherit system;
```

また一度makeすると次のエラーメッセージが出てきます。

```
...
error: hash mismatch in fixed-output derivation '/nix/store/4i2jb1mhg695fbxm56wj50fpbyqjpc6n-b4smt-sbt-dependencies.tar.zst.drv':
         specified: sha256-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
            got:    sha256-dm8qlhY87+9tKoX9TWACi+yyzPbSFEnQTGEdJmQl4LE=
...
```

ここで出てきたハッシュに置き換えてmakeするとうまくビルドされると思います。
