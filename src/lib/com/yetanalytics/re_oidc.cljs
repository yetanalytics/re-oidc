(ns com.yetanalytics.re-oidc
  (:require [cljsjs.oidc-client :refer [UserManager Log WebStorageStateStore]]
            [re-frame.core :as re-frame]
            [clojure.spec.alpha :as s :include-macros true]
            [com.yetanalytics.re-oidc.user :as user]
            [com.yetanalytics.re-oidc.util :as u]))

;; OIDC lib logging can be enabled:

#_(set! Log.logger js/console)

(s/def ::status #{:init :loaded :unloaded})
(s/def ::user user/user-spec)
(s/def ::callback #{:login :logout})
(s/def ::login-query-string string?)

(s/def :com.yetanalytics.re-oidc.error/name string?)
(s/def :com.yetanalytics.re-oidc.error/message string?)
(s/def :com.yetanalytics.re-oidc.error/handler qualified-keyword?)
(s/def :com.yetanalytics.re-oidc.error/ex-data qualified-keyword?)

(s/def ::error
  (s/keys :req-un [:com.yetanalytics.re-oidc.error/name
                   :com.yetanalytics.re-oidc.error/message
                   :com.yetanalytics.re-oidc.error/handler]
          :opt-un [:com.yetanalytics.re-oidc.error/ex-data]))

(s/def ::errors (s/every ::error))

;; A (partial) spec for what re-oidc puts in the re-frame db
(def partial-db-spec
  (s/keys :opt [::status
                ::user
                ::callback
                ::login-query-string
                ::errors]))

(defonce user-manager
  (atom nil))

(defn reg-events!
  "Register event callbacks to re-frame on the OIDC UserManager"
  [^UserManager user-manager
   {:keys [on-user-loaded
           on-user-unloaded]}]
  (doto user-manager.events
    (.addUserLoaded
     (cond-> (u/dispatch-cb [::user-loaded])
       on-user-loaded
       (juxt (u/cb-fn-or-dispatch on-user-loaded))))
    (.addUserUnloaded
     (cond-> (u/dispatch-cb [::user-unloaded])
       on-user-unloaded
       (juxt (u/cb-fn-or-dispatch on-user-unloaded))))
    ;; We set automaticSilentRenew to true and these are done for us
    #_(.addAccessTokenExpiring
     (u/dispatch-cb [::access-token-expiring]))
    (.addAccessTokenExpired
     (u/dispatch-cb [::access-token-expired]))
    (.addSilentRenewError
     (u/dispatch-cb [::silent-renew-error]))
    ;; session monitoring requires an iframe
    ;; this breaks Figwheel and makes dev hard
    ;; TODO: enable on-demand for those with iframe-friendly idp settings
    #_(.addUserSignedIn
     (u/dispatch-cb [::user-signed-in]))
    #_(.addUserSignedOut
     (u/dispatch-cb [::user-signed-out]))
    #_(.addUserSessionChanged
     (u/dispatch-cb [::user-session-changed]))))

(defn init!
  "Initialize the OIDC UserManager from config + calllbacks. Idempotent"
  [user-manager config lifecycle-callbacks]
  (if user-manager
    user-manager
    (doto (UserManager. (clj->js config))
      (reg-events! lifecycle-callbacks))))

(re-frame/reg-fx
 ::init-fx
 (fn [{:keys [config
              state-store
              user-store]
       :or {state-store :local-storage
            user-store :session-storage}
       :as init-input}]
   (swap! user-manager
          init!
          (assoc
           config
           "stateStore"
           (new WebStorageStateStore
                #js {:store (case state-store
                              :local-storage
                              js/window.localStorage
                              :session-storage
                              js/window.sessionStorage
                              ;; custom
                              state-store)})
           "userStore"
           (new WebStorageStateStore
                #js {:store (case user-store
                              :local-storage
                              js/window.localStorage
                              :session-storage
                              js/window.sessionStorage
                              ;; custom
                              user-store)}))
          (select-keys init-input
                       [:on-user-loaded
                        :on-user-unloaded]))))

(defn- throw-not-initialized!
  []
  (throw (ex-info "UserManager not Initialized!"
                  {:type ::user-manager-not-initialized})))

