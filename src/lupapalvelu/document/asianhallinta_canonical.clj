(ns lupapalvelu.document.asianhallinta_canonical
  (require [lupapalvelu.document.canonical-common :refer :all]
           [lupapalvelu.document.tools :as tools]
           [clojure.string :as s]
           [sade.util :as util]))


;; UusiAsia, functions prefixed with ua-

(def uusi-asia {:UusiAsia
                {:Tyyppi nil
                 :Kuvaus nil
                 :Kuntanumero nil
                 :Hakijat nil
                 :Maksaja nil
                 :HakemusTunnus nil
                 :VireilletuloPvm nil
                 :Liitteet nil
                 :Asiointikieli nil
                 :Toimenpiteet nil
                 :Viiteluvat {:Viitelupa nil}}})

(def ^:private ua-root-element {:UusiAsia nil})

(defn- ua-get-asian-tyyppi-string [application]
  ; KasiteltavaHakemus, TODO later: Tiedoksianto
  "KasiteltavaHakemus")

(defn- ua-get-yhteystiedot [data]
  (util/strip-nils
    {:Jakeluosoite (get-in data [:osoite :katu])
     :Postinumero (get-in data [:osoite :postinumero])
     :Postitoimipaikka (get-in data [:osoite :postitoimipaikannimi])
     :Maa "Suomi" ; TODO voiko olla muu?
     :Email (get-in data [:yhteystiedot :email])
     :Puhelin (get-in data [:yhteystiedot :puhelin])}))

(defn- ua-get-henkilo [data]
  {:Etunimi (get-in data [:henkilo :henkilotiedot :etunimi])
   :Sukunimi (get-in data [:henkilo :henkilotiedot :sukunimi])
   :Yhteystiedot (ua-get-yhteystiedot (:henkilo data))
   :Henkilotunnus (get-in data [:henkilo :henkilotiedot :hetu])
   :VainSahkoinenAsiointi nil ; TODO tarviiko tätä ja Turvakieltoa?
   :Turvakielto nil})

(defn- ua-get-yritys [data]
  {:Nimi (get-in data [:yritys :yritysnimi])
   :Ytunnus (get-in data [:yritys :liikeJaYhteisoTunnus])
   :Yhteystiedot (ua-get-yhteystiedot (:yritys data))})

(defn- ua-get-hakijat [documents]
  (for [hakija documents
        :let [hakija (:data hakija)]]
    (merge {:Hakija ; TODO yritys
            {:Henkilo (ua-get-henkilo hakija)}})))

(defn- ua-get-maksaja [document]
  (let [sel (get-in document [:_selected])
        maksaja-map {:Laskuviite (get-in document [:laskuviite])
                     :Verkkolaskutustieto (when (= "yritys" sel)
                                            {:OVT-tunnus (get-in document [:yritys :verkkolaskutustieto :ovtTunnus])
                                             :Verkkolaskutunnus (get-in document [:yritys :verkkolaskutustieto :verkkolaskuTunnus])
                                             :Operaattoritunnus (get-in document [:yritys :verkkolaskutustieto :valittajaTunnus])})}]

    (condp = sel
       "yritys" (assoc-in maksaja-map [:Yritys] nil)
       "henkilo" (assoc-in maksaja-map [:Henkilo] (ua-get-henkilo document)))))

;; TaydennysAsiaan, prefix: ta-


;; AsianPaatos, prefix: ap-


;; AsianTunnusVastaus, prefix: atr-


(defn application-to-asianhallinta-canonical [application lang]
  (let [application (tools/unwrapped application)
        documents (documents-by-type-without-blanks application)]
    (-> (assoc-in ua-root-element [:UusiAsia :Tyyppi] (ua-get-asian-tyyppi-string application))
      (assoc-in [:UusiAsia :Kuvaus] (:title application))
      (assoc-in [:UusiAsia :Kuntanumero] (:municipality application))
      (assoc-in [:UusiAsia :Hakijat] (ua-get-hakijat (:hakija documents)))
      (assoc-in [:UusiAsia :Maksaja] (ua-get-maksaja (:data (first (:maksaja documents))))))))
