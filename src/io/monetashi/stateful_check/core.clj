(ns io.monetashi.stateful-check.core)

(defmethod cljs.test/assert-expr 'specification-correct?
  [_ msg [_ specification options]]
  `(cljs.test/async done#
                   (cljs.core.async.macros/go
                     (let [spec# ~specification
                           options# ~options
                           results# (cljs.core.async/<! (io.monetashi.stateful-check.core-utils/run-specification spec# options#))
                           ]
                       (if (true? (:result results#))
                         (cljs.test/do-report {:type :pass,
                                               :message ~msg,
                                               :expected :pass,
                                               :actual :pass})

                         ;; PROBLEMS with-out-str inside macro???
                         ;; http://dev.clojure.org/jira/browse/CLJS-1406
                         ;; moved insie
                               #_(if-let [msg# ~msg]
                                   (println msg#))

                               (let [msg (->> {:first-case? (:print-first-case? options#)
                                               :stacktraces? (:print-stacktraces? options#)}
                                              (io.monetashi.stateful-check.core-utils/build-test-results-str spec# results#))]
                                 (cljs.test/do-report {:type :fail,
                                                       :message msg,
                                                       :expected :pass,
                                                       :actual :fail})))

                       (done#)
                       (true? (:result results#))
                       ))))