(defn- get-user-manager
  []
  (if-some [user-manager @user-manager]
    user-manager
    (throw-not-initialized!)))

(re-frame/reg-fx
 ::get-user-fx
 (fn [{:keys [on-success
              on-failure
              auto-login]
       :or {auto-login false}}]
   (let [on-failure (or on-failure
                        [::add-error ::get-user-fx])]
     (-> (get-user-manager)
         .getUser
         (u/handle-promise
          (cond-> (fn [?user]
                    (if-let [logged-in-user (and ?user
                                                 (not
                                                  (some-> ?user
                                                          .-expires_at
                                                          u/expired?))
                                                 ?user)]
                      (re-frame/dispatch [::user-loaded logged-in-user])
                      (when auto-login
                        (re-frame/dispatch [::login]))))
            on-success
            (juxt (u/cb-fn-or-dispatch on-success)))
          on-failure)))))

(re-frame/reg-fx
 ::signin-redirect-fx
 (fn [{:keys [on-success
              on-failure]}]
   (let [on-failure (or on-failure
                        [::add-error ::signin-redirect-fx])]
     (-> (get-user-manager)
         .signinRedirect
         (u/handle-promise on-success on-failure)))))

(re-frame/reg-fx
 ::signin-redirect-callback-fx
 (fn [{:keys [on-success
              on-failure
              query-string]}]
   (let [on-failure (or on-failure
                        [::add-error ::signin-redirect-callback-fx])
         um (get-user-manager)]
     (-> um
         (.signinRedirectCallback query-string)
         (u/handle-promise on-success on-failure)
         (.then #(.clearStaleState um))))))

(re-frame/reg-fx
 ::signout-redirect-fx
 (fn [{:keys [on-success
              on-failure]}]
   (let [on-failure (or on-failure
                        [::add-error ::signout-redirect-fx])]
     (-> (get-user-manager)
         .signoutRedirect
         (u/handle-promise on-success on-failure)))))

(re-frame/reg-fx
 ::signout-redirect-callback-fx
 (fn [{:keys [on-success
              on-failure]}]
   (let [on-failure (or on-failure
                        [::add-error ::signout-redirect-callback-fx])]
     (-> (get-user-manager)
         .signoutRedirectCallback
         (u/handle-promise on-success on-failure)))))

(defn add-error
  "Add a thrown error to the list in the db"
  [db [_ handler-id js-error]]
  (update db
          :errors
          (fnil conj [])
          (u/js-error->clj
           handler-id
           js-error)))

(re-frame/reg-event-db
 ::add-error
 add-error)

(defn user-loaded
  "Load a user object from js into the db and set status to :loaded"
  [db [_ js-user]]
  (let [id-token (.-id_token js-user)
        access-token (.-access_token js-user)
        expires-at (.-expires_at js-user)
        refresh-token (.-refresh_token js-user)
        token-type (.-token_type js-user)
        state (.-state js-user)
        session-state (.-session_state js-user)
        scope (.-scope js-user)
        profile (js->clj (.-profile js-user))]
    (assoc db
           ::status :loaded
           ::user
           {:id-token id-token
            :access-token access-token
            :refresh-token refresh-token
            :expires-at expires-at
            :token-type token-type
            :state state
            :scope scope
            :session-state session-state
            :profile profile})))

(re-frame/reg-event-db
 ::user-loaded
 user-loaded)

(defn user-unloaded
  "Remove any present user and set status to :unloaded"
  [db _]
  (-> db
      (dissoc ::user)
      (assoc ::status :unloaded)))

(re-frame/reg-event-db
 ::user-unloaded
 user-unloaded)

;; TODO: Possibly more behavior on errors
(def dispatch-unloaded
  (constantly
   {:fx [[:dispatch [::user-unloaded]]]}))

(re-frame/reg-event-fx
 ::silent-renew-error
 dispatch-unloaded)

(re-frame/reg-event-fx
 ::access-token-expired
 dispatch-unloaded)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; "Public" API ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (maybe) Pre-initialization events

;; Add login redirect callback state, usually from routing

(defn login-callback
  "Process OIDC login callback data from a query string.
  Requires configuration map for success/failure handlers and a query string."
  [{{?status ::status
     :as db} :db} [_
                   {:keys [on-login-success
                           on-login-failure]}
                   qstring]]
  (cond
    ;; Pre-init
    (nil? ?status) {:db (assoc db
                               ::callback :login
                               ::login-query-string qstring)}
    (#{:init
       :unloaded}
     ?status) {:db db
               :fx [[::signin-redirect-callback-fx
                     {:query-string qstring
                      :on-success on-login-success
                      :on-failure on-login-failure}]]}
    :else
    (do
      (.warn js/console
             "::re-oidc/login-callback called with unknown status"
             (name ?status))
      {})))

(re-frame/reg-event-fx
 ::login-callback
 login-callback)

;; Add logout redirect callback state, usually from routing
(defn logout-callback
  "Process OIDC logout callback"
  [{{?status ::status
     :as db} :db} [_
                   {:keys [on-logout-success
                           on-logout-failure]}]]
  (case ?status
    ;; Pre-init
    nil {:db (assoc db
                    ::callback :logout)}
    :init {:db db
           :fx [[::signout-redirect-callback-fx
                 {:on-success on-logout-success
                  :on-failure on-logout-failure}]]}
    (do
      (.warn js/console
             "::re-oidc/logout-callback called with unknown status"
             (name ?status))
      {})))

(re-frame/reg-event-fx
 ::logout-callback
 logout-callback)

;; Initialization
;; Sets up the OIDC client from config and queues login/logout callback
;; Or if not on a callback, attempts to get the user from storage

(defn init
  "Initialize the OIDC client. If there are callbacks waiting for processing
  execute them, otherwise get the user from storage (if possible)"
  [{{status ::status
     ?callback ::callback
     ?qstring ::login-query-string
     :as db} :db} [_ {:keys [oidc-config
                             auto-login
                             on-login-success
                             on-login-failure
                             on-logout-success
                             on-logout-failure
                             on-get-user-success
                             on-get-user-failure
                             on-user-loaded
                             on-user-unloaded
                             state-store
                             user-store
                             redirect-uri-absolution]
                      :or {auto-login false
                           state-store :local-storage
                           user-store :session-storage
                           redirect-uri-absolution false}}]]
  (if status
    {}
    {:db (-> db
             (assoc ::status :init)
             (dissoc ::callback
                     ::login-query-string))
     :fx [[::init-fx
           {:config (cond-> oidc-config
                      redirect-uri-absolution
                      u/absolve-redirect-uris)
            :state-store state-store
            :user-store user-store
            :on-user-loaded on-user-loaded
            :on-user-unloaded on-user-unloaded}]
          (case ?callback
            :login [::signin-redirect-callback-fx
                    {:query-string ?qstring
                     :on-success on-login-success
                     :on-failure on-login-failure}]
            :logout [::signout-redirect-callback-fx
                     {:on-success on-logout-success
                      :on-failure on-logout-failure}]
            [::get-user-fx
             ;; We need to set the user, if present, no matter what
             {:auto-login auto-login
              :on-success on-get-user-success
              :on-failure on-get-user-failure}])]}))

(re-frame/reg-event-fx
 ::init
 init)

;; Post-initialization

;; Get the UserManager for customization, if it is initialized
(re-frame/reg-cofx
 ::user-manager
 (fn [cofx _]
   (assoc cofx ::user-manager @user-manager)))

;; Trigger the login redirect from a user interaction
(re-frame/reg-event-fx
 ::login
 (fn [{:keys [db]} _]
   (if-not (= :loaded (::status db))
     {::signin-redirect-fx {}}
     {})))

;; Trigger the logout redirect from a user interaction
(re-frame/reg-event-fx
 ::logout
 (fn [{:keys [db]} _]
   (if (= :loaded (::status db))
     {::signout-redirect-fx {}}
     {})))

;; Subs
(re-frame/reg-sub
 ::status
 (fn [db _]
   (::status db)))

(re-frame/reg-sub
 ::user
 (fn [db _]
   (::user db)))

(re-frame/reg-sub
 ::user/profile
 :<- [::user]
 (fn [user _]
   (:profile user)))

(re-frame/reg-sub
 ::logged-in?
 (fn [_ _]
   (re-frame/subscribe [::status]))
 (fn [status _]
   (= :loaded
      status)))
