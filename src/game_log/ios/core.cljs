(ns game-log.ios.core
    (:require-macros [natal-shell.core :refer [with-error-view]]
                     [natal-shell.components :refer [view text text-input image touchable-highlight]]
                     [natal-shell.alert :refer [alert]]
                     [cljs.core.async.macros :refer [go]])
    (:require [om.next :as om :refer-macros [defui]]
              [clojure.string :as string]
              [re-natal.support :as sup]
              [game-log.state :as state]
              [cljs.core.async :as async :refer [<! >! put! chan]])
    (:import [goog Uri]
             [goog.net Jsonp])
    )

(set! js/React (js/require "react-native"))

(def app-registry (.-AppRegistry js/React))
(def logo-img (js/require "./images/cljs.png"))

(enable-console-print!)


;;;;;;;;;;;;;;;;;
;; Tab Bar
;;;;;;;;;;;;;;;;;

(def FontAwesomeIcon (js/require "react-native-vector-icons/FontAwesome"))

(defn tab-bar-ios [opts & children]
    (apply js/React.createElement js/React.TabBarIOS (clj->js opts) children))

(defn tab-bar-item [opts & children]
    (apply js/React.createElement FontAwesomeIcon.TabBarItem (clj->js opts) children))


(defonce app-state (atom {:app/msg "Game Log" :search/results []}))

(def base-url
    "http://en.wikipedia.org/w/api.php?action=opensearch&format=json&search=")

(defn jsonp
    ([uri] (jsonp (chan) uri))
    ([c uri]
     (let [gjsonp (Jsonp. (Uri. uri))]
         (.send gjsonp nil #(put! c %))
         c)))

(defn search-loop [c]
    (go
        (loop [[query cb] (<! c)]
            (let [v (<! (jsonp (str base-url query)))
                  _ (println "v:" v)
                  [_ results] v]
                (cb {:search/results results}))
            (recur (<! c)))))


(defn result-list [results]
    (text #js {:key "result-list"}
          (map #(text nil %) results)))


(defn search-field [ac query]
    (text-input {:style {:fontSize 30
                         :height 60
                         :textAlign "center"
                         :fontWeight "bold"
                         :borderColor "lightgrey"
                         :borderWidth 1
                         :marginBottom 10
                         :borderRadius 4}
                 :autoCorrect false
                 :placeholder "Type a game title"
                 ;:value query
                 :onChangeText (fn [text]
                                   (println "search-field changed, text is:" text)
                                   (om/set-query! ac
                                                  {:params {:query text}}))}))


(defui AutoCompleter
    static om/IQueryParams
    (params [_]
        {:query "aliens"})
    static om/IQuery
    (query [_]
        '[(:search/results {:query ?query})])
    Object
    (render [this]
        (let [{:keys [search/results]} (om/props this)
              _ (println "AutoCompleter.render")]
            (view nil
                  (cond->
                      [(search-field this (:query (om/get-params this)))]
                      (not (empty? results)) (conj (result-list results)))))))


(def autocompleter (om/factory AutoCompleter))


(defn send-to-chan [c]
    (fn [{:keys [search]} cb]
        (when search
            (let [{[search] :children} (om/query->ast search)
                  query (get-in search [:params :query])]
                (put! c [query cb])))))


(def send-chan (chan))

(search-loop send-chan)


(defui MainView
    static om/IQuery
    (query [this]
        '[:app/msg])

    Object
    (render [this]
        (with-error-view
            (let [{:keys [app/msg]} (om/props this)]
                (view {:style {:flex 1}}
                      (view {:style {:flex 1
                                     :margin 30
                                     }}
                            (text
                                {:style {:justifyContent "space-around"
                                         :fontSize 50
                                         :fontWeight "100"
                                         :marginBottom 20
                                         :textAlign "center"}}
                                msg)

                            (autocompleter)

                            (touchable-highlight
                                {:style {:backgroundColor "#999" :padding 10 :borderRadius 5}
                                 :onPress (fn [e]
                                              ;(find-game "aliens")
                                              (alert "HELLO!"))}

                                (text
                                    {:style {:color "white" :textAlign "center" :fontWeight "bold"
                                             }}
                                    "Search 2")))

                      (tab-bar-ios {:style {:flex 0
                                            :height 50      ; necessary otherwise the bar can't get focus
                                            }}
                                   (tab-bar-item
                                       {:iconName "home"
                                        :title "Accueil"
                                        :onPress #(alert "hey")
                                        })
                                   (tab-bar-item
                                       {:iconName "euro"
                                        :title "Investir"
                                        :badge 3
                                        ;:onPress (navigate "invest")
                                        }
                                       )
                                   (tab-bar-item
                                       {:iconName "area-chart"
                                        :title "Gains"
                                        ;:onPress (navigate "earnings")
                                        })
                                   (tab-bar-item
                                       {:iconName "gear"
                                        :title "Preferences"
                                        ;:onPress (navigate "settings")
                                        })

                                   ))))))


(defmulti read om/dispatch)

(defmethod read :search/results
    [{:keys [state ast] :as env} k {:keys [query]}]
    ;(println "read# query:" query)
    ;(println "read# ast:" ast)
    (merge
        {:value (get @state k [])}
        (when-not (or (string/blank? query)
                      (< (count query) 3))
            {:search ast})))


(defmethod read :default
    [{:keys [state]} k _]
    (let [st @state]
        (if-let [[_ v] (find st k)]
            {:value v}
            {:value :not-found})))

(def reconciler
    (om/reconciler
        {:state app-state
         :parser (om/parser {:read read})
         :send (send-to-chan send-chan)
         :remotes [:remote :search]
         :root-render sup/root-render
         :root-unmount sup/root-unmount}))

(defonce RootNode (sup/root-node 1))
(defonce app-root (om/factory RootNode))

(defn init []
    (om/add-root! reconciler MainView 1)
    (.registerComponent app-registry "GameLog" (fn [] app-root)))