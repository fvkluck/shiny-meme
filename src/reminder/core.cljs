(ns reminder.core
  (:require [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer [<p!]]
            [reagent.core :as r]
            [reagent.react-native :as rn]
            [tick.core :as t]
            ["@notifee/react-native" :as notifee :refer [default]]))

(def title-style {:font-size 50})

(def n (aget notifee "default"))

(defonce state (r/atom {:cue-time "08:30" 
                        :reminder-time "17:00"
                        :days [{:date "date"
                                :goals {1 {:text "This is my first goal"}
                                        2 {:text "This is my second goal"}}}]}))

(defn logger [x]
  (do
    (js/console.log "start logger")
    (doseq [k (js-keys x)]
      (do (js/console.log k) (js/console.log (aget x k))))
  x))

(defn new-id [goals]
  (->> goals
      keys
      (apply max)
      inc))

(defn add-goal [day text]
  (let [id (new-id (:goals @day))]
    (swap! day update :goals conj [id {:text text}])))

(defn send-notification
  ([title body] (send-notification title body (:channel-name @state)))
  ([title body channel-name]
   (go
     <p! (.displayNotification n #js {"title" title
                                      "body" body
                                      "android" #js {"channelId" channel-name}}))))

(defn cue-view [day]
  (let [new-goal-text (r/atom "")]
    [rn/view {:style {:flex 1 :align-items "center" :justify-content "center"}}
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
     [rn/button {:on-press #(add-goal day @new-goal-text) :title "save"}]
     [rn/button {:on-press #(send-notification "Dit is een titel" "dit is een body") :title "Send notification"}]]))

(defn hello []
  [rn/view  {:style  {:flex 1 :align-items  "center" :justify-content  "center"}}
   [cue-view (r/cursor state [:days 0])]])

(defn ^:export -main  [& args]
  (go
    (let [c (<p! (.createChannel n #js {:id "reminder-channel" :name "channel for reminder app"}))]
      (swap! state assoc :channel-name c)))
  (r/as-element  [hello]))
