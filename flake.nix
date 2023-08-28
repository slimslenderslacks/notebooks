{
  description = "The Docker LSP";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    clj-nix = {
      url = "github:jlesquembre/clj-nix";
      inputs.flake-utils.follows = "flake-utils";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    devshell = {
      url = "github:numtide/devshell";
      inputs.flake-utils.follows = "flake-utils";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    pod.url = "github:docker/babashka-pod-docker";
  };

  outputs = { self, nixpkgs, flake-utils, clj-nix, devshell, pod }:

    flake-utils.lib.eachDefaultSystem
      (system:
        let
          overlays = [
            (self: super: rec {
              nodejs = super.nodejs-18_x;
            })
            devshell.overlays.default
            (self: super: {
              clj-nix = clj-nix.packages."${system}";
            })
            (self: super: {
              pod = pod.packages."${system}";
            })
          ];
          # don't treat pkgs as meaning nixpkgs - treat it as all packages!
          pkgs = import nixpkgs {
            inherit overlays system;
          };
        in
        {
          packages = rec {
            clj = pkgs.clj-nix.mkCljBin {
              name = "docker-lsp";
              projectSrc = ./.;
              #projectSrc = builtins.path
              #{
              #name = "clj-sources";
              #path = ./.;
              #filter = path: type:
              #builtins.elem (/. + path) [
              #./src
              #./deps.edn
              #./deps-lock.edn
              #];
              #};
              main-ns = "docker.lsp.server";
              buildCommand = "clj -T:build uber";
            };
            deps-cache = pkgs.clj-nix.mk-deps-cache {
              lockfile = ./deps-lock.json;
            };
            graal = pkgs.clj-nix.mkGraalBin {
              # lazy lookup of a derivation that will exist
              cljDrv = self.packages."${system}".clj;
              graalvmXmx = "-J-Xmx8g";
              extraNativeImageBuildArgs = [
                "--native-image-info"
                "--initialize-at-build-time"
                "--enable-http"
                "--enable-https"
              ];
            };
            default = pkgs.buildEnv {
              name = "install";
              paths = [
                pkgs.pod.app
                pkgs.hadolint
                graal
              ];
            };
            docker = pkgs.dockerTools.buildImage {
              name = "docker/lsp";
              tag = "latest";
              copyToRoot = default;
              config = {
                Cmd = [ "/bin/docker-lsp" "listen" "--pod-exe-path" "/bin/docker-pod" "--log-path" "/" ];
              };
            };
            dist = pkgs.mkDerivation {
              buildInputs = [ pkgs.gh ];
              src = ./.;
              buildPhase = ''
                gh auth login --with-token < mytoken.txt
              '';
              installPhase = ''

            '';
            };
          };

          devShells.default = pkgs.devshell.mkShell {
            name = "lsp";
            packages = with pkgs; [ babashka clojure node2nix nodejs ];

            commands = [
              {
                name = "lock-clojure-deps";
                help = "update deps-lock.json whenever deps.edn changes";
                command = "nix run /Users/slim/slimslenderslacks/clj-nix#deps-lock";
              }
            ];
          };
        });
}
