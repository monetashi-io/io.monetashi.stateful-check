(ns io.monetashi.stateful-check.command-runner
  (:require [clojure.walk :as walk]
            [io.monetashi.stateful-check.async :refer [chan?] :refer-macros [go-catching <?]]
            [io.monetashi.stateful-check.command-utils :as u]
            [io.monetashi.stateful-check.symbolic-values
             :refer
             [->RootVar get-real-value SymbolicValue]]
            [taoensso.timbre :as timbre])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defmulti step-command-runner
  "Step the command runner state machine one step. Each state in the
  state machine is represented by a \"variant\", which is a vector
  with a key (the state name) and a series of values. What work needs
  to be done in each state is taken care of by this method's
  implementations, and they return a new state variant."
  (fn [state-name & _] state-name))

;; :next-command, :run-command, :next-state, :postcondition-check
;; :pass, :fail

(defmethod step-command-runner :next-command
  [_ spec command-list results state]
  (if (seq command-list)
    (try (if (u/check-spec-postcondition spec state)
           (let [[sym-var [command & raw-args]] (first command-list)
                 args (walk/prewalk (fn [value]
                                      (if (satisfies? SymbolicValue value)
                                        (get-real-value value results)
                                        value))
                                    raw-args)]
             [:run-command spec
              [sym-var [command args raw-args]]
              (next command-list)
              results
              state])
           [:fail spec state])
         (catch js/Error ex
           [:fail spec state ex]))
    [:pass spec state]))

(defmethod step-command-runner :run-command
  [_ spec [sym-var [command args raw-args] :as current] command-list results state]
  (go
    (try (let [result (u/run-command command args)
               result (if (chan? result)
                        (<? result)
                        result)
               results (assoc results sym-var result)]
           [:next-state spec
            current
            command-list
            results
            state
            result])
         (catch js/Error ex
           [:fail spec state ex]))))

(defmethod step-command-runner :next-state
  [_ spec [sym-var [command args raw-args] :as current] command-list results state result]
  (try [:postcondition-check spec
        current
        command-list
        results
        (u/real-make-next-state command state args result)
        state
        result
        (pr-str result) ;; this is for debug purposes, as it
                        ;; effectively takes a snapshot of the object
        ]
       (catch js/Error ex
         [:fail spec state ex])))

(defmethod step-command-runner :postcondition-check
  [_ spec [sym-var [command args raw-args] :as current] command-list results next-state prev-state result _]
  (go
    (try (let [outcome (u/check-postcondition command prev-state next-state args result)
               outcome (if (chan? outcome)
                        (<? outcome)
                        outcome)]
           (if outcome
             [:next-command spec
              command-list
              results
              next-state]
             [:fail spec next-state]))
         (catch js/Error ex
           [:fail spec next-state ex]))))

;; terminal states, so return `nil`
(defmethod step-command-runner :fail [_ spec state & _]
  (u/run-spec-cleanup spec state)
  nil)
(defmethod step-command-runner :pass [_ spec state & _]
  (u/run-spec-cleanup spec state)
  nil)

(defn run-commands
  "Run the given list of commands with the provided initial
  results/state. Returns a lazy seq of states from the command
  runner."
  [spec command-list]
  (go-catching
   (let [state-fn (or (:real/initial-state spec)
                      (:initial-state spec)
                      (constantly nil))
         setup-fn (:real/setup spec)
         setup-value (if setup-fn (setup-fn))
         setup-value (if (chan? setup-value)
                       (<! setup-value)
                       setup-value)
         results (if setup-fn
                   {(->RootVar "setup") setup-value})
         state (if setup-fn
                 (state-fn setup-value)
                 (state-fn))
         state (if (chan? state)
                 (<! state)
                 state)
         ]
     ;; NOTE: every step-command-runner will return the parameters to
     ;;       its next call. Nice, how do we make this async??
     ;; x => (f x)  => (f (f x))

     ;; implement an async-iterate? Which will realize the async values first
     (loop [params [:next-command spec command-list results state]
               step-results []]
       ;; when we get new parms, run the next step
       (if-let [params' (apply step-command-runner params)]
         (let  [params' (if (chan? params')
                          (<! params')
                          params')]
           (recur params' (conj step-results params')))
         ;; else just return our results
         step-results))


     #_(->> [:next-command spec command-list results state]
            (iterate (partial apply step-command-runner))
            (take-while (complement nil?))
            doall))))

(defn passed?
  "Determine whether a list of command runner states represents a
  successfully completed execution."
  [command-results]
  (= (first (last command-results)) :pass))

(defn extract-exception
  "Return the exception thrown during the execution of commands for
  this result list."
  [command-results]
  (let [[type _ _ exception] (last command-results)]
    (if (= type :fail)
      exception)))
