(ns lupapalvelu.xml.krysp.maa-aines-canonical-to-krysp-xml-test
  (:require [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :refer :all :as mapping-to-krysp]
            [lupapalvelu.document.maa-aines-canonical :refer [maa-aines-canonical]]
            [lupapalvelu.document.maa-aines-canonical-test :refer [application]]
            [lupapalvelu.xml.krysp.maa-aines-mapping :refer [maa-aines_to_krysp]]
            [lupapalvelu.xml.krysp.validator :as validator]
            [lupapalvelu.xml.emit :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.data.xml :refer :all]
            [sade.xml :as xml]
            [sade.common-reader :as cr]
            ))

(facts "Maa-aineslupa type of permit to canonical and then to xml with schema validation"

  (let [canonical (maa-aines-canonical application "fi")
        krysp-xml (element-to-xml canonical maa-aines_to_krysp)
        xml-s     (indent-str krysp-xml)
        lp-xml    (cr/strip-xml-namespaces (xml/parse xml-s))]

    ;(println xml-s)

    (validator/validate xml-s (:permitType application) "2.1.1") ; throws exception

    (fact "kuvaus in koontiKentta element"
      (xml/get-text lp-xml [:koontiKentta]) => "Hankkeen synopsis")

    (let [hakija (xml/select1 lp-xml [:hakija])
          maksaja (xml/select1 lp-xml [:viranomaismaksujenSuorittaja])]
      (xml/get-text hakija [:puhelin]) => "060222155"
      (xml/get-text maksaja [:puhelin]) => "121212"
      (xml/get-text maksaja [:henkilotunnus]) => "210281-9988")


    (let [sijainti (xml/select1 lp-xml [:sijaintitieto :Sijainti])
          osoite (xml/select1 sijainti :osoite)]

        (xml/get-text osoite [:osoitenimi :teksti]) => "Londb\u00f6lentie 97"
        (xml/get-text sijainti [:yksilointitieto]) => (:propertyId application)
        (xml/get-text sijainti [:piste :Point :pos]) =>  "428195.77099609 6686701.3931274")

    ))
