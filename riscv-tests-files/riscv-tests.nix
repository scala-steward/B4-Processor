{ pkgs, riscv-test-src }:
pkgs.stdenv.mkDerivation {
  name = "riscv-tests";
  src = riscv-test-src;
  patches = [ ./isa.patch ./bench.patch ];
  configureFlags = [ "target_alias=riscv64-none-elf" ];
  enableParallelBuilding = true;
  nativeBuildInputs = with pkgs; [ pkgs.pkgsCross.riscv64-embedded.buildPackages.gcc autoreconfHook ];
  fixupPhase = "true";
}
