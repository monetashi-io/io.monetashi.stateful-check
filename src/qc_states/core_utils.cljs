(ns qc-states.core-utils
  (:require [clojure.test.check :refer [quick-check]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :refer-macros [for-all]]
            [clojure.test.check.rose-tree :as rose :refer [make-rose]]
            [qc-states.check-async :refer [quick-check-async]]
            [qc-states.async :refer [chan?] :refer-macros [go-catching <?]]
            [qc-states.command-runner :as r]
            [qc-states.command-utils :as u]
            [qc-states.command-verifier :as v]
            [qc-states.generator-utils :refer-macros [gen-do]]
            [qc-states.symbolic-values :refer [->RootVar]])
  (:require-macros [qc-states.core-utils :refer [assert-val]]))

(def setup-name "setup")


(defn generate-command-object
  "Generate the object for a command. This means first generating a
  command name using the spec's :model/generate-command function, then
  returning the full command object matching the generated command
  name. The generated name is `assoc`ed onto the command map under
  the :name key."
  [spec state]
  (gen/gen-fmap (fn [rose-tree]
                  (let [command-key (rose/root rose-tree)
                        value (get (:commands spec) command-key)
                        value (if (var? value) @value value)]
                    (assoc (assert-val value (str "Command " command-key " not found in :commands map"))
                           :name command-key)))
                (u/generate-command-name spec state)))

;; Don't remove the `size` parameter to this function! It's there so
;; we can keep track of how "long" the command list is meant to be
;; without also influencing the "size" of the generated commands. You
;; can't use gen/sized because that would also influence the size of
;; the command generators themselves.
(defn generate-commands*
  "Returns a list of rose-trees within the monad of gen/gen-pure. "
  ([spec state size] (generate-commands* spec state size 0))
  ([spec state size count]
   (gen/frequency
    [[1 (gen/gen-pure nil)]
     [size (gen-do command <- (generate-command-object spec state)
                   (if (u/check-requires command state)
                     (gen-do args-rose <- (u/generate-args command state)
                             :let [args (rose/root args-rose)]
                             (if (u/check-precondition command state args)
                               (gen-do :let [result (->RootVar count)
                                             next-state (u/model-make-next-state command state args result)]
                                       roses <- (generate-commands* spec next-state (dec size) (inc count))
                                       (gen/gen-pure (cons (rose/fmap (fn [args]
                                                                        (make-rose result (cons command args)))
                                                                      args-rose)
                                                           roses)))
                               (generate-commands* spec state size count)))
                     (generate-commands* spec state size count)))]])))

(defn shrink-roses
  "Shrink the command rose trees (by removing commands and shrinking
  command arguments)."
  [roses]
  (let [singles (rose/remove roses)
        pairs (mapcat (comp rose/remove vec) singles)]
    (concat singles pairs)))

(defn concat-command-roses
  "Take a seq of rose trees and concatenate them. Create a vector from
  the roots along with a rose tree consisting of shrinking each
  element in turn."
  [roses]
  (if (seq roses)
    (make-rose (apply vector (map rose/root roses))
               (map concat-command-roses (shrink-roses (vec roses))))
    (make-rose [] [])))

(defn generate-commands
  "Generate a seq of commands from a spec and an initial state."
  [spec state]
  (gen/gen-bind (gen/sized #(generate-commands* spec state %))
                (fn [roses]
                  (gen/gen-pure (concat-command-roses roses)))))

(defn valid-commands?
  "Verify whether a given list of commands is valid (preconditions all
  return true, symbolic vars are all valid, etc.)."
  [spec command-list]
  (let [setup-var (->RootVar setup-name)]
    (v/valid? command-list
              (if (:real/setup spec)
                #{setup-var}
                #{})
              (let [initial-state-fn (or (:model/initial-state spec)
                                         (:initial-state spec)
                                         (constantly nil))]
                (if (:real/setup spec)
                  (initial-state-fn setup-var)
                  (initial-state-fn))))))

(defn generate-valid-commands
  "Generate a set of valid commands from a specification"
  [spec]
  (->> (let [initial-state-fn (or (:model/initial-state spec)
                                  (:initial-state spec)
                                  (constantly nil))]
         (if (:real/setup spec)
           (initial-state-fn (->RootVar setup-name))
           (initial-state-fn)))
       (generate-commands spec)
       (gen/such-that (partial valid-commands? spec))))

(defn make-failure-exception [results]
  (ex-info ""
           {:results results}))

(defn spec->property
  "Turn a specification into a testable property."
  ([spec] (spec->property spec {:tries 1}))
  ([spec {:keys [tries]}]
   (for-all [commands (generate-valid-commands spec)]
            (go-catching
             (loop [tries (or tries 1)]
               (if (pos? tries)
                 (let [results (<! (r/run-commands spec commands))]
                   (if (r/passed? results)
                     (recur (dec tries))
                     (throw (make-failure-exception results))))
                 true))))))

(defn format-command [[sym-var [{name :name} _ args] :as cmd]]
  (str (pr-str sym-var) " = " (pr-str (cons name args))))

(defn print-command-results
  "Print out the results of a `r/run-commands` call. No commands are
  actually run, as the argument to this function contains the results
  for each individual command."
  ([results] (print-command-results results false))
  ([results stacktraces?]
   (try
     (doseq [[pre [type :as step]] (partition 2 1 results)]
       ;; get each state, plus the state before it (we can ignore the
       ;; first state because it's definitely not a terminal state
       ;; (:pass/:fail) and hence must have a following state which we
       ;; will see
       (case type
         :postcondition-check
         (let [[_ _ cmd _ _ _ _ _ str-result] step]
           (println "  " (format-command cmd) "\t=>" str-result))
         :fail
         (let [[_ _ _ ex] step
               [pre-type _ cmd] pre
               location (case pre-type
                          :precondition-check "checking precondition"
                          :run-command "executing command"
                          :postcondition-check "checking postcondition"
                          :next-state "making next state"
                          :next-command "checking spec postcondition")]
           (if-let [ex  ex]
             (let [show-command? (and cmd (= :run-command pre-type))]
               (if show-command?
                 (println "  " (format-command cmd) "\t=!!>" ex))
               (println "Exception thrown while" location
                        (if show-command? "" (str "- " ex)))
               (if stacktraces?
                 (prn (.-stack ex))))
             (println "Error while" location)))
         nil))
     (catch js/Error ex
       (println "Unexpected exception thrown in test runner -" ex)
       (prn (.-stack ex))))))

(defn print-test-results
  "Print the results of a test.check test in a more helpful form (each
  command with its corresponding output).

  This function will re-run both the failing test case and the shrunk
  failing test. This means that the commands will be run against the
  live system."
  [spec results {:keys [first-case? stacktraces?]}]
  (when-not (true? (:result results))
    (when first-case?
      (println "First failing test case:")
      (print-command-results (-> results :result ex-data :results) stacktraces?)
      (println "Shrunk:"))
    (print-command-results (-> results :shrunk :result ex-data :results) stacktraces?)
    (println "Seed: " (:seed results))
    (println "Visited: " (-> results :shrunk :total-nodes-visited))))

(defn run-specification
  "Run a specification. This will convert the spec into a property and
  run it using clojure.test.check/quick-check. This function then
  returns the full quick-check result."
  ([specification] (run-specification specification nil))
  ([specification {:keys [num-tests max-size seed tries]}]
   (quick-check-async (or num-tests 100)
                (spec->property specification {:tries tries})
                :seed seed
                :max-size (or max-size 200))))
