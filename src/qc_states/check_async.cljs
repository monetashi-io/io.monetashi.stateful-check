(ns qc-states.check-async
  (:require [clojure.test.check.generators :as gen]
            [clojure.test.check.clojure-test :as ct]
            [qc-states.async :refer [chan?] :refer-macros [go-catching <?]]
            [clojure.test.check.random :as random]
            [clojure.test.check.rose-tree :as rose]
            [clojure.test.check :as tc]))

(defn quick-check-async
  "Tests `property` `num-tests` times.
  Takes optional keys `:seed` and `:max-size`. The seed parameter
  can be used to re-run previous tests, as the seed used is returned
  after a test is run. The max-size can be used to control the 'size'
  of generated values. The size will start at 0, and grow up to
  max-size, as the number of tests increases. Generators will use
  the size parameter to bound their growth. This prevents, for example,
  generating a five-thousand element vector on the very first test.

  Examples:

      (def p (for-all [a gen/pos-int] (> (* a a) a)))
      (quick-check 100 p)
  "
  [num-tests property & {:keys [seed max-size] :or {max-size 200}}]
  (let [[created-seed rng] (tc/make-rng seed)
        size-seq (gen/make-size-range-seq max-size)]
    (go-catching
     (loop [so-far 0
            size-seq size-seq
            rstate rng]
       (if (== so-far num-tests)
         (tc/complete property num-tests created-seed)
         (let [[size & rest-size-seq] size-seq
               [r1 r2] (random/split rstate)
               result-map-rose (gen/call-gen property r1 size)
               result-map (rose/root result-map-rose)
               result (<? (:result result-map))
               args (:args result-map)]
           (if (tc/not-falsey-or-exception? result)
             (do
               (ct/report-trial property so-far num-tests)
               (recur (inc so-far) rest-size-seq r2))
             (tc/failure property result-map-rose so-far size created-seed))))))))
