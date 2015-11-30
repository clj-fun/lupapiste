(ns lupapalvelu.attachment-accessibility-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.attachment-accessibility :refer :all]))

(facts "facts about accessing attachment(s)"
  (let [user1 {:id "1" :role "applicant"}
        user2 {:id "2" :role "applicant"}
        user-authority {:id "2" :role "authority"}

        att0-empty {}
        att1-no-meta {:latestVersion {:fileId "322" :user {:id "1"}}
                      :versions [{:fileId "321" :user {:id "1"}}
                                 {:fileId "322" :user {:id "1"}}]}
        att-authority-no-auth {:metadata {:nakyvyys "viranomainen"}
                               :latestVersion   {:fileId "322" :user {:id "1"}}
                               :versions        [{:fileId "321" :user {:id "1"}}
                                                 {:fileId "322" :user {:id "1"}}]}
        att-authority-auth-u1 {:metadata {:nakyvyys "viranomainen"}
                               :auth [user1]
                               :latestVersion   {:fileId "322" :user {:id "1"}}
                               :versions        [{:fileId "321" :user {:id "1"}}
                                                 {:fileId "322" :user {:id "1"}}]}
        att-parties-no-auth {:metadata {:nakyvyys "asiakas-ja-viranomainen"}
                             :latestVersion   {:fileId "322" :user {:id "1"}}
                             :versions        [{:fileId "321" :user {:id "1"}}
                                               {:fileId "322" :user {:id "1"}}]}
        att-parties-auth-u1 {:metadata {:nakyvyys "asiakas-ja-viranomainen"}
                             :auth [user1]
                             :latestVersion   {:fileId "322" :user {:id "1"}}
                             :versions        [{:fileId "321" :user {:id "1"}}
                                               {:fileId "322" :user {:id "1"}}]}
        att-public {:metadata {:nakyvyys "julkinen"}
                    :latestVersion   {:fileId "322" :user {:id "1"}}
                    :versions        [{:fileId "321" :user {:id "1"}}
                                      {:fileId "322" :user {:id "1"}}]}]
    (fact "nils und nulls"
      (can-access-attachment? nil nil nil) => (throws AssertionError)
      (can-access-attachment? nil nil att1-no-meta) => false) ; anonymouys can't access, check this

     (fact "empty attachment can be accessed by anyone, to upload versions"
       (can-access-attachment? user1 nil att0-empty) => true
       (can-access-attachment? user-authority nil att0-empty) => true)
     (fact "if no metadata or auth, attachment is regarded as public"
       (can-access-attachment? user1 nil att1-no-meta) => true)

     (facts "only authority attachment visibility ('viranomainen')"
       (fact "authority can access"
         (can-access-attachment? user-authority nil att-authority-no-auth) => true)
       (fact "can't access if user isn't authed to application"
         (can-access-attachment? user1 nil att-authority-no-auth) => false)
       (fact "can access if user has auth in application, but auth is not defined in attachment"
         (can-access-attachment? user1 [user1] att-authority-no-auth) => true)

       (fact "can access when user is authed to attachment"
         (can-access-attachment? user1 nil att-authority-auth-u1) => true)
       (fact "can access when user is authed to both attachment and application"
         (can-access-attachment? user1 [user1] att-authority-auth-u1) => true)
       (fact "user2 not authed"
         (can-access-attachment? user2 nil att-authority-auth-u1) => false)
       (fact "application auth for user2 is not enough"
         (can-access-attachment? user2 [user2] att-authority-auth-u1) => false)
       (fact "authority can access"
        (can-access-attachment? user-authority nil att-authority-auth-u1) => true))

     (facts "authed and authority attachment visibility ('asiakas-ja-viranomainen')"
       (fact "can't access if user isn't authed to application"
         (can-access-attachment? user2 [user1] att-parties-no-auth) => false)
       (fact "is available when attachment doesn't have parties (no attachment auth -> true)"
         (can-access-attachment? user2 [user2] att-parties-no-auth) => true)
       (fact "is only available for user authed in application, and authorities"
         (can-access-attachment? user2 [user2] att-parties-auth-u1) => true
         (can-access-attachment? user2 [user1] att-parties-auth-u1) => false
         (can-access-attachment? user2 [user1 user2] att-parties-auth-u1) => true
         (can-access-attachment? user-authority nil att-parties-auth-u1) => true))

     (facts "public"
       (can-access-attachment? nil nil att-public) => false ; anon can't access ?
       (can-access-attachment? user1 [user2] att-public) => true
       (can-access-attachment? user1 [user2 user1] att-public) => true
       (can-access-attachment? user-authority nil att-public) => true)))
