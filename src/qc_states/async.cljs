(ns qc-states.async
  (:require  [cljs.core.async :refer [take! <! >! timeout ]]
             [cljs.core.async.impl.protocols :as impl]
             [cljs.pprint :refer [pprint]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn error? [x]
  (instance? js/Error x))


(defn throw-err [e]
  (when (error? e) (throw e))
  e)

(defn throw-if-error-async!! [c]
  (take! c #(if (error? %)
              (throw %)
              %)))

(defn <!prn [c]
  (take! c pprint))

;; No chan? predicat as Hickey
;; states want an interface function for every
;; protocol??
(defn chan? [v]
  (or
   (satisfies? impl/ReadPort v)
   (satisfies? impl/WritePort v)))
