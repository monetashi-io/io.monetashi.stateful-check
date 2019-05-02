(ns io.monetashi.stateful-check.core-utils)

(defmacro assert-val
  ([val]
   (when *assert*
     `(let [val# ~val]
        (if (some? val#)
          val#
          (throw (js/Error (str "Assert failed: " (pr-str '~val))))))))
  ([val message]
   (when *assert*
     `(let [val# ~val]
        (if (some? val#)
          val#
          (throw (js/Error (str "Assert failed: " ~message))))))))
