(ns com.yetanalytics.re-oidc.demo-server
  (:require
   [ring.util.response :refer [resource-response content-type not-found]]))

;; the routes that we want to be resolved to index.html
(def route-set #{"/" "/login-callback" "/logout-callback"})

(defn handler [req]
  (or
   (when (route-set (:uri req))
     (some-> (resource-response "index.html" {:root "public"})
             (content-type "text/html; charset=utf-8")))
   (not-found "Not found")))
