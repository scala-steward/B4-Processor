{ mkSbtDerivation, nix-filter, ripgrep, circt, callPackage, riscv-programs }:
{ hash }:
let
  mkDerivation = attrs: mkSbtDerivation ({
    pname = "b4smt";
    version = "0.1.0";
    src = nix-filter {
      root = ../.;
      include = [
        "src"
        "project"
        "build.sbt"
      ];
    };
    buildInputs = [ circt ripgrep ];
    depsSha256 = hash;
    buildPhase = ''
      echo no buildPhase set. Failing.
      false
    '';

    CHISEL_FIRTOOL_PATH = "${circt}/bin";

    fixupPhase = "true";
  } // attrs);
  b4smt = mkDerivation {
    buildPhase = ''
      sbt "runMain b4processor.B4Processor"
      cat B4Processor.sv | rg -U '(?s)module B4Processor\(.*endmodule' > B4Processor.wrapper.v
      sed -i 's/module B4Processor(/module B4ProcessorUnused(/g' B4Processor.sv
    '';

    installPhase = ''
      mkdir $out
      cp B4Processor.* $out
    '';

    passthru = {
      inherit riscv-programs mkDerivation;
      sbtTest = callPackage ./b4smt_sbt_test.nix {
        inherit b4smt;
      };
    };
  };
in
b4smt

