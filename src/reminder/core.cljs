(ns reminder.core
  (:require [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer [<p!]]
            [reagent.core :as r]
            [reagent.react-native :as rn]
            [tick.core :as t]
            ["@notifee/react-native" :refer [TriggerType]]
            ["@notifee/react-native$default" :as n]
            ["@react-navigation/native" :refer [NavigationContainer]]
            ["@react-navigation/native-stack" :as nav-stack]))

(def title-style {:font-size 50})

(def Stack (nav-stack/createNativeStackNavigator))

(defonce state (r/atom {:cue-time "08:30" 
                        :reminder-time "17:00"
                        :days [{:date "date"
                                :goals {1 {:text "This is my first goal"}
                                        2 {:text "This is my second goal"}}}]}))

(defn logger [x]
  (js/console.log "start logger")
  (doseq [k (js-keys x)]
    (js/console.log k) (js/console.log (aget x k)))
  x)

(defn new-id [goals]
  (->> goals
      keys
      (apply max)
      inc))

(defn add-goal [day text]
  (let [id (new-id (:goals @day))]
    (swap! day update :goals conj [id {:text text}])))

(defn send-notification [title body & {:keys [channel-name trigger-time]
                                       :or {channel-name (:channel-name @state)
                                            trigger-time (t/>> (t/now) (t/new-duration 2 :seconds))}}]
  (go
    <p! (n/createTriggerNotification #js {"title" title
                                          "body" body
                                          "android" #js {"channelId" channel-name}}
                                     #js {"type" (.-TIMESTAMP TriggerType)
                                          "timestamp" (.getTime (js/Date. trigger-time))})))
(comment
  (defn send-notification
    ([title body] (send-notification title body (:channel-name @state)))
    ([title body channel-name] (send-notification title
                                                  body
                                                  channel-name
                                                  (+ (.getTime (js/Date. (.now js/Date))) 2000)))
    ([title body channel-name trigger-time]
     (go
       <p! (n/createTriggerNotification #js {"title" title
                                             "body" body
                                             "android" #js {"channelId" channel-name}}
                                        #js {"type" (.-TIMESTAMP TriggerType)
                                             "timestamp" trigger-time})))))

(comment 
  (def date (js/Date. (.now js/Date)))
  (.setMinutes date 11)
  (send-notification "title" "body" (:channel-name @state) (.getTime date)))

(defn cue-view [_navigation]
  (let [day (r/cursor state [:days 0])
        new-goal-text (r/atom "")]
    (r/as-element [rn/view {:style {:flex 1 :align-items "center" :justify-content "center"}}
                   [rn/text {:style title-style} "Set your goals"]
                   [rn/text "Please take a moment to consider what you want to achieve today."]
                   (for [[g-id g] (:goals @day)]
                     [rn/view {:key g-id :flex-direction "row" :margin-bottom 10}
                      [rn/text-input {:key g-id :default-value (:text g)
                                      :on-change-text #(swap! day assoc-in [:goals g-id :text] %)}]
                      [rn/button {:on-press #(swap! day update :goals dissoc g-id) :title "Remove"}]])
                   [rn/text-input {:placeholder "my new goal" :on-change-text #(reset! new-goal-text %)
                                   :on-end-editing #(do
                                                      (add-goal day @new-goal-text)
                                                      (.focus (-> % .-target))
                                                      (.clear (-> % .-target)))}]
                   [rn/button {:on-press #(send-notification "Dit is een titel" "dit is een body") :title "Remind me tonight!"}]])))

(defn reminder-view [day]
  (let [new-goal-text (r/atom "")]
    [rn/view {:style {:flex 1 :align-items "center" :justify-content "center"}}
                   [rn/text {:style title-style} "Your plan"]
                   [rn/text "For better or for worse this is what you had planned"]
                   (for [[g-id g] (:goals @day)]
                     [rn/view {:key g-id :flex-direction "row" :margin-bottom 10}
                      [rn/text {:key g-id
                                :style {:color (if (:finished g) "#009900" "#ff0000")}} (:text g)]
                      [rn/button {:on-press #(swap! day update-in [:goals g-id] assoc :finished true) :title "Finished"}]])
                   [rn/text-input {:placeholder "my new goal" :on-change-text #(reset! new-goal-text %)
                                   :on-end-editing #(do
                                                      (add-goal day @new-goal-text)
                                                      (.focus (-> % .-target))
                                                      (.clear (-> % .-target)))}]]))
(defonce num (r/atom 0))

(defn hello []
    [rn/view  {:style  {:flex 1 :align-items  "center" :justify-content  "center"}}
     [reminder-view (r/cursor state [:days 0])]])

(defn home [_navigation]
  [rn/view {:style {:flex 1 :align-items "center" :justify-content "center"}}
   [rn/text {} (str "count: " @num)]
   [rn/button {:on-press #(swap! num inc) :title "increase"}]
   [rn/button {:on-press #(.navigate (:navigation _navigation) "cue") :title "go to cue-view"}]
   [rn/button {:on-press #(.navigate (:navigation _navigation) "reminder") :title "go to reminder-view"}]])

(defn app []
  [:> NavigationContainer {}
   [:> (.-Navigator Stack) {:screen-options {:title "Awesome Project"
                                             :header-style {:background-color "green" :height 100}
                                             :header-tint-color "#fff"
                                             :header-title-style {:font-size 30 :text-align "center"}}}
    [:> (.-Screen Stack) {:name "home" :component (r/reactify-component home)
                          :options {:title "Home Page"}}]
    [:> (.-Screen Stack) {:name "reminder" :component (r/reactify-component hello)
                          :options {:title "This is your reminder"}}]
    [:> (.-Screen Stack) {:name "cue" :component (r/reactify-component cue-view)
                          :options {:title "This is your cue"}}]
    ]])

(defn ^:export -main  [& args]
  (go
    (let [c (<p! (n/createChannel #js {:id "reminder-channel" :name "channel for reminder app"}))]
      (swap! state assoc :channel-name c)))
  (r/as-element [app]))
