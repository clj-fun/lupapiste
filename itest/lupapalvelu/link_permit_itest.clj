(ns lupapalvelu.link-permit-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]))

(fact* "Link permit creation and removal"
  (apply-remote-minimal)

  (let [apikey                 sonja
        municipality           sonja-muni

        ;; App 1 - with same permit type, approved
        approved-application      (create-and-submit-application apikey
                                    :municipality municipality
                                    :address "Paatoskuja 13"
                                    :operation "ya-katulupa-vesi-ja-viemarityot") => truthy
        approved-application-id   (:id approved-application)
        _                         (generate-documents approved-application apikey)
        _                         (command apikey :approve-application :id approved-application-id :lang "fi") => ok?

        ;; App 2 - with same permit type, verdict given
        verdict-given-application (create-and-submit-application apikey
                                    :municipality municipality
                                    :address "Paatoskuja 14"
                                    :operation "ya-katulupa-vesi-ja-viemarityot") => truthy
        verdict-given-application-id (:id verdict-given-application)
        _                         (generate-documents verdict-given-application apikey)
        _                         (command apikey :approve-application :id verdict-given-application-id :lang "fi") => ok?
        _                         (command apikey :give-verdict
                                    :id verdict-given-application-id
                                    :verdictId "aaa" :status 42 :name "Paatoksen antaja"
                                    :given 123 :official 124) => ok?

        ;; App 3 - with same permit type, verdict given, of operation "ya-jatkoaika"
        create-jatkoaika-resp     (command apikey :create-continuation-period-permit :id verdict-given-application-id) => ok?
        jatkoaika-application-id  (:id create-jatkoaika-resp)
        jatkoaika-application     (query-application apikey jatkoaika-application-id) => truthy
        _                         (command apikey :submit-application :id jatkoaika-application-id) => ok?
        _                         (generate-documents jatkoaika-application apikey)
        _                         (command apikey :approve-application :id jatkoaika-application-id :lang "fi") => ok?
        jatkoaika-application     (query-application apikey jatkoaika-application-id) => truthy
        _                         (:state jatkoaika-application) => "closed"

        ;; App that gets a link permit attached to it
        test-application          (create-and-submit-application apikey
                                    :municipality municipality
                                    :address "Paatoskuja 15"
                                    :operation "ya-katulupa-vesi-ja-viemarityot") => truthy
        test-application-id       (:id test-application)

        ;; The "matches", i.e. the contents of the dropdown selection component in the link-permit dialog
        ;; is allowed to have only these kind of applications:
        ;;    - different id than current application has, but same permit-type
        ;;    - not in draft state
        ;;    - whose operation is not of type "ya-jatkoaika"
        ;;
        matches-resp (query apikey :app-matches-for-link-permits :id test-application-id) => ok?
        matches (:app-links matches-resp)]

    (count matches) => 2
    (-> matches first :id) => approved-application-id
    (-> matches second :id) => verdict-given-application-id

    (fact "Can not insert invalid key"
      (command apikey :add-link-permit :id test-application-id :linkPermitId "foo.bar") => fail?
      (command apikey :add-link-permit :id test-application-id :linkPermitId " ") => fail?)

    (command apikey :add-link-permit :id test-application-id :linkPermitId verdict-given-application-id) => ok?
    (generate-documents test-application apikey)
    (command apikey :approve-application :id test-application-id :lang "fi") => ok?
    (command apikey :add-link-permit
      :id test-application-id
      :linkPermitId verdict-given-application-id) => (partial expected-failure? "error.command-illegal-state")

    (fact "Authority gives verdict and adds link permit"
      (command sonja :add-link-permit :id verdict-given-application-id :linkPermitId approved-application-id) => ok?)

    (let [app (query-application apikey test-application-id) => truthy]
      (-> app first :appsLinkingToUs) => nil?
      (count (:linkPermitData app)) => 1
      (let [link-permit-data (-> app :linkPermitData first)]
        (:id link-permit-data)        => verdict-given-application-id
        (:type link-permit-data)      => "lupapistetunnus"
        (:operation link-permit-data) => "ya-katulupa-vesi-ja-viemarityot"))))
