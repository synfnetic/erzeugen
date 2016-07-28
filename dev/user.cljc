(ns user
  #?(:cljs (:require-macros
             [untangled-spec.reporters.suite :as ts]))
  (:require [erzeugen.erator :as core]
            #?@(:cljs ([untangled-spec.reporters.impl.suite]
                       [erzeugen.tests-to-run]
                       [devtools.core :as devtools]))
            #?@(:clj ([figwheel-sidecar.repl-api :as ra]
                      [figwheel-sidecar.repl :as r]))))

#?(:clj
    (def figwheel-config
      {:figwheel-options {:css-dirs ["resources/public/css"]}
       :build-ids        ["test"]
       :all-builds       (r/get-project-cljs-builds)}))

#?(:clj
    (defn start-figwheel
      "If passed no arguments, will run builds from :all-builds that are in the system properties.
      If passed a non empty argument, it will use those build ids.
      Otherwise it defaults to :builds-ids"
      ([]
       (let [props (System/getProperties)
             all-builds (->> figwheel-config :all-builds (mapv :id))]
         (start-figwheel (keys (select-keys props all-builds)))))
      ([build-ids]
       (let [default-build-ids (:build-ids figwheel-config)
             build-ids (if (empty? build-ids) default-build-ids build-ids)]
         (println "STARTING FIGWHEEL ON BUILDS: " build-ids)
         (ra/start-figwheel! (assoc figwheel-config :build-ids build-ids))
         (ra/cljs-repl)))))

#?(:cljs
    (do
      (enable-console-print!)
      (devtools/install!)
      (ts/deftest-all-suite erzeugen-specs #".*-spec")
      (erzeugen-specs)))
