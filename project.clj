(defproject erzeugen "1.0.0-SNAPSHOT"
  :license {:name "MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.51"]
                 [navis/untangled-spec "0.3.8" :scope "test"]
                 [org.omcljs/om "1.0.0-alpha40" :scope "test"]
                 [lein-doo "0.1.6" :scope "test"]]
  :plugins [[lein-doo "0.1.6"]
            [lein-shell "0.5.0"]
            [com.jakemccrary/lein-test-refresh "0.15.0"]]
  :test-refresh {:report untangled-spec.reporters.terminal/untangled-report
                 :changes-only true
                 :with-repl true}
  :doo {:build "automated-tests"
        :paths {:karma "node_modules/karma/bin/karma"}}
  :cljsbuild {:builds
              [{:id           "test"
                :source-paths ["src" "specs" "dev"]
                :figwheel     true
                :compiler     {:main                 user
                               :output-to            "resources/public/js/specs/specs.js"
                               :output-dir           "resources/public/js/compiled/specs"
                               :asset-path           "js/compiled/specs"
                               :recompile-dependents true
                               :optimizations        :none}}
               {:id           "automated-tests"
                :source-paths ["src" "specs"]
                :compiler     {:output-to     "resources/private/js/unit-tests.js"
                               :main          erzeugen.all-tests
                               :asset-path    "js"
                               :output-dir    "resources/private/js"
                               :optimizations :none}}]}
  :aliases {"cljs-test" ["do" ["shell" "npm" "install"] ["doo" "chrome" "automated-tests" "once"]]}
  :profiles {:dev {:dependencies [[figwheel-sidecar "0.5.3-1"]
                                  [com.cemerick/piggieback "0.2.1"]
                                  [org.clojure/tools.nrepl "0.2.12"]
                                  [binaryage/devtools "0.6.1"]]
                   :source-paths ["src" "specs" "dev"]
                   :repl-options {:init-ns          user
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}})
