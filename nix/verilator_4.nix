{ verilator, fetchFromGitHub }: (verilator.overrideAttrs (old: rec{
  inherit (old) pname;
  version = "4.228";
  src = fetchFromGitHub {
    owner = pname;
    repo = pname;
    rev = "v${version}";
    sha256 = "sha256-ToYad8cvBF3Mio5fuT4Ce4zXbWxFxd6smqB1TxvlHao=";
  };
  doCheck = false;
}))
