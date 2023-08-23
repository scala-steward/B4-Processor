{
  description = "RISC-V Tests";

  inputs.nixpkgs.url = "nixpkgs/nixos-unstable";
  inputs.riscv-test-src = {
    url = "https://github.com/riscv-software-src/riscv-tests";
    type = "git";
    submodules = true;
    flake = false;
  };
  inputs.flake-utils.url = "github:numtide/flake-utils";

  outputs = { self, nixpkgs, riscv-test-src, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let pkgs = nixpkgs.legacyPackages.${system}; in {
        packages.default = pkgs.stdenv.mkDerivation {
          name = "riscv-tests";
          src = riscv-test-src;
          patches = [ ./riscv-test.patch ];
          configureFlags = [ "target_alias=riscv64-none-elf" ];
          enableParallelBuilding = true;
          buildInputs = with pkgs; [
            llvmPackages_16.bintools
            pkgsCross.riscv64-embedded.buildPackages.gcc
            autoreconfHook
            circt
          ];
          postInstall = ''
            find -L $out -name Makefile | xargs rm
          '';
          fixupPhase = "true";
        };

        formatter = pkgs.nixpkgs-fmt;
      });
}
