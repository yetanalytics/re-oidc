(ns com.yetanalytics.re-oidc.util
  (:require [re-frame.core :as re-frame]))

(defn dispatch-cb
  [qvec]
  (fn [& args]
    (re-frame/dispatch (into qvec args))))

(defn cb-fn-or-dispatch
  [x]
  (cond
    (vector? x) (dispatch-cb x)
    (fn? x) x))

(defn handle-promise
  "Handle a promise result from the lib"
  [p & [?on-success ?on-failure]]
  (cond-> p
    ?on-failure (.catch (cb-fn-or-dispatch ?on-failure))
    ?on-success (.then (cb-fn-or-dispatch ?on-success))))

(defn js-error->clj
  [handler-id js-error]
  (let [?exd (ex-data js-error)]
    (cond-> {:name (.-name js-error)
             :message (ex-message js-error)
             :handler handler-id}
      ?exd (assoc :ex-data ?exd))))

(defn expired?
  [expires-at]
  (< (* expires-at 1000) (.now js/Date)))
