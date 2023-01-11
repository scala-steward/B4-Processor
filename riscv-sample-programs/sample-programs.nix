{ pkgs }: pkgs.stdenv.mkDerivation {
  name = "riscv-sample-programs";
  src = ./.;
  buildInputs = with pkgs;[ pkgsCross.riscv64-embedded.buildPackages.gcc ];
  installPhase = "
    mkdir $out
    cp **/*.{hex,bin,dump} $out
  ";
}
