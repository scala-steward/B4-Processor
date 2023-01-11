{ pkgs, riscv-test-src }:
let
  riscv-toolchain = pkgs.pkgsCross.riscv64-embedded.buildPackages.gcc;
  riscv-toolchain-renamed = pkgs.runCommand "riscv toolchain renamed" { } "
          mkdir -p $out/bin
          for b in `ls ${riscv-toolchain}/bin`; do
            ln -s ${riscv-toolchain}/bin/$b $out/bin/$(echo $b | sed 's/none/unknown/g');
          done
        ";
in
pkgs.stdenv.mkDerivation {
  name = "riscv-tests";
  src = riscv-test-src;
  patches = [ ./isa.patch ./bench.patch ];
  buildInputs = with pkgs; [ riscv-toolchain-renamed ];
  nativeBuildInputs = with pkgs;[ autoreconfHook ];
}
