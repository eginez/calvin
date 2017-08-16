(defproject calvin "0.1.0"
  :description "minimalistic clojurescript only build tool"
  :url "https://github.com/eginez/calvin"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.293"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojars.eginez/huckleberry "0.2.0"]
                 [andare "0.4.0"]]
  :source-paths ["src/main/clojure" "target/classes"]
  :test-paths ["src/test/clojure"]
  :clean-targets ["out" "release" "target"]
  :target-path "target"
  :figwheel {:server-port 3450}
  :plugins [[lein-npm "0.6.1"]
            [lein-figwheel "0.5.11"]
            [lein-cljsbuild "1.1.3"]]
  :npm {
        :dependencies [[xml2js "0.4.17"]
                       [request "2.74.0"]]
        :devDependencies [[source-map-support "0.4.0"]
                          [ws "3.1.0"]]}

  :cljsbuild {
              :builds [
                       {:id "dev"
                        :source-paths ["src/main/clojure"]
                        :figwheel true
                        :compiler {
                                   :main eginez.calvin.figwheel-server
                                   :output-to "out/dev/figwheel-server.js"
                                   :output-dir "dev"
                                   :target :nodejs
                                   :optimizations :none
                                   :pretty-print true
                                   :parallel-build true
                                   :source-map true}}

                       {:id "prod"
                        :source-paths ["src/main/clojure"]
                        :compiler {
                                   :main eginez.calvin.core
                                   :output-to "package/prod/index.js"
                                   :target :nodejs
                                   :output-dir "package/prod"
                                   :optimizations :simple
                                   :pretty-print true
                                   :parallel-build true
                                   :source-map "package/prod/sourcemap.js"}}]})
