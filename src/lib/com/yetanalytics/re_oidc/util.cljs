(ns com.yetanalytics.re-oidc.util
  (:require [clojure.string :as cs]
            [re-frame.core :as re-frame]))

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

(defn absolve-uri
  "Convert a relative path to an absolute URI string based on browser location."
  [uri]
  (if (and uri
           (cs/starts-with? uri "/"))
    (.-href (new js/URL uri js/window.location))
    uri))

(def redirect-uri-keys
  #{:redirect_uri
    "redirect_uri"
    :post_logout_redirect_uri
    "post_logout_redirect_uri"})

(defn absolve-redirect-uris
  "Convert relative redirect URIs to absolute based on browser location."
  [oidc-config]
  (reduce-kv
   (fn [m k v]
     (assoc m
            k
            (if (redirect-uri-keys k)
              (absolve-uri v)
              v)))
   {}
   oidc-config))
