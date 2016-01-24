(ns qc-states.core
  (:require [cljs.test :as t]
            [qc-states.async :refer [chan?] :refer-macros [go-catching <?]]
            [clojure.test.check :refer [quick-check]]
            [cljs.core.async :refer [<! >! timeout ]]
            [qc-states.core-utils :as utils]))

(defn ^{:deprecated "0.3.0"} reality-matches-model
  "Create a property which checks a given qc-states
  specification."
  [spec]
  (utils/spec->property spec))

(defn print-test-results
  #_{:deprecated "0.3.0",
   :doc (:doc (meta #'utils/build-test-results-str))
   :arglists (:arglists (meta #'utils/build-test-results-str))}
  [spec results options]
  (print (utils/build-test-results-str spec results options)))

(defn specification-correct?
  "Test whether or not the specification matches reality. This
  generates test cases and runs them.

  When used within an `is` expression two extra options can be
  supplied:

    :print-first-case?, which instructs the test-case printer to print
  the command list prior to shrinking as well as the shrunk list

    :print-stacktrace?, which instructs the test-case printer to print
  the stacktrace of any exceptions"
  ([specification] (specification-correct? specification nil))
  ([specification options]
   (go-catching
    (true? (:result (<? (utils/run-specification specification options)))))))
;; We need this to be a separate form, for some reason. The attr-map
;; in defn doesn't work if you use the multi-arity form.
(alter-meta! #'specification-correct? assoc :arglists
             (:arglists (meta #'utils/run-specification)))
