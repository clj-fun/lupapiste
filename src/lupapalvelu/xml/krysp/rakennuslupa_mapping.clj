(ns lupapalvelu.xml.krysp.rakennuslupa-mapping
  (:use  [lupapalvelu.xml.krysp.yhteiset]
         [clojure.data.xml]
         [clojure.java.io]
         [lupapalvelu.document.krysp :only [application-to-canonical]]
         [lupapalvelu.xml.emit :only [element-to-xml]]
         [lupapalvelu.xml.krysp.validator :only [validate]]))

;RakVal
(def tunnus-children [{:tag :valtakunnallinenNumero}
                      {:tag :jarjestysnumero}
                      {:tag :kiinttun}
                      {:tag :rakennusnro}
                      {:tag :aanestysalue}])

(def rakennelma (conj [{:tag :kuvaus
                        :child [{:tag :kuvaus}]}]
                      sijantitieto
                      {:tag :tunnus :child tunnus-children}))

(def huoneisto {:tag :huoneisto
                :child [{:tag :huoneluku}
                        {:tag :keittionTyyppi}
                        {:tag :huoneistoala}
                        {:tag :varusteet
                         :child [{:tag :WCKytkin}
                                 {:tag :ammeTaiSuihkuKytkin}
                                 {:tag :saunaKytkin}
                                 {:tag :parvekeTaiTerassiKytkin}
                                 {:tag :lamminvesiKytkin}]}
                        {:tag :huoneistonTyyppi}
                        {:tag :huoneistotunnus
                         :child [{:tag :porras}
                                 {:tag :huoneistonumero}
                                 {:tag :jakokirjain}
                                 ]}
                        ]})

(def rakennus {:tag :Rakennus
                :child [{:tag :yksilointitieto :attr {:xmlns "http://www.paikkatietopalvelu.fi/gml/yhteiset"}}
                        {:tag :alkuHetki :attr {:xmlns "http://www.paikkatietopalvelu.fi/gml/yhteiset"}}
                        sijantitieto
                        {:tag :rakennuksenTiedot
                         :child [{:tag :rakennustunnus :child tunnus-children}
                                 {:tag :kayttotarkoitus}
                                 {:tag :tilavuus}
                                 {:tag :kokonaisala}
                                 {:tag :kellarinpinta-ala}
                                 {:tag :BIM :child []}
                                 {:tag :kerrosluku}
                                 {:tag :kerrosala}
                                 {:tag :rakentamistapa}
                                 {:tag :kantavaRakennusaine :child [{:tag :muuRakennusaine}
                                                                    {:tag :rakennusaine}]}
                                 {:tag :julkisivu
                                  :child [{:tag :muuMateriaali}
                                          {:tag :julkisivumateriaali}]}
                                 {:tag :verkostoliittymat :child [{:tag :viemariKytkin}
                                                                  {:tag :vesijohtoKytkin}
                                                                  {:tag :sahkoKytkin}
                                                                  {:tag :maakaasuKytkin}
                                                                  {:tag :kaapeliKytkin}]}
                                 {:tag :energialuokka}
                                 {:tag :paloluokka}
                                 {:tag :lammitystapa}
                                 {:tag :lammonlahde :child [{:tag :polttoaine}
                                                             {:tag :muu}]}
                                 {:tag :varusteet
                                  :child [{:tag :sahkoKytkin}
                                          {:tag :kaasuKytkin}
                                          {:tag :viemariKytkin}
                                          {:tag :vesijohtoKytkin}
                                          {:tag :lamminvesiKytkin}
                                          {:tag :aurinkopaneeliKytkin}
                                          {:tag :hissiKytkin}
                                          {:tag :koneellinenilmastointiKytkin}
                                          {:tag :saunoja}
                                          {:tag :uima-altaita}
                                          {:tag :vaestonsuoja}]}
                                 {:tag :jaahdytysmuoto}
                                 {:tag :asuinhuoneistot :child [huoneisto]}
                                 ]}]})


(def rakennuslupa_to_krysp
  {:tag :Rakennusvalvonta :attr {:xmlns:xlink "http://www.w3.org/1999/xlink" :xmlns:xml "http://www.w3.org/XML/1998/namespace"
  :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance" :xmlns:yht "http://www.paikkatietopalvelu.fi/gml/yhteiset"
  :xsi:schemaLocation "http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta/2.0.0/rakennusvalvonta.xsd"
  :xmlns "http://www.paikkatietopalvelu.fi/gml/rakennusvalvonta"}
   :child [{:tag :toimituksenTiedot :child toimituksenTiedot}
           {:tag :rakennusvalvontaAsiatieto
            :child [{:tag :RakennusvalvontaAsia
                     :child [{:tag :kasittelynTilatieto :child [tilamuutos]}
                             {:tag :luvanTunnisteTiedot
                              :child [{:tag :LupaTunnus
                                       :attr {:xmlns "http://www.paikkatietopalvelu.fi/gml/yhteiset"}
                                       :child [{:tag :muuTunnus} {:tag :saapumisPvm}]}]}
                             {:tag :osapuolettieto
                              :child [osapuolet]}
                             {:tag :rakennuspaikkatieto
                              :child [rakennuspaikka]}
                             {:tag :toimenpidetieto
                              :child [{:tag :Toimenpide
                                       :child [{:tag :uusi
                                                :child [{:tag :huoneistoala}
                                                        {:tag :kuvaus}]}
                                               {:tag :laajennus}
                                               {:tag :kayttotarkoitusmuutos}
                                               {:tag :perustus}
                                               {:tag :perusparannus}
                                               {:tag :uudelleenrakentaminen}
                                               {:tag :purkaminen}
                                               {:tag :muuMuutosTyo}
                                               {:tag :kaupunkikuvaToimenpide}
                                               {:tag :katselmustieto}
                                               {:tag :rakennustieto
                                                :child [rakennus]}
                                               {:tag :rakennelmatieto}]}]}]}]}]})

(defn get-application-as-krysp [application]
  (let [
        canonical (application-to-canonical application)
        xml (element-to-xml canonical rakennuslupa_to_krysp)
        xml-s (indent-str xml)]
    (validate xml-s)


    ;(with-open [out-file (writer "/Users/terotu/example-krysp.xml" )]
    ; (emit xml out-file))
    ))
