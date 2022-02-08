(ns com.yetanalytics.re-oidc-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [com.yetanalytics.re-oidc
    :as re-oidc
    :refer [add-error
            user-loaded
            user-unloaded
            login-callback
            logout-callback
            init]]))

(deftest add-error-test
  (testing "Adds re-oidc errors to the DB"
    (is (= {:errors [{:name "Error",
                      :message "whoops!",
                      :handler :some-handler,
                      :ex-data {:type :com.yetanalytics.re-oidc-test/whoops}}]}
           (add-error
            {}
            [nil
             :some-handler
             (ex-info "whoops!"
                      {:type ::whoops})])))))

(deftest user-loaded-test
  (testing "Loads the user from JS"
    (is (= {::re-oidc/status :loaded,
            ::re-oidc/user {:refresh-token "",
                            :expires-at 0,
                            :state "",
                            :scope "",
                            :id-token "",
                            :access-token "",
                            :token-type "Bearer",
                            :session-state "",
                            :profile {"sub" ""}}}
           (user-loaded {}
                        [nil
                         #js {:id_token ""
                              :access_token ""
                              :expires_at 0
                              :refresh_token ""
                              :token_type "Bearer"
                              :state ""
                              :session_state ""
                              :scope ""
                              :profile {"sub" ""}}])))))

(deftest user-unloaded-test
  (testing "unloads user and sets status"
    (is (= {::re-oidc/status :unloaded}
           (user-unloaded
            {::re-oidc/status :loaded,
             ::re-oidc/user {:refresh-token "",
                             :expires-at 0,
                             :state "",
                             :scope "",
                             :id-token "",
                             :access-token "",
                             :token-type "Bearer",
                             :session-state "",
                             :profile {"sub" ""}}}
            nil)))))

(deftest login-callback-test
  (testing "before initialization, init callbacks get used"
    (is (= {:db
            {::re-oidc/callback :login,
             ::re-oidc/login-query-string "?foo=bar"}}
           (login-callback
            {}
            [nil
             {:on-login-success [::success]
              :on-login-failure [::failure]}
             "?foo=bar"]))))
  (testing "after initialization, arg callbacks get used"
    (is (= {:db {::re-oidc/status :init},
            :fx [[::re-oidc/signin-redirect-callback-fx
                  {:query-string "?foo=bar",
                   :on-success [::success],
                   :on-failure [::failure]}]]}
           (login-callback
            {:db {::re-oidc/status :init}}
            [nil
             {:on-login-success [::success]
              :on-login-failure [::failure]}
             "?foo=bar"]))))
  (testing "invalid status, nothing happens"
    (is (= {}
           (login-callback
            {:db {::re-oidc/status :loaded}}
            [nil
             {:on-login-success [::success]
              :on-login-failure [::failure]}
             "?foo=bar"])))))

(deftest logout-callback-test
  (testing "before initialization, init callbacks get used"
    (is (= {:db
            {::re-oidc/callback :logout}}
           (logout-callback
            {}
            [nil
             {:on-logout-success [::success]
              :on-logout-failure [::failure]}]))))
  (testing "after initialization, arg callbacks get used"
    (is (= {:db {::re-oidc/status :init},
            :fx [[::re-oidc/signout-redirect-callback-fx
                  {:on-success [::success],
                   :on-failure [::failure]}]]}
           (logout-callback
            {:db {::re-oidc/status :init}}
            [nil
             {:on-logout-success [::success]
              :on-logout-failure [::failure]}]))))
  (testing "invalid status, nothing happens"
    (is (= {}
           (logout-callback
            {:db {::re-oidc/status :loaded}}
            [nil
             {:on-login-success [::success]
              :on-login-failure [::failure]}])))))

(deftest init-test
  (testing "idempotent"
    (is (= {}
           (init
            {:db {::re-oidc/status :init}}
            [nil nil]))))
  (testing "with login callback"
    (is (= {:db {::re-oidc/status :init},
            :fx [[::re-oidc/init-fx
                  {:config {}
                   :state-store :local-storage
                   :user-store :session-storage}]
                 [::re-oidc/signin-redirect-callback-fx
                  {:query-string "?foo=bar",
                   :on-success [::success],
                   :on-failure [::failure]}]]}
           (init
            {:db {::re-oidc/callback :login
                  ::re-oidc/login-query-string "?foo=bar"}}
            [nil
             {:oidc-config {}
              :on-login-success [::success]
              :on-login-failure [::failure]}]))))
  (testing "with logout callback"
    (is (= {:db {::re-oidc/status :init},
            :fx [[::re-oidc/init-fx
                  {:config {}
                   :state-store :local-storage
                   :user-store :session-storage}]
                 [::re-oidc/signout-redirect-callback-fx
                  {:on-success [::success],
                   :on-failure [::failure]}]]}
           (init
            {:db {::re-oidc/callback :logout}}
            [nil
             {:oidc-config {}
              :on-logout-success [::success]
              :on-logout-failure [::failure]}]))))
  (testing "with no callback"
    (is (= {:db {::re-oidc/status :init},
            :fx [[::re-oidc/init-fx
                  {:config {}
                   :state-store :local-storage
                   :user-store :session-storage}]
                 [::re-oidc/get-user-fx
                  {:auto-login false,
                   :on-success [::success],
                   :on-failure [::failure]}]]}
           (init
            {:db {}}
            [nil
             {:oidc-config {}
              :on-get-user-success [::success]
              :on-get-user-failure [::failure]}])))))
