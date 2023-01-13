{ pkgs }: pkgs.stdenv.mkDerivation {
  name = "riscv-sample-programs";
  src = ./.;
  nativeBuildInputs = with pkgs;[ pkgsCross.riscv64-embedded.buildPackages.gcc ];
  enableParallelBuilding = true;
  installPhase = "
    mkdir $out
    cp **/*.{hex,bin,dump} $out
  ";
  fixupPhase = "true";
}
