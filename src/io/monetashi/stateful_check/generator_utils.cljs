(ns io.monetashi.stateful-check.generator-utils
  (:require [clojure.test.check.generators :as gen]))

(defn to-generator
  "Convert a value into a generator, recursively. This means:
    + generator? -> the value
    + sequential? -> gen/tuple with each sub-value already processed
    + map? -> gen/hash-map with each value (not keys) already processed
    + otherwise -> gen/return the value"
  [value]
  (cond (gen/generator? value) value
        (sequential? value) (apply gen/tuple (map to-generator value))
        (map? value) (apply gen/hash-map (mapcat (fn [[k v]]
                                                   [k (to-generator v)])
                                                 value))
        :else (gen/return value)))
