(ns lupapalvelu.document.yleiset-alueet-canonical
  (:require [lupapalvelu.core :refer [now]]
            [lupapalvelu.document.canonical-common :refer :all]
            [sade.util :as util]))

(defn- get-henkilo [henkilo]
  {:nimi {:etunimi (-> henkilo :henkilotiedot :etunimi :value)
          :sukunimi (-> henkilo :henkilotiedot :sukunimi :value)}
   :osoite {:osoitenimi {:teksti (-> henkilo :osoite :katu :value)}
            :postinumero (-> henkilo :osoite :postinumero :value)
            :postitoimipaikannimi (-> henkilo :osoite :postitoimipaikannimi :value)}
   :sahkopostiosoite (-> henkilo :yhteystiedot :email :value)
   :puhelin (-> henkilo :yhteystiedot :puhelin :value)
   :henkilotunnus (-> henkilo :henkilotiedot :hetu :value)})

(defn- get-henkilo-reduced [henkilo]
  (dissoc (get-henkilo henkilo) :osoite :henkilotunnus))

(defn- get-yritys [yritys]
  (merge
    {:nimi (-> yritys :yritysnimi :value)
     :liikeJaYhteisotunnus (-> yritys :liikeJaYhteisoTunnus :value)
     :postiosoitetieto {:Postiosoite {:osoitenimi {:teksti (-> yritys :osoite :katu :value)}
                                      :postinumero (-> yritys :osoite :postinumero :value)
                                      :postitoimipaikannimi (-> yritys :osoite :postitoimipaikannimi :value)}}}))

(defn- get-yritys-maksaja [yritys]
  (merge
    {:nimi (-> yritys :yritysnimi :value)
     :liikeJaYhteisotunnus (-> yritys :liikeJaYhteisoTunnus :value)
     :postiosoite {:osoitenimi {:teksti (-> yritys :osoite :katu :value)}
                   :postinumero (-> yritys :osoite :postinumero :value)
                   :postitoimipaikannimi (-> yritys :osoite :postitoimipaikannimi :value)}}))

(defn- get-hakija [hakija-doc]
  (merge
    (if (= (-> hakija-doc :_selected :value) "yritys")
      {:yritystieto  {:Yritys (get-yritys (:yritys hakija-doc))}
       :henkilotieto {:Henkilo (get-henkilo-reduced (-> hakija-doc :yritys :yhteyshenkilo))}}
      ;; If _selected == "henkilo", then yritys info is left out...
      {:henkilotieto {:Henkilo (get-henkilo (:henkilo hakija-doc))}})
    {:rooliKoodi "hakija"}))

(defn- get-vastuuhenkilo-osoitetieto [osoite]
  {:osoite {:osoitenimi {:teksti (-> osoite :katu :value)}
            :postinumero (-> osoite :postinumero :value)
            :postitoimipaikannimi (-> osoite :postitoimipaikannimi :value)}})

(defn- get-vastuuhenkilo [vastuuhenkilo type roolikoodi]
  (merge
    (if (= type :yritys)
      ;; yritys-tyyppinen vastuuhenkilo
      {:sukunimi (-> vastuuhenkilo :yritys :yhteyshenkilo :henkilotiedot :sukunimi :value)
       :etunimi (-> vastuuhenkilo :yritys :yhteyshenkilo :henkilotiedot :etunimi :value)
       :osoitetieto (get-vastuuhenkilo-osoitetieto (-> vastuuhenkilo :yritys :osoite))
       :puhelinnumero (-> vastuuhenkilo :yritys :yhteyshenkilo :yhteystiedot :puhelin :value)
       :sahkopostiosoite (-> vastuuhenkilo :yritys :yhteyshenkilo :yhteystiedot :email :value)}
      ;; henkilo-tyyppinen vastuuhenkilo
      {:sukunimi (-> vastuuhenkilo :henkilo :henkilotiedot :sukunimi :value)
       :etunimi (-> vastuuhenkilo :henkilo :henkilotiedot :etunimi :value)
       :osoitetieto (get-vastuuhenkilo-osoitetieto (-> vastuuhenkilo :henkilo :osoite))
       :puhelinnumero (-> vastuuhenkilo :henkilo :yhteystiedot :puhelin :value)
       :sahkopostiosoite (-> vastuuhenkilo :henkilo :yhteystiedot :email :value)})
    {:rooliKoodi roolikoodi}))

