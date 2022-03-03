(ns com.yetanalytics.re-oidc.util-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [com.yetanalytics.re-oidc.util :refer [dispatch-cb
                                          cb-fn-or-dispatch
                                          js-error->clj
                                          expired?
                                          absolve-uri
                                          absolve-redirect-uris]]
   [re-frame.core :as re-frame]))

(deftest dispatch-cb-test
  (with-redefs [re-frame/dispatch identity]
    (testing "returns a callback that will call a partial dispatch vector"
      (is (= ((dispatch-cb [:some-dispatch :foo])
              :bar)
             [:some-dispatch :foo :bar])))))

(deftest cb-fn-or-dispatch-test
  (with-redefs [re-frame/dispatch identity]
    (testing "returns a callback that will call a partial dispatch vector"
      (is (= ((cb-fn-or-dispatch [:some-dispatch :foo])
              :bar)
             [:some-dispatch :foo :bar])))
    (testing "function callback passthru"
      (is (= ((cb-fn-or-dispatch
               (fn [ret]
                 (conj [:some-dispatch :foo] ret)))
              :bar)
             [:some-dispatch :foo :bar])))))

(deftest js-error->clj-test
  (testing "vanilla js errors"
    (is (= {:name "Error"
            :message "whoops!"
            :handler :some-handler}
           (js-error->clj
            :some-handler
            (try
              (throw (new js/Error "whoops!"))
              (catch js/Error error
                error))))))
  (testing "cool cljs ex-infos"
    (is (= {:name "Error"
            :message "whoops!"
            :handler :some-handler
            :ex-data {:type ::whoops}}
           (js-error->clj
            :some-handler
            (ex-info "whoops!"
                     {:type ::whoops}))))))

(deftest expired?-test
  (testing "epoch has passed"
    (is (expired? 0)))
  (testing "future is cool"
    (is
     (not
      (expired?
       (-> (new js/Date)
           .getTime
           (quot 1000)
           (+ 86400)))))))

(deftest absolve-uri-test
  (testing "Converts relative to absolute"
    (is
     (= "http://localhost:9500/foo"
        (absolve-uri "/foo")))))

(deftest absolve-redirect-uris-test
  (testing "Converts relative uri keys (kw or string) to absolute"
    (is
     (= {:redirect_uri "http://localhost:9500/foo"
         "redirect_uri" "http://localhost:9500/bar"
         :post_logout_redirect_uri "http://localhost:9500/baz"
         "post_logout_redirect_uri" "http://localhost:9500/quxx"
         :something_else "/fizz"}
        (absolve-redirect-uris
         {:redirect_uri "/foo"
          "redirect_uri" "/bar"
          :post_logout_redirect_uri "/baz"
          "post_logout_redirect_uri" "/quxx"
          :something_else "/fizz"})))))
