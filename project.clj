(defproject boymaas/qc-states "0.1.0-SNAPSHOT"
  :description "cljs stateful-check conversion"
  :url "http://www.boymaas.nl/"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.228"]
                 [org.clojure/test.check "0.9.0"]

                 [com.cemerick/piggieback "0.2.1"]
                 [org.clojure/core.async "0.2.374"]]

  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :jvm-opts ^:replace ["-Xmx1g" "-server"]
  :plugins [[lein-npm "0.6.1"]]
  :npm {:dependencies [[source-map-support "0.4.0"]]}
  :source-paths ["src" "target/classes"]
  :clean-targets ["out" "release"]
  :target-path "target")
