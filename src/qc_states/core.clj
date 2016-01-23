(ns qc-states.core)

(defmethod cljs.test/assert-expr 'specification-correct?
  [_ msg [_ specification options]]
  `(do
     (cljs.test/async done#
                      (qc-states.async/go-catching
                       (let [spec# ~specification
                             options# ~options
                             results# (qc-states.async/<? (qc-states.core-utils/run-specification spec# options#))]
                         (if (true? (:result results#))
                           (cljs.test/do-report {:type :pass,
                                                 :message ~msg,
                                                 :expected :pass,
                                                 :actual :pass})
                           (cljs.test/do-report {:type :fail,
                                                 :message (with-out-str
                                                            (if-let [msg# ~msg]
                                                              (println msg#))
                                                            (->> {:first-case? (:print-first-case? options#)
                                                                  :stacktraces? (:print-stacktraces? options#)}
                                                                 (qc-states.core-utils/print-test-results spec# results#))),
                                                 :expected :pass,
                                                 :actual :fail}))
                         (true? (:result results#))
                         (done#)
                         )))))
