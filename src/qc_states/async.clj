(ns qc-states.async)

(defmacro <? [ch]
  `(throw-err (cljs.core.async/<! ~ch)))

(defmacro go-catching [& body]
  `(cljs.core.async.macros/go
     (try
       ~@body
       (catch js/Error e#
         e#))))
