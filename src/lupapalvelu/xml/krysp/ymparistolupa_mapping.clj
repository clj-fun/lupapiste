(ns lupapalvelu.xml.krysp.ymparistolupa-mapping
  (:require [clojure.walk :as walk]
            [sade.util :as util]
            [lupapalvelu.core :refer [now]]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.document.ymparistolupa-canonical :as ymparistolupa-canonical]
            [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
            [lupapalvelu.xml.emit :refer [element-to-xml]]))

(def toiminta-aika-children [{:tag :alkuHetki} ; time
                             {:tag :loppuHetki} ; time
                             {:tag :vuorokausivaihtelu}]) ; string

(def tuotanto-children [{:tag :yhteistuotanto} ; string
                        {:tag :erillistuotanto}]) ; string

(def Varastointipaikka {:tag :Varastointipaikka
                        :child [mapping-common/yksilointitieto
                                mapping-common/alkuHetki
                                (mapping-common/sijaintitieto "yht")
                                {:tag :kuvaus}]}); string

(def ymparistolupaType
  [{:tag :kasittelytietotieto :child [{:tag :Kasittelytieto :child mapping-common/ymp-kasittelytieto-children}]}
   {:tag :luvanTunnistetiedot :child [mapping-common/lupatunnus]}
   ; 0-n {:tag :valvontatapahtumattieto :child []}
   {:tag :lausuntotieto :child [mapping-common/lausunto_213]}
   {:tag :maksajatieto :child mapping-common/maksajatype-children_213}
   {:tag :hakija :child mapping-common/yhteystietotype-children_213}
   {:tag :toiminta
    :child [{:tag :peruste} ; string
            {:tag :kuvaus} ;string
            {:tag :luvanHakemisenSyy}]} ;optional string
   {:tag :laitoksentiedot :child [{:tag :Laitos :child [;{:tag :yht:metatieto}
                                                        mapping-common/yksilointitieto
                                                        mapping-common/alkuHetki
                                                        {:tag :laitoksenNimi}
                                                        {:tag :osoite :child mapping-common/postiosoite-children-ns-yht}
                                                        (mapping-common/sijaintitieto "yht")
                                                        {:tag :toimialatunnus}
                                                        {:tag :toimiala}
                                                        {:tag :yhteyshenkilo :child mapping-common/henkilo-child-ns-yht}
                                                        {:tag :toimintaAika :child [{:tag :aloitusPvm} {:tag :lopetusPvm}]}
                                                        {:tag :tyontekijamaara}
                                                        {:tag :henkilotyovuodet}
                                                        {:tag :kiinttun}
                                                        {:tag :rakennustunnustieto, :child [{:tag :kiinttun :ns "yht"} {:tag :rakennusnro :ns "yht"}]}]}]}
   {:tag :voimassaOlevatLuvat
    :child [{:tag :luvat :child [{:tag :lupa :child [{:tag :tunnistetieto} ; string
                                                     {:tag :kuvaus} ; string
                                                     {:tag :liite :child mapping-common/liite-children_213}]}]}
            {:tag :vakuutukset :child [{:tag :vakuutus :child [{:tag :vakuutusyhtio} ; string
                                                               {:tag :vakuutusnumero}]}]}]} ; string
;   {:tag :alueJaYmparisto :child [{:tag :kiinteistonLaitokset :child [{:tag :kiinteistorekisteritunnus} ; string
;                                                                      {:tag :laitos}]}]} ; string
    {:tag :toiminnanSijaintitieto :child [{:tag :ToiminnanSijainti :child [mapping-common/yksilointitieto
                                                                           mapping-common/alkuHetki
                                                                           (mapping-common/sijaintitieto "yht")
                                                                           ;{:tag :ymparistoolosuhteet :child mapping-common/liite-children_213}
                                                                           ;{:tag :ymparistonLaatu :child mapping-common/liite-children_213}
                                                                           ;{:tag :asutus :child mapping-common/liite-children_213}
                                                                           ;{:tag :kaavoitustilanne :child mapping-common/liite-children_213}
                                                                           ;{:tag :rajanaapurit :child [{:tag :luettelo :child mapping-common/liite-children_213}]}
                                                                           ]
                                               }]}

   {:tag :referenssiPiste :child [mapping-common/gml-point]}
   {:tag :koontiKentta} ; String
   {:tag :liitetieto :child [{:tag :Liite :child mapping-common/liite-children_213}]}
   {:tag :asianKuvaus}])

(def ymparistolupa_to_krysp
  {:tag :Ymparistoluvat
   :ns "ymy"
   :attr (merge {:xsi:schemaLocation (mapping-common/schemalocation "ymparisto/ymparistoluvat" "2.1.2")
                 :xmlns:ymy "http://www.paikkatietopalvelu.fi/gml/ymparisto/ymparistoluvat"}
           mapping-common/common-namespaces)
   :child [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
           {:tag :ymparistolupatieto :child [{:tag :Ymparistolupa :child ymparistolupaType}]}]})

(defn save-application-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent.
   3rd parameter (submitted-application) is not used on YL applications."
  [application lang _ krysp-version output-dir begin-of-link]
  (let [krysp-polku-lausuntoon [:Ymparistoluvat :ymparistolupatieto :Ymparistolupa :lausuntotieto]
        canonical-without-attachments  (ymparistolupa-canonical/ymparistolupa-canonical application lang)
        statement-given-ids (mapping-common/statements-ids-with-status
                              (get-in canonical-without-attachments krysp-polku-lausuntoon))
        statement-attachments (mapping-common/get-statement-attachments-as-canonical application begin-of-link statement-given-ids)
        attachments (mapping-common/get-attachments-as-canonical application begin-of-link)
        canonical-with-statement-attachments (mapping-common/add-statement-attachments canonical-without-attachments statement-attachments krysp-polku-lausuntoon)
        canonical (assoc-in
                    canonical-with-statement-attachments
                    [:Ymparistoluvat :ymparistolupatieto :Ymparistolupa :liitetieto]
                    attachments)
        xml (element-to-xml canonical ymparistolupa_to_krysp)]

    (mapping-common/write-to-disk application attachments statement-attachments xml krysp-version output-dir)))

(permit/register-function permit/YL :app-krysp-mapper save-application-as-krysp)

