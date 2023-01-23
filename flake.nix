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
      inputs.flake-utils.follows = "flake-utils";
    };
  };

  outputs = { self, nixpkgs, flake-utils, riscv-test-src, nix-sbt }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
        B4ProcessorDerivation = attrs: nix-sbt.mkSbtDerivation.x86_64-linux ({
          pname = "B4Processor";
          version = "0.1.0";
          src = ./.;
          depsSha256 = "sha256-xOZ56Vmf4oHo7FDHOK7a+RK0LdQE9PVxfM4wLumzjxA=";
          buildPhase = ''
            sbt "runMain b4processor.B4Processor"
          '';

          # install your software in $out, depending on your needs
          installPhase = ''
            mkdir $out
            cp B4Processor.* $out
          '';

          fixupPhase = "true";
        } // attrs);
      in
      {
        packages = rec {
          riscv-tests = import ./riscv-tests-files/riscv-tests.nix { inherit pkgs riscv-test-src; };
          riscv-sample-programs = import ./riscv-sample-programs/sample-programs.nix { inherit pkgs; };
          processor = B4ProcessorDerivation { };
          default = pkgs.linkFarm "processor test programs" [
            { name = "riscv-tests"; path = riscv-tests; }
            { name = "riscv-sample-programs"; path = riscv-sample-programs; }
          ];
        };
        checks =
          {
            all = B4ProcessorDerivation {
              pname = "B4Processor-tests";
              buildInputs = with pkgs; [ verilog verilator stdenv.cc zlib ];
              buildPhase = ''
                ln -s ${self.packages.${system}.default} programs
                sbt test
              '';
              installPhase = ''
                mkdir $out
                [ -d test_run_dir ] && cp -r test_run_dir $out || true
              '';
            };
          };
        formatter = pkgs.nixpkgs-fmt;
      });
}
