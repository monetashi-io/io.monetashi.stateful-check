(ns qc-states.async-test
  (:require  [cljs.test :refer-macros [is deftest]]
             [clojure.test.check.generators :as gen]
             [cljs.core.async :refer [<! >! timeout ]]
             [qc-states.async :refer (<!prn) :refer-macros [<? go-catching]]
             [qc-states.core :refer [specification-correct?] :refer-macros []]
             [qc-states.core-utils :refer [run-specification] :refer-macros []]

             [cljs.test :as t]
             [taoensso.timbre :as timbre])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))
;;
;; Implementation
;;

(def global-state (atom #{}))

;;
;; Generative commands
;;

(def add-command
  {:model/args (fn [_] [gen/nat])
   :real/command #(swap! global-state conj %)
   :next-state (fn [state [arg] _]
                 (conj (or state #{}) arg))})

(def remove-command
  {:model/requires (fn [state] (seq state))
   :model/args (fn [state] [(gen/elements state)])
   :real/command #(swap! global-state disj %)
   :next-state (fn [state [arg] _]
                 (disj state arg))})

(def contains?-command
  {:model/requires (fn [state] (seq state))
   :model/args (fn [state] [(gen/one-of [(gen/elements state) gen/nat])])
   :real/command #(contains? @global-state %)
   :real/postcondition (fn [state _ [value] result]
                         (= (contains? state value) result))})

(def empty?-command
  {:real/command #(empty? @global-state)
   :real/postcondition (fn [state _ _ result]
                         (= (empty? state) result))})

(def empty-command
  {:real/command (fn [] (reset! global-state #{}))
   :next-state (fn [state _ _] #{})})

;;
;; Generative specification
;;

(def specification
  {:commands {:add #'add-command
              :remove #'remove-command
              :contains? #'contains?-command
              :empty? #'empty?-command
              :empty #'empty-command}
   :initial-state (constantly #{})
   :real/setup #(reset! global-state #{1})})

;; NOTE: this macroexpands to a real test
(deftest atomic-set-test
  (is (specification-correct? specification) "test-message") )

;; NOTE: this is direct call
#_ (<!prn (specification-correct? specification))
#_ (<!prn (go (:shrunk (<! (run-specification specification)))))

;; NOTE: what becomes is specification correct?
#_ (macroexpand '(is (specification-correct? specification)))