(defn- get-tyomaasta-vastaava [tyomaasta-vastaava]
  (if (= (-> tyomaasta-vastaava :_selected :value) "yritys")
    ;; yritys-tyyppinen tyomaasta-vastaava, siirretaan yritysosa omaksi osapuolekseen
    {:osapuolitieto {:Osapuoli {:yritystieto {:Yritys (get-yritys (:yritys tyomaasta-vastaava))}
                                :rooliKoodi "ty\u00f6nsuorittaja"}}
     :vastuuhenkilotieto {:Vastuuhenkilo (get-vastuuhenkilo
                                           tyomaasta-vastaava
                                           :yritys
                                           "lupaehdoista/ty\u00f6maasta vastaava henkil\u00f6")}}
    ;; henkilo-tyyppinen tyomaasta-vastaava
    {:vastuuhenkilotieto {:Vastuuhenkilo (get-vastuuhenkilo
                                           tyomaasta-vastaava
                                           :henkilo
                                           "lupaehdoista/ty\u00f6maasta vastaava henkil\u00f6")}}))

(defn- get-maksaja [maksaja-doc]
  (merge
    (if (= (-> maksaja-doc :_selected :value) "yritys")
      ;; yritys-tyyppinen maksaja, siirretaan yritysosa omaksi osapuolekseen
      {:vastuuhenkilotieto {:Vastuuhenkilo (get-vastuuhenkilo               ;; vastuuhenkilotieto
                                             maksaja-doc
                                             :yritys
                                             "maksajan vastuuhenkil\u00f6")}
       :yritystieto {:Yritys (get-yritys-maksaja (:yritys maksaja-doc))}}    ;; maksajatieto
      ;; henkilo-tyyppinen maksaja
      {:henkilotieto {:Henkilo (get-henkilo (:henkilo maksaja-doc))}})       ;; maksajatieto
    {:laskuviite (-> maksaja-doc :laskuviite :value)}))

(defn- get-handler [application]
  (if-let [handler (:authority application)]
    {:henkilotieto {:Henkilo {:nimi {:etunimi  (:firstName handler)
                                     :sukunimi (:lastName handler)}}}}
    empty-tag))

(defn- get-kasittelytieto [application]
  {:Kasittelytieto {:muutosHetki (to-xml-datetime (:modified application))
                    :hakemuksenTila ((keyword (:state application)) application-state-to-krysp-state)
                    :asiatunnus (:id application)
                    :paivaysPvm (to-xml-date ((state-timestamps (keyword (:state application))) application))
                    :kasittelija (get-handler application)}})

(defn- get-sijaintitieto [application]
  {:Sijainti {:osoite {:yksilointitieto (:id application)
                       :alkuHetki (to-xml-datetime (now))
                       :osoitenimi {:teksti (:address application)}}
              :piste {:Point {:pos (str (:x (:location application)) " " (:y (:location application)))}}}})

(defn- get-lisatietoja-sijoituskohteesta [data]
  {:selitysteksti "Lis\u00e4tietoja sijoituskohteesta"
   :arvo (-> data :lisatietoja-sijoituskohteesta :value)})

(defn- get-sijoituksen-tarkoitus [data]
  {:selitysteksti "Sijoituksen tarkoitus"
   :arvo (if (= "other" (:sijoituksen-tarkoitus data))
           (-> data :muu-sijoituksen-tarkoitus :value)
           (-> data :sijoituksen-tarkoitus :value))})

(defn- get-mainostus-alku-loppu-hetki [mainostus-viitoitus-tapahtuma]
  {:Toimintajakso {:alkuHetki (to-xml-datetime-from-string (-> mainostus-viitoitus-tapahtuma :mainostus-alkaa-pvm :value))
                   :loppuHetki (to-xml-datetime-from-string (-> mainostus-viitoitus-tapahtuma :mainostus-paattyy-pvm :value))}})

