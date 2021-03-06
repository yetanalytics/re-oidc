(ns ^:figwheel-hooks com.yetanalytics.re-oidc.demo
  "In this example, we initialize re-oidc from a remote config file"
  (:require
   [goog.dom :as gdom]
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]
   [re-frame.core :as re-frame]
   [day8.re-frame.http-fx]
   [com.yetanalytics.re-oidc :as re-oidc]
   [goog.events :as events]
   [clojure.pprint :as pp]
   [ajax.core :as ajax]
   [goog.string :refer [format]]
   goog.string.format))

(defn- push-state
  "Push history state to clean up on login/logout"
  [path]
  (.pushState js/window.history
              (clj->js {})
              js/document.title
              path))

;; config for re-oidc that won't change, isn't loaded dynamically
(def static-config
  {:auto-login false
   :on-login-success #(push-state "/")
   :on-logout-success #(push-state "/")})

;; Init the demo's DB
(re-frame/reg-event-db
 ::init-db
 (fn [_ _]
   {}))

;; Fetch the OIDC config from a server
(re-frame/reg-event-fx
 ::get-oidc-config!
 (fn [{:keys [db]} _]
   {:http-xhrio {:method :get
                 :uri "/oidc.json"
                 :response-format (ajax/json-response-format
                                   {:keywords? true})
                 :on-success [::recv-oidc-config]
                 :on-failure [::fail-oidc-config]}}))

;; Receive the OIDC config and initialize
(re-frame/reg-event-fx
 ::recv-oidc-config
 (fn [ctx [_ config]]
   {:fx [[:dispatch
          ;; Initialize OIDC from the remote config
          [::re-oidc/init
           (assoc static-config
                  :user-store :local-storage
                  ;; These config options are passed directly to the OIDC client
                  :oidc-config
                  config)]]]}))

(re-frame/reg-event-fx
 ::fail-oidc-config
 (fn [ctx [_ {:keys [status]}]]
   (.error js/console "Failed to fetch OIDC config, status:" status)
   {}))

;; Attempt a call to the API server when logged in
(re-frame/reg-event-fx
 ::echo-token!
 (fn [{{status ::re-oidc/status
        :as db} :db} _]
   (if (= :loaded status)
     (let [{{:keys [access-token]} ::re-oidc/user} db]
       {:http-xhrio {:uri "http://0.0.0.0:8081/api"
                     :method :get
                     :headers {"Authorization" (format "Bearer %s" access-token)}
                     :response-format (ajax/json-response-format
                                       {:keywords? true})
                     :on-success [::recv-token-echo]
                     :on-failure [::fail-token-echo]}})
     (do
       (.error js/console "Can't call API server if not logged in!")
       {}))))

;; receive token echo from api
(re-frame/reg-event-db
 ::recv-token-echo
 (fn [db [_ token-echo]]
   (assoc db ::token-echo token-echo)))

(re-frame/reg-event-fx
 ::fail-token-echo
 (fn [ctx [_ {:keys [status]}]]
   (.error js/console "Failed to get token echo from api server, status:" status)
   {}))

;; Compose init events for the demo db & getting remote config
(re-frame/reg-event-fx
 ::init!
 (fn [_ _]
   {:fx [[:dispatch [::init-db]]
         ;; Fetch the OIDC config, initializing the UserManager on success
         [:dispatch [::get-oidc-config!]]]}))

;; A simple sub to see the DB
(re-frame/reg-sub
 ::db-debug
 (fn [db _]
   (-> db
       pp/pprint
       with-out-str)))

(defn get-app-element []
  (gdom/getElement "app"))

(defn process-callbacks!
  "Detect post login/logout callbacks and issue route dispatch to re-oidc."
  [& _]
  (case js/window.location.pathname
    "/login-callback" (re-frame/dispatch
                       [::re-oidc/login-callback
                        static-config
                        js/window.location.search])
    "/logout-callback" (re-frame/dispatch
                        [::re-oidc/logout-callback
                         static-config])
    nil))

(defn hello-world []
  [:div
   [:h2 "DEMO"]
   [:p
    (if @(re-frame/subscribe [::re-oidc/logged-in?])
      "You are logged in"
      "You are logged out")]
   [:pre @(re-frame/subscribe [::db-debug])]
   ;; Since the login/logout actions must run after init,
   ;; you can use the ::re-oidc/status key for things like loading
   (case @(re-frame/subscribe [::re-oidc/status])
     nil
     [:div
      [:button "Loading..."]]
     :loaded
     [:div
      [:button
       {:on-click #(re-frame/dispatch [::echo-token!])}
       "Echo Token"]
      [:button
       {:on-click #(re-frame/dispatch [::re-oidc/logout])}
       "Log out"]]
     ;; :init/:unloaded
     [:div
      [:button
       {:on-click #(re-frame/dispatch [::re-oidc/login])}
       "Log in"]])])

(defn mount [el]
  (rdom/render [hello-world] el))

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (mount el)))

(defn init! []
  (re-frame/dispatch-sync [::init!])
  (process-callbacks!)
  (mount-app-element))

(defonce init
  (do
    (init!)))

;; specify reload hook with ^:after-load metadata
(defn ^:after-load on-reload []
  (println "figwheel reload!")
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
