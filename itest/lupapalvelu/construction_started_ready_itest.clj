(ns lupapalvelu.construction-started-ready-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]))

(fact* "Application can be set to Started state after verdict has been given, and after that to Closed state."
  (let [application    (create-and-submit-application pena
                         :operation "ya-katulupa-vesi-ja-viemarityot"
                         :municipality sonja-muni
                         :address "Paatoskuja 11") => truthy
        application-id (:id application)
        _              (generate-documents application sonja)
        _              (command sonja :approve-application :id application-id :lang "fi") => ok?
        _              (command sonja :inform-construction-started :id application-id :startedTimestampStr "31.12.2013") => (partial expected-failure? "error.command-illegal-state")
        _              (command sonja :give-verdict :id application-id :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?
        application    (query-application sonja application-id) => truthy]

    (:state application) => "verdictGiven"
    sonja => (allowed? :create-continuation-period-permit :id application-id)
    (command sonja :inform-construction-ready :id application-id :readyTimestampStr "31.12.2013" :lang "fi") => (partial expected-failure? "error.command-illegal-state")
    (command sonja :inform-construction-started :id application-id :startedTimestampStr "31.12.2013") => ok?

    ;; Started application
    (let [application (query-application sonja application-id) => truthy
          email       (last-email) => truthy]
      (:state application) => "constructionStarted"
      (:to email) => (email-for-key pena)
      (:subject email) => "Lupapiste.fi: Paatoskuja 11 - hakemuksen tila muuttunut"
      (get-in email [:body :plain]) => (contains "Rakennusty\u00f6t aloitettu")
      email => (partial contains-application-link? application-id)

      (command sonja :inform-construction-ready :id application-id :readyTimestampStr "31.12.2013" :lang "fi") => ok?

      ;; Closed application
      (let [application (query-application sonja application-id) => truthy
            email       (last-email) => truthy]
        (:state application) => "closed"
        sonja =not=> (allowed? :inform-construction-started :id application-id)
        sonja =not=> (allowed? :create-continuation-period-permit :id application-id)

        (:to email) => (email-for-key pena)
        (:subject email) => "Lupapiste.fi: Paatoskuja 11 - hakemuksen tila muuttunut"
        (get-in email [:body :plain]) => (contains "Valmistunut")
        email => (partial contains-application-link? application-id)))))

(fact* "Application cannot be set to Started state if it is not an YA type of application."
  (let [application    (create-and-submit-application pena :municipality sonja-muni :address "Paatoskuja 11") => truthy
        application-id (:id application)
        _              (command sonja :approve-application :id application-id :lang "fi") => ok?
        _              (command sonja :give-verdict :id application-id :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?
        application    (query-application sonja application-id) => truthy
        _              (:state application) => "verdictGiven"]
    (command sonja :inform-construction-started :id application-id :startedTimestampStr "31.12.2013") => (partial expected-failure? "error.invalid-permit-type")))