(defn- get-mainostus-viitoitus-lisatiedot [mainostus-viitoitus-tapahtuma]
  (let [lisatiedot [{:LupakohtainenLisatieto {:selitysteksti "Tapahtuman nimi"
                                             :arvo (-> mainostus-viitoitus-tapahtuma :tapahtuman-nimi :value)}}
                   {:LupakohtainenLisatieto {:selitysteksti "Tapahtumapaikka"
                                             :arvo (-> mainostus-viitoitus-tapahtuma :tapahtumapaikka :value)}}]]
    (if (-> mainostus-viitoitus-tapahtuma :haetaan-kausilupaa)
      (conj lisatiedot
        {:LupakohtainenLisatieto {:selitysteksti "Haetaan kausilupaa"
                                  :arvo (-> mainostus-viitoitus-tapahtuma :haetaan-kausilupaa :value)}})
      lisatiedot)))

;;
;; TODO: Mihin kielitieto (lang) lisataan? Rakval-puolella on lisatiedoissa asioimiskieli.
;;
(defn- permits [application lang]
  (let [documents-by-type (by-type (:documents application))]
;    (println "\n documents-by-type: \n")
;    (clojure.pprint/pprint documents-by-type)
;    (println "\n application: ")
;    (clojure.pprint/pprint application)
;    (println "\n")

    ;; Sijoituslupa: Maksaja, alkuPvm and loppuPvm are not filled in the application, but are requested by schema
    ;;               -> maksaja gets hakija's henkilotieto, alkuPvm/loppuPvm both get application's "modified" date.

    (let [operation-name-key (-> application :operations first :name keyword)
          lupa-name-key (operation-name-key ya-operation-type-to-schema-name-key)
          hakija (get-hakija (-> documents-by-type :hakija-ya first :data))
          tyoaika-doc (if-not (or
                                (= lupa-name-key :Sijoituslupa)
                                (= operation-name-key :ya-kayttolupa-mainostus-ja-viitoitus))
                        (-> documents-by-type :tyoaika first :data)
                        {})
          mainostus-viitoitus-tapahtuma-doc (if (= operation-name-key :ya-kayttolupa-mainostus-ja-viitoitus)
                                              (-> documents-by-type :mainosten-tai-viitoitusten-sijoittaminen first :data)
                                              {})
          mainostus-viitoitus-tapahtuma (mainostus-viitoitus-tapahtuma-doc (keyword (-> mainostus-viitoitus-tapahtuma-doc :_selected :value)))
          alku-pvm (if (= lupa-name-key :Sijoituslupa)
                    (to-xml-date (:modified application))
                    (if (= operation-name-key :ya-kayttolupa-mainostus-ja-viitoitus)
                      (to-xml-date-from-string (-> mainostus-viitoitus-tapahtuma :tapahtuma-aika-alkaa-pvm :value))
                      (to-xml-date-from-string (-> tyoaika-doc :tyoaika-alkaa-pvm :value))))
          loppu-pvm (if (= lupa-name-key :Sijoituslupa)
                    (to-xml-date (:modified application))
                    (if (= operation-name-key :ya-kayttolupa-mainostus-ja-viitoitus)
                      (to-xml-date-from-string (-> mainostus-viitoitus-tapahtuma :tapahtuma-aika-paattyy-pvm :value))
                      (to-xml-date-from-string (-> tyoaika-doc :tyoaika-paattyy-pvm :value))))
          maksaja (if-not (= lupa-name-key :Sijoituslupa)
                    (get-maksaja (-> documents-by-type :yleiset-alueet-maksaja first :data))
                    {:henkilotieto (:henkilotieto hakija)
                     :laskuviite "0000000000"})
          hankkeen-kuvaus-key (if (= lupa-name-key :Sijoituslupa)
                                :yleiset-alueet-hankkeen-kuvaus-sijoituslupa
                                :yleiset-alueet-hankkeen-kuvaus-kaivulupa)
          hankkeen-kuvaus (if (= operation-name-key :ya-kayttolupa-mainostus-ja-viitoitus)
                            {}
                            (-> documents-by-type hankkeen-kuvaus-key first :data))
          tyomaasta-vastaava (if (= lupa-name-key :Tyolupa)
                               (get-tyomaasta-vastaava (-> documents-by-type :tyomaastaVastaava first :data))
                               {})
          sijoituslupaviitetieto-key (if (= lupa-name-key :Sijoituslupa)
                                       :kaivuLuvanTunniste
                                       :sijoitusLuvanTunniste)
          body {lupa-name-key {:kasittelytietotieto (get-kasittelytieto application)
                               :alkuPvm alku-pvm
                               :loppuPvm loppu-pvm
                               :sijaintitieto (get-sijaintitieto application)
                               ;; If tyomaasta-vastaava does not have :osapuolitieto, we filter the resulting nil out.
                               :osapuolitieto (into [] (filter :Osapuoli [{:Osapuoli hakija}
                                                                          (:osapuolitieto tyomaasta-vastaava)]))
                               ;; If tyomaasta-vastaava does not have :vastuuhenkilotieto, we filter the resulting nil out.
                               :vastuuhenkilotieto (into [] (filter :Vastuuhenkilo [(:vastuuhenkilotieto tyomaasta-vastaava)
                                                                                    (:vastuuhenkilotieto maksaja)]))
                               :maksajatieto {:Maksaja (dissoc maksaja :vastuuhenkilotieto)}
                               :lausuntotieto (get-statements (:statements application))
                               :lupaAsianKuvaus (-> hankkeen-kuvaus :kayttotarkoitus :value)
                               ;;   TODO: Onko tama oikea paikka sijoitusluvassa olevalle kaivuLuvanTunnisteelle?
                               :sijoituslupaviitetieto {:Sijoituslupaviite {:vaadittuKytkin false  ;; TODO: Muuta trueksi?
                                                                            :tunniste (-> hankkeen-kuvaus sijoituslupaviitetieto-key :value)}}
                               :kayttotarkoitus (operation-name-key ya-operation-type-to-usage-description)}}]

      (condp = lupa-name-key
        :Tyolupa (assoc-in body [lupa-name-key :johtoselvitysviitetieto]
                   {:Johtoselvitysviite {:vaadittuKytkin false ;; TODO: Muuta trueksi?
                                         ;:tunniste "..."      ;; TODO: Tarvitaanko tunnistetta?
                                         }})
        :Kayttolupa (if (= operation-name-key :ya-kayttolupa-mainostus-ja-viitoitus)
                      ;; Mainostus/viitoituslupa
                      (util/dissoc-in
                        (util/dissoc-in

                          (assoc-in
                            ;; Mainostuksen alku-/loppuHetki
                            (if (= "mainostus-tapahtuma-valinta" (-> mainostus-viitoitus-tapahtuma-doc :_selected :value))
                              (assoc-in body [lupa-name-key :toimintajaksotieto]
                                (get-mainostus-alku-loppu-hetki mainostus-viitoitus-tapahtuma))
                              body)
                            [lupa-name-key :lupakohtainenLisatietotieto]
                            (get-mainostus-viitoitus-lisatiedot mainostus-viitoitus-tapahtuma))

                          [lupa-name-key :lupaAsianKuvaus])
                        [lupa-name-key :sijoituslupaviitetieto])
                      ;; Muu kayttolupa
                      body)
        :Sijoituslupa (util/dissoc-in
                        (assoc-in body [lupa-name-key :lupakohtainenLisatietotieto]
                          (let [sijoituksen-tarkoitus-doc (-> documents-by-type :sijoituslupa-sijoituksen-tarkoitus first :data)]
                            [{:LupakohtainenLisatieto (get-sijoituksen-tarkoitus sijoituksen-tarkoitus-doc)}
                             {:LupakohtainenLisatieto (get-lisatietoja-sijoituskohteesta sijoituksen-tarkoitus-doc)}]))
                        [lupa-name-key :vastuuhenkilotieto])))))

(defn application-to-canonical
  "Transforms application mongodb-document to canonical model."
  [application lang]
  (let [app (assoc application :documents
              (clojure.walk/postwalk empty-strings-to-nil (:documents application)))]
    {:YleisetAlueet {:toimituksenTiedot
                     {:aineistonnimi (:title app)
                      :aineistotoimittaja "lupapiste@solita.fi"
                      :tila toimituksenTiedot-tila
                      :toimitusPvm (to-xml-date (now))
                      :kuntakoodi (:municipality app)
                      :kielitieto ""}
                     :yleinenAlueAsiatieto (permits app lang)}}))

