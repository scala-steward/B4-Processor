{
  description = "riscv test flake";

  inputs = {
    nixpkgs.url = "nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    riscv-test-src = {
      url = "https://github.com/riscv-software-src/riscv-tests";
      type = "git";
      submodules = true;
      flake = false;
    };
    nix-sbt = {
      url = "github:zaninime/sbt-derivation";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { self, nixpkgs, flake-utils, riscv-test-src, nix-sbt }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
      in
      {
        packages = rec {
          riscv-tests = import ./riscv-tests-files/riscv-tests.nix { inherit pkgs riscv-test-src; };
          riscv-sample-programs = import ./riscv-sample-programs/sample-programs.nix { inherit pkgs; };
          processor = nix-sbt.mkSbtDerivation.x86_64-linux {
            pname = "B4Processor";
            version = "0.1.0";
            src = ./.;
            depsSha256 = "sha256-ZBRnv88LnJnxeclRRhpVwI8eQlacgst9IctV0+OSfWY=";
            buildPhase = ''
              sbt "runMain b4processor.B4Processor"
            '';

            # install your software in $out, depending on your needs
            installPhase = ''
              mkdir $out
              cp B4Processor.* $out
            '';
          };
          default = pkgs.linkFarm "processor test programs" [
            { name = "riscv-tests"; path = riscv-tests; }
            { name = "riscv-sample-programs"; path = riscv-sample-programs; }
          ];
        };
        checks =
          {
            all = (nix-sbt.mkSbtDerivation.x86_64-linux {
              pname = "B4Processor-tests";
              version = "0.1.0";
              src = ./.;
              depsSha256 = "sha256-ZBRnv88LnJnxeclRRhpVwI8eQlacgst9IctV0+OSfWY=";
              buildInputs = with pkgs; [ verilog ];
              buildPhase = ''
                ln -s ${self.packages.${system}.default} programs
                sbt test
              '';
              installPhase = ''
                mkdir $out
                [ -d test_run_dir ] && cp -r test_run_dir $out || true
              '';
            });
          };
        formatter = pkgs.nixpkgs-fmt;
      });
}
